package ai.closette.common.i18n;

import ai.closette.common.exception.MessageKeys;
import ai.closette.config.MessageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the bundles themselves. A missing Turkish line, a placeholder that only
 * exists in one language, or a key nobody translated is the kind of thing that
 * ships silently and then shows an English sentence — or a raw key — to a Turkish
 * user. It fails here instead.
 */
class MessageBundleTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private static final ResourceBundle EN =
            ResourceBundle.getBundle("messages", MessageConfig.ENGLISH);
    private static final ResourceBundle TR =
            ResourceBundle.getBundle("messages", MessageConfig.TURKISH);

    static List<String> allKeys() {
        return new ArrayList<>(new TreeSet<>(EN.keySet()));
    }

    @Test
    void bothLanguagesDefineExactlyTheSameKeys() {
        assertThat(new TreeSet<>(TR.keySet()))
                .as("keys only in one bundle")
                .isEqualTo(new TreeSet<>(EN.keySet()));
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void noTranslationIsEmpty(String key) {
        assertThat(EN.getString(key).trim()).as("English %s", key).isNotEmpty();
        assertThat(TR.getString(key).trim()).as("Turkish %s", key).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void placeholdersMatchAcrossLanguages(String key) {
        // Word order differs, so {0} and {1} may be swapped — but a translation
        // that drops or invents a placeholder renders as a broken sentence.
        assertThat(placeholdersIn(TR.getString(key)))
                .as("placeholders in %s", key)
                .isEqualTo(placeholdersIn(EN.getString(key)));
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void everyMessageRendersCleanly(String key) {
        // Messages are always run through MessageFormat (see MessageConfig), so an
        // apostrophe must be written as '' — and must come back out as a single
        // one. Getting this wrong shows the user a literal "wasn''t".
        for (ResourceBundle bundle : List.of(EN, TR)) {
            String rendered = MessageFormat.format(bundle.getString(key), 1, 2, 3);
            assertThat(rendered).as("%s renders", key).isNotBlank();
            assertThat(rendered).as("%s leaks an escape", key).doesNotContain("''");
            assertThat(rendered).as("%s has an unfilled placeholder", key).doesNotContain("{");
        }
    }

    @ParameterizedTest
    @MethodSource("allKeys")
    void everyApostropheIsEscaped(String key) {
        // The raw value must never carry a lone apostrophe: MessageFormat would
        // treat it as a quote and swallow the text after it.
        for (ResourceBundle bundle : List.of(EN, TR)) {
            String raw = bundle.getString(key);
            assertThat(raw.replace("''", "")).as("%s has an unescaped apostrophe", key)
                    .doesNotContain("'");
        }
    }

    @Test
    void everyKeyConstantResolvesInBothLanguages() {
        for (String key : declaredKeys()) {
            // Plural keys are looked up with a .one/.other suffix at runtime.
            List<String> candidates = EN.containsKey(key)
                    ? List.of(key)
                    : List.of(key + ".one", key + ".other");
            for (String candidate : candidates) {
                assertThat(EN.containsKey(candidate)).as("English bundle is missing %s", candidate).isTrue();
                assertThat(TR.containsKey(candidate)).as("Turkish bundle is missing %s", candidate).isTrue();
            }
        }
    }

    @Test
    void everyBundleKeyIsActuallyUsed() {
        // Catches leftovers: a key kept after the sentence using it was removed.
        Set<String> declared = declaredKeys();
        List<String> orphans = EN.keySet().stream()
                .map(MessageBundleTest::baseKey)
                .filter(key -> !declared.contains(key))
                .distinct()
                .sorted()
                .toList();

        assertThat(orphans).as("bundle keys no constant refers to").isEmpty();
    }

    @Test
    void turkishIsActuallyTranslated() {
        // A copied-over English line is a translation that was never done.
        List<String> identical = EN.keySet().stream()
                .filter(key -> EN.getString(key).equals(TR.getString(key)))
                .sorted()
                .toList();

        // Loanwords legitimately match ("camel", "blazer"); prose never should.
        assertThat(identical).as("Turkish values identical to English").isEmpty();
    }

    /** Every key referenced from code, via the MessageKeys constants. */
    private static Set<String> declaredKeys() {
        Set<String> keys = new TreeSet<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            try {
                String value = (String) field.get(null);
                if (value.endsWith(".")) {
                    continue; // CATEGORY_PREFIX is completed with an enum name
                }
                keys.add(value);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        // Categories are addressed as CATEGORY_PREFIX + enum name.
        for (ai.closette.wardrobe.model.ClothingCategory category
                : ai.closette.wardrobe.model.ClothingCategory.values()) {
            keys.add(MessageKeys.CATEGORY_PREFIX + category.name());
        }
        // Validation keys live in annotations rather than constants.
        for (String key : EN.keySet()) {
            if (key.startsWith("validation.")) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** "buy.pairsWith.one" → "buy.pairsWith"; anything else unchanged. */
    private static String baseKey(String key) {
        return key.endsWith(".one") || key.endsWith(".other")
                ? key.substring(0, key.lastIndexOf('.'))
                : key;
    }

    private static List<String> placeholdersIn(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found.stream().distinct().sorted().toList();
    }
}
