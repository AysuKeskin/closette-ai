package ai.closette.common.persistence;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Colors, styles and seasons all round-trip through this converter. */
class StringListConverterTest {

    private final StringListConverter converter = new StringListConverter();

    @Test
    void listRoundTripsThroughTheColumn() {
        List<String> values = List.of("black", "dusty pink", "navy");

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(values)))
                .isEqualTo(values);
    }

    @Test
    void emptyAndNullListsBecomeAnEmptyColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isEmpty();
        assertThat(converter.convertToDatabaseColumn(List.of())).isEmpty();
    }

    @Test
    void emptyColumnBecomesAnEmptyList() {
        // Never null: callers stream over these lists without a null check.
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void blankEntriesAreDroppedOnTheWayIn() {
        assertThat(converter.convertToDatabaseColumn(Arrays.asList("black", "", null, "  ", "navy")))
                .isEqualTo("black,navy");
    }

    @Test
    void surroundingWhitespaceIsTrimmedOnBothSides() {
        assertThat(converter.convertToDatabaseColumn(List.of("  black  ", " navy"))).isEqualTo("black,navy");
        assertThat(converter.convertToEntityAttribute(" black , navy ")).containsExactly("black", "navy");
    }
}
