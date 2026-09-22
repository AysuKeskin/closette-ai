package ai.closette.wardrobe.service;

import ai.closette.ai.service.AIService;
import ai.closette.auth.service.AuthService;
import ai.closette.storage.service.StorageService;
import ai.closette.support.TestData;
import ai.closette.wardrobe.dto.CreateItemRequest;
import ai.closette.wardrobe.dto.UpdateItemRequest;
import ai.closette.wardrobe.dto.WardrobeItemResponse;
import ai.closette.wardrobe.model.ClothingCategory;
import ai.closette.wardrobe.repository.WardrobeItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How much of a piece is each colour, and when that measurement stops being true.
 *
 * The shares come off the photograph, so they only describe the colours the
 * pipeline found. The moment somebody edits the colours by hand there is nothing
 * honest left to say about the proportions, and saying it anyway would have the
 * stylist weight a colour nobody measured.
 */
@SpringBootTest
@ActiveProfiles("test")
class ColorShareTest {

    @Autowired
    AuthService authService;

    @Autowired
    WardrobeService wardrobe;

    @Autowired
    WardrobeItemRepository repository;

    @MockitoBean
    StorageService storage;

    @MockitoBean
    AIService aiService;

    private WardrobeItemResponse create(UUID user, List<String> colors, List<String> shares) {
        return wardrobe.create(user, new CreateItemRequest(
                "Pinstripe trousers", ClothingCategory.BOTTOMS, null, colors, shares,
                "striped", List.of("classic"), List.of("fall"), null, null, null, false));
    }

    @Test
    void measuredSharesAreKeptWithTheItem() {
        UUID user = TestData.newUser(authService);

        WardrobeItemResponse item = create(user,
                List.of("dark grey", "grey"), List.of("dark grey:78", "grey:22"));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares())
                .containsExactly("dark grey:78", "grey:22");
    }

    @Test
    void sharesThatDoNotMatchTheColoursAreNotStored() {
        // A client could send anything; a share pinned to a colour that is not in
        // the list is worse than no share at all.
        UUID user = TestData.newUser(authService);

        WardrobeItemResponse item = create(user, List.of("navy"), List.of("dark grey:78"));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares()).isEmpty();
    }

    @Test
    void removingAColourKeepsWhatWasMeasuredAboutTheRest() {
        // Dropping "grey" from the list is the user disagreeing that it is worth
        // listing. It does not make "dark grey covers 78% of this" less true, and
        // throwing that away would cost the stylist the one fact it can act on.
        UUID user = TestData.newUser(authService);
        WardrobeItemResponse item = create(user,
                List.of("dark grey", "grey"), List.of("dark grey:78", "grey:22"));

        wardrobe.update(user, item.id(), new UpdateItemRequest(
                null, null, null, List.of("dark grey"), null, null, null, null, null, null, null));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares())
                .containsExactly("dark grey:78");
    }

    @Test
    void addingAColourDropsTheWholeMeasurement() {
        // The opposite edit, and it says something different: the pipeline missed a
        // colour, so every proportion it reported is suspect, not just the missing one.
        UUID user = TestData.newUser(authService);
        WardrobeItemResponse item = create(user,
                List.of("dark grey", "grey"), List.of("dark grey:78", "grey:22"));

        wardrobe.update(user, item.id(), new UpdateItemRequest(
                null, null, null, List.of("dark grey", "grey", "navy"),
                null, null, null, null, null, null, null));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares()).isEmpty();
    }

    @Test
    void replacingTheColoursOutrightDropsTheMeasurement() {
        UUID user = TestData.newUser(authService);
        WardrobeItemResponse item = create(user,
                List.of("dark grey", "grey"), List.of("dark grey:78", "grey:22"));

        wardrobe.update(user, item.id(), new UpdateItemRequest(
                null, null, null, List.of("navy"), null, null, null, null, null, null, null));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares()).isEmpty();
    }

    @Test
    void anEditThatLeavesTheColoursAloneKeepsTheMeasurement() {
        UUID user = TestData.newUser(authService);
        WardrobeItemResponse item = create(user,
                List.of("dark grey", "grey"), List.of("dark grey:78", "grey:22"));

        wardrobe.update(user, item.id(), new UpdateItemRequest(
                "Renamed", null, null, null, null, null, null, null, null, null, null));

        assertThat(repository.findById(item.id()).orElseThrow().getColorShares())
                .containsExactly("dark grey:78", "grey:22");
    }
}
