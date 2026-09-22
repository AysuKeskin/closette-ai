package ai.closette.beauty.service;

import ai.closette.beauty.dto.BeautyProductCandidate;
import ai.closette.beauty.model.BeautyCategory;
import ai.closette.beauty.model.CatalogueProduct;
import ai.closette.beauty.repository.CatalogueProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Beauty product lookup, aggregated over several free sources and ranked:
 * <ul>
 *   <li>RapidAPI (e.g. Sephora) — optional, only when a key is configured; the richest catalogue;</li>
 *   <li>Makeup API — a structured makeup catalogue (brand, name, image, type);</li>
 *   <li>Open Beauty Facts — barcode + broad coverage for skincare/hair/perfume.</li>
 * </ul>
 * Every source is best-effort: one failing (or missing a key) never fails the search.
 * Results are merged, de-duplicated, then ranked so known brands and name matches lead.
 */
@Service
public class BeautyLookupService {

    private static final Logger log = LoggerFactory.getLogger(BeautyLookupService.class);
    // Request the English ingredient text too; OBF's generic ingredients_text is often in another
    // language (Spanish/French), which leaks foreign words like "beneficios" into the chips.
    private static final String FIELDS =
            "code,product_name,brands,ingredients_text,ingredients_text_en,image_url,categories";
    private static final int MAX_RESULTS = 15;
    private static final Duration MAKEUP_CACHE_TTL = Duration.ofHours(24);
    // Overall cap for a search: sources run in parallel, so this bounds the whole call, not each one.
    private static final long SEARCH_TIMEOUT_MS = 6000;
    // How many local hits count as a good enough answer. Open Beauty Facts allows only
    // a handful of searches a minute PER IP, and every user of this app shares the
    // server's one IP — so the upstream budget belongs to the whole app, not to each
    // person. Answering a repeat search from our own rows is what keeps that budget
    // for the queries that genuinely need it.
    private static final int CACHE_HIT_FLOOR = 5;

    // Well-known brands (normalised: lowercase, punctuation stripped) that get a ranking boost,
    // so a generic query like "mascara" surfaces recognisable products, not obscure indie ones.
    private static final Set<String> KNOWN_BRANDS = Set.of(
            "maybelline", "loreal", "nyx", "revlon", "covergirl", "maxfactor", "rimmel", "mac",
            "fenty", "nars", "urbandecay", "toofaced", "benefit", "clinique", "esteelauder",
            "lancome", "dior", "chanel", "ysl", "charlottetilbury", "elf", "colourpop", "milani",
            "wetnwild", "essence", "catrice", "cerave", "cetaphil", "larocheposay", "theordinary",
            "neutrogena", "olay", "nivea", "garnier", "bioderma", "vichy", "eucerin", "aveeno",
            "glossier", "rarebeauty", "sephora", "kylie", "huda", "anastasia", "morphe", "tarte",
            "hourglass", "bobbibrown", "clarins", "shiseido", "kiehls", "drunkelephant",
            "paulaschoice", "firstaidbeauty", "tatcha", "pixi", "burtsbees", "physiciansformula",
            // Turkish shelves: without these a search in Turkish ranks local products last.
            "flormar", "goldenrose", "farmasi", "avon", "oriflame", "pastel", "note", "gratis",
            "sebamed", "bioxcin", "dermokil", "hobby", "arkopharma", "nuxe", "vichyturkiye",
            "eveline", "lorealparis", "sheglam", "beaulis", "gabrini", "koton");

    private final WebClient obf = WebClient.builder()
            .baseUrl("https://world.openbeautyfacts.org")
            .defaultHeader("User-Agent", "Closette/1.0 (closette.ai)")
            .build();

    // The Makeup API returns its whole catalogue in one array; raise the buffer past the 256KB default.
    private final WebClient makeup = WebClient.builder()
            .baseUrl("https://makeup-api.herokuapp.com")
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();

    private final String rapidApiKey;
    private final String rapidApiHost;
    private final String rapidSearchPath;
    private final String rapidQueryParam;
    private final WebClient rapid;

    // The Makeup API has no free-text search, so we cache its catalogue and match in memory.
    private record MakeupEntry(BeautyProductCandidate candidate, String searchText) {
    }

    private volatile List<MakeupEntry> makeupCache;
    private volatile Instant makeupCacheAt;
    private final AtomicBoolean makeupRefreshing = new AtomicBoolean(false);
    private final CatalogueProductRepository catalogue;

    public BeautyLookupService(
            @Value("${closette.beauty.rapidapi-key:}") String rapidApiKey,
            @Value("${closette.beauty.rapidapi-host:sephora.p.rapidapi.com}") String rapidApiHost,
            @Value("${closette.beauty.rapidapi-search-path:/products/list}") String rapidSearchPath,
            @Value("${closette.beauty.rapidapi-query-param:q}") String rapidQueryParam,
            CatalogueProductRepository catalogue) {
        this.catalogue = catalogue;
        this.rapidApiKey = rapidApiKey == null ? "" : rapidApiKey.trim();
        this.rapidApiHost = rapidApiHost;
        this.rapidSearchPath = rapidSearchPath;
        this.rapidQueryParam = rapidQueryParam;
        this.rapid = WebClient.builder()
                .baseUrl("https://" + rapidApiHost)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
        if (this.rapidApiKey.isBlank()) {
            log.info("Beauty search: RapidAPI source disabled (no key); using Makeup API + Open Beauty Facts");
        } else {
            log.info("Beauty search: RapidAPI source enabled on {}{}?{}=", rapidApiHost, rapidSearchPath, rapidQueryParam);
        }
    }

    @Transactional
    public BeautyProductCandidate byBarcode(String barcode) {
        var cached = catalogue.findById(barcode == null ? "" : barcode);
        if (cached.isPresent()) return toCandidate(cached.get());
        BeautyProductCandidate fresh = fetchByBarcode(barcode);
        if (fresh != null) remember(List.of(fresh));
        return fresh;
    }

    private BeautyProductCandidate fetchByBarcode(String barcode) {
        try {
            JsonNode root = obf.get()
                    .uri("/api/v2/product/{code}.json?fields={fields}", barcode, FIELDS)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
            if (root == null || root.path("status").asInt(0) != 1) {
                return null;
            }
            return toCandidate(root.path("product"));
        } catch (Exception e) {
            log.warn("Open Beauty Facts barcode lookup failed for {}", barcode, e);
            return null;
        }
    }

    /** Warm the Makeup API catalogue in the background at startup so the first user search isn't
     * blocked by the (slow, cold-starting) full-catalogue download. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCatalogue() {
        CompletableFuture.runAsync(this::makeupCatalog);
    }

    /** Name search aggregated over every source, de-duplicated and ranked by relevance. The sources
     * are queried in parallel and bounded by {@link #SEARCH_TIMEOUT_MS}; whatever returns in time is merged. */
    @Transactional
    public List<BeautyProductCandidate> search(String query) {
        if (query == null || query.isBlank()) return List.of();

        List<BeautyProductCandidate> known = catalogue.search(query.trim(), PageRequest.of(0, MAX_RESULTS))
                .stream().map(BeautyLookupService::toCandidate).toList();
        if (known.size() >= CACHE_HIT_FLOOR) {
            String[] localTokens = tokens(query);
            return known.stream()
                    .sorted(Comparator.comparingInt((BeautyProductCandidate c) -> score(c, localTokens)).reversed())
                    .toList();
        }

        CompletableFuture<List<BeautyProductCandidate>> rapidF = CompletableFuture.supplyAsync(() -> rapidApiSearch(query));
        CompletableFuture<List<BeautyProductCandidate>> makeupF = CompletableFuture.supplyAsync(() -> makeupSearch(query));
        CompletableFuture<List<BeautyProductCandidate>> obfF = CompletableFuture.supplyAsync(() -> openBeautyFactsSearch(query));
        try {
            CompletableFuture.allOf(rapidF, makeupF, obfF).get(SEARCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Beauty search: sources didn't all finish within {}ms for '{}'", SEARCH_TIMEOUT_MS, query);
        }

        LinkedHashMap<String, BeautyProductCandidate> merged = new LinkedHashMap<>();
        // getNow(empty) takes whatever each source produced; a slow one simply contributes nothing.
        for (BeautyProductCandidate c : rapidF.getNow(List.of())) merged.putIfAbsent(dedupeKey(c), c);
        for (BeautyProductCandidate c : makeupF.getNow(List.of())) merged.putIfAbsent(dedupeKey(c), c);
        for (BeautyProductCandidate c : obfF.getNow(List.of())) merged.putIfAbsent(dedupeKey(c), c);

        String[] tokens = tokens(query);
        List<BeautyProductCandidate> ranked = merged.values().stream()
                .sorted(Comparator.comparingInt((BeautyProductCandidate c) -> score(c, tokens)).reversed())
                .limit(MAX_RESULTS)
                .toList();
        remember(ranked);
        return ranked;
    }

    /**
     * Keep what the upstreams just told us. Only products carrying a barcode are
     * stored, because that is the identity the cache is keyed by — a result without
     * one cannot be recognised again, and guessing a key would merge two products.
     */
    private void remember(List<BeautyProductCandidate> found) {
        for (BeautyProductCandidate c : found) {
            if (c.barcode() == null || c.barcode().isBlank()) continue;
            if (catalogue.existsById(c.barcode())) continue;
            catalogue.save(new CatalogueProduct(
                    c.barcode(), c.productName(), c.brand(), c.category(),
                    c.ingredients() == null ? null : String.join(", ", c.ingredients()),
                    c.imageUrl(), "obf"));
        }
    }

    private static BeautyProductCandidate toCandidate(CatalogueProduct p) {
        List<String> ingredients = p.getIngredients() == null || p.getIngredients().isBlank()
                ? List.of()
                : List.of(p.getIngredients().split("\\s*,\\s*"));
        return new BeautyProductCandidate(
                p.getBarcode(), p.getProductName(), p.getBrand(), p.getCategory(), ingredients, p.getImageUrl());
    }

    /** Higher = more relevant: known brand, query words in the name/brand, and having an image all help. */
    private int score(BeautyProductCandidate c, String[] tokens) {
        int s = 0;
        String brand = normalize(c.brand());
        String name = c.productName() == null ? "" : c.productName().toLowerCase(Locale.ROOT);
        if (isKnownBrand(brand)) s += 5;
        for (String t : tokens) {
            if (t.isBlank()) continue;
            if (name.contains(t)) s += 3;
            if (brand.contains(t)) s += 2;
        }
        if (c.imageUrl() != null && !c.imageUrl().isBlank()) s += 1;
        // Community catalogues carry stubs whose name is just the search word and whose
        // brand is empty. They match everything and tell the shopper nothing.
        if (brand.isBlank()) s -= 4;
        return s;
    }

    private static boolean isKnownBrand(String normalizedBrand) {
        if (normalizedBrand.isBlank()) return false;
        for (String known : KNOWN_BRANDS) {
            if (normalizedBrand.contains(known)) return true;
        }
        return false;
    }

    // ---- Source: RapidAPI (optional) ----

    private List<BeautyProductCandidate> rapidApiSearch(String rawQuery) {
        if (rapidApiKey.isBlank()) return List.of();
        // Last variant is the English reading when there is one, the query as typed otherwise.
        String query = BeautyTerms.expand(rawQuery).stream().reduce((a, b) -> b).orElse(rawQuery);
        try {
            JsonNode root = rapid.get()
                    .uri(uri -> uri.path(rapidSearchPath)
                            .queryParam(rapidQueryParam, query)
                            .queryParam("pageSize", "15")
                            .build())
                    .header("X-RapidAPI-Key", rapidApiKey)
                    .header("X-RapidAPI-Host", rapidApiHost)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            List<BeautyProductCandidate> out = new ArrayList<>();
            if (root != null) {
                // Defensive: different RapidAPI beauty APIs nest the list under "products"/"data"/root array.
                JsonNode arr = root.has("products") ? root.get("products")
                        : root.has("data") ? root.get("data") : root;
                if (arr != null && arr.isArray()) {
                    for (JsonNode p : arr) {
                        BeautyProductCandidate c = rapidToCandidate(p);
                        if (c != null) out.add(c);
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("RapidAPI beauty search failed for '{}' on host {}", query, rapidApiHost, e);
            return List.of();
        }
    }

    private BeautyProductCandidate rapidToCandidate(JsonNode p) {
        String name = firstNonBlank(text(p, "displayName"), text(p, "productName"), text(p, "name"), text(p, "title"));
        if (name == null) return null;
        String brand = firstNonBlank(text(p, "brandName"), text(p, "brand"), text(p, "brand_name"));
        String image = firstNonBlank(text(p, "heroImage"), text(p, "image"), text(p, "imageUrl"), text(p, "image_url"));
        if (image != null && image.startsWith("/")) image = "https://www." + rapidApiHost.replace(".p.rapidapi.com", ".com") + image;
        BeautyCategory category = guessCategory(name + " " + (brand == null ? "" : brand));
        return new BeautyProductCandidate(null, name, brand, category, List.of(), normalizeImage(image));
    }

    // ---- Source: Open Beauty Facts ----

    private List<BeautyProductCandidate> openBeautyFactsSearch(String query) {
        try {
            JsonNode root = obf.get()
                    .uri(uri -> uri.path("/cgi/search.pl")
                            .queryParam("search_terms", query)
                            .queryParam("search_simple", "1")
                            .queryParam("action", "process")
                            .queryParam("json", "1")
                            .queryParam("page_size", "10")
                            .queryParam("fields", FIELDS)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            List<BeautyProductCandidate> out = new ArrayList<>();
            if (root != null && root.has("products")) {
                for (JsonNode p : root.path("products")) {
                    BeautyProductCandidate c = toCandidate(p);
                    if (c != null && c.productName() != null && !c.productName().isBlank()) {
                        out.add(c);
                    }
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Open Beauty Facts search failed for '{}'", query, e);
            return List.of();
        }
    }

    // ---- Source: Makeup API ----

    private List<BeautyProductCandidate> makeupSearch(String query) {
        // This catalogue is English-only and matched in memory, so trying the query's
        // English reading as well costs nothing and is the only way "ruj" reaches it.
        for (String variant : BeautyTerms.expand(query)) {
            List<BeautyProductCandidate> hits = makeupMatch(variant);
            if (!hits.isEmpty()) return hits;
        }
        return List.of();
    }

    private List<BeautyProductCandidate> makeupMatch(String query) {
        String[] tokens = tokens(query);
        List<BeautyProductCandidate> out = new ArrayList<>();
        for (MakeupEntry e : makeupCatalog()) {
            boolean matchesAll = true;
            for (String t : tokens) {
                if (!t.isBlank() && !e.searchText().contains(t)) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                out.add(e.candidate());
                if (out.size() >= 12) break;
            }
        }
        return out;
    }

    /**
     * The Makeup API catalogue. Once we have any copy we return it immediately and refresh in the
     * background when stale, so a user search never waits on the (slow) full download after the first load.
     */
    private List<MakeupEntry> makeupCatalog() {
        List<MakeupEntry> cached = makeupCache;
        if (cached != null) {
            boolean fresh = makeupCacheAt != null && makeupCacheAt.isAfter(Instant.now().minus(MAKEUP_CACHE_TTL));
            if (!fresh) refreshMakeupAsync();
            return cached;
        }
        return loadMakeupCatalogue(); // no copy yet: the one unavoidable blocking load
    }

    private void refreshMakeupAsync() {
        if (makeupRefreshing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    loadMakeupCatalogue();
                } finally {
                    makeupRefreshing.set(false);
                }
            });
        }
    }

    private List<MakeupEntry> loadMakeupCatalogue() {
        List<MakeupEntry> cached = makeupCache;
        try {
            JsonNode arr = makeup.get()
                    .uri("/api/v1/products.json")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(12))
                    .block();
            List<MakeupEntry> list = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode p : arr) {
                    MakeupEntry e = makeupToEntry(p);
                    if (e != null) list.add(e);
                }
            }
            makeupCache = list;
            makeupCacheAt = Instant.now();
            return list;
        } catch (Exception e) {
            log.warn("Makeup API catalogue load failed; using {} cached entries",
                    cached == null ? 0 : cached.size(), e);
            return cached != null ? cached : List.of();
        }
    }

    private MakeupEntry makeupToEntry(JsonNode p) {
        String name = text(p, "name");
        if (name == null || name.isBlank()) return null;
        String brand = text(p, "brand");
        String type = text(p, "product_type");
        BeautyCategory category = "nail_polish".equalsIgnoreCase(type)
                ? BeautyCategory.NAILS : BeautyCategory.MAKEUP;
        BeautyProductCandidate candidate =
                new BeautyProductCandidate(null, name, brand, category, List.of(), normalizeImage(text(p, "image_link")));

        StringBuilder hay = new StringBuilder();
        if (brand != null) hay.append(brand).append(' ');
        hay.append(name).append(' ');
        if (type != null) hay.append(type.replace('_', ' ')).append(' ');
        hay.append(text(p, "product_category") == null ? "" : text(p, "product_category")).append(' ');
        JsonNode tags = p.get("tag_list");
        if (tags != null && tags.isArray()) {
            for (JsonNode t : tags) hay.append(t.asText("")).append(' ');
        }
        return new MakeupEntry(candidate, hay.toString().toLowerCase(Locale.ROOT));
    }

    // ---- Shared helpers ----

    private BeautyProductCandidate toCandidate(JsonNode p) {
        if (p == null || p.isMissingNode()) return null;
        String name = text(p, "product_name");
        String brand = firstToken(text(p, "brands"));
        // Prefer the English ingredient text; fall back to the generic one.
        String ingredientsText = text(p, "ingredients_text_en");
        if (ingredientsText == null || ingredientsText.isBlank()) ingredientsText = text(p, "ingredients_text");
        List<String> ingredients = splitIngredients(ingredientsText);
        String image = text(p, "image_url");
        String code = text(p, "code");
        BeautyCategory category = guessCategory(text(p, "categories") + " " + (name == null ? "" : name));
        return new BeautyProductCandidate(code, name, brand, category, ingredients, image);
    }

    private static String[] tokens(String query) {
        return BeautyTerms.fold(query).split(" ");
    }

    /**
     * One entry per product, not per shade.
     *
     * A lipstick ships in thirty colours and the sources list every one, so a search
     * for "ruj" came back as the same Maybelline three times over. Everything from a
     * trailing shade or size marker onwards is dropped — but only when that marker is
     * near the end, so the 15 in "SPF 15 ... 6 ml" is kept and only "6 ml" goes.
     */
    private static String dedupeKey(BeautyProductCandidate c) {
        String brand = BeautyTerms.fold(c.brand());
        String[] words = BeautyTerms.fold(c.productName()).split(" ");
        int cut = words.length;
        for (int i = Math.max(0, words.length - 3); i < words.length; i++) {
            // Shade codes are alphanumeric as often as numeric ("FC62", "No:27", "115").
            // SPF is spelled the same way and is a real product difference, never a shade.
            if (!words[i].contains("spf") && words[i].matches("(no:?)?[a-z]{0,3}\\d+([.,]\\d+)?(ml|gr?|oz)?")) {
                cut = i;
                break;
            }
        }
        return brand + "|" + String.join(" ", java.util.Arrays.copyOfRange(words, 0, cut));
    }

    private static String normalize(String s) {
        return BeautyTerms.fold(s).replaceAll("[^a-z0-9]", "");
    }

    // Upgrade protocol-relative ("//...") and plain-http image links to https (iOS ATS blocks http).
    private static String normalizeImage(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://")) return "https://" + url.substring("http://".length());
        return url;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText().trim();
    }

    private static String firstToken(String s) {
        if (s == null || s.isBlank()) return null;
        return s.split(",")[0].trim();
    }

    // Connector/boilerplate tokens that appear in INCI lists but are not ingredients.
    private static final Set<String> INGREDIENT_NOISE = Set.of(
            "and", "or", "with", "may", "contain", "may contain", "other", "ingredients", "ingredient",
            "n/a", "na", "none", "aqua/water/eau", "ci", "and/or", "plus", "minus",
            // Common non-English connectors/boilerplate that leak in from other-language OBF entries.
            "y", "con", "sin", "de", "et", "avec", "und", "mit", "ingredientes", "ingredientes:",
            "beneficios", "ingredienti", "composition");

    private static List<String> splitIngredients(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split("[,;•\\n]")) {
            // Strip surrounding punctuation/brackets/dots so a stray "." or "(water)" doesn't become a chip.
            String t = part.strip()
                    .replaceAll("^[\\s.;:()\\[\\]/*+_\\-]+", "")
                    .replaceAll("[\\s.;:()\\[\\]/*+_\\-]+$", "")
                    .strip();
            if (t.isBlank() || t.length() >= 60) continue;
            if (!t.matches(".*\\p{L}{2,}.*")) continue;         // must contain a real word (≥2 letters)
            if (INGREDIENT_NOISE.contains(t.toLowerCase(Locale.ROOT))) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out.size() > 30 ? out.subList(0, 30) : out;
    }

    private static BeautyCategory guessCategory(String hay) {
        return BeautyTerms.guessCategory(hay);
    }

    private static boolean containsAny(String h, String... keys) {
        for (String k : keys) if (h.contains(k)) return true;
        return false;
    }
}
