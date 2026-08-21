"""Colour extraction is classic code, so it is fully assertable: a known pixel
value must always come back as the same fashion colour name."""
from app.pipeline.color import FASHION_COLORS, extract_colors

from tests.images import cutout_png, garment_on_background, solid_png

BLACK = FASHION_COLORS["black"]
NAVY = FASHION_COLORS["navy"]
RED = FASHION_COLORS["red"]
MUSTARD = FASHION_COLORS["mustard"]
CREAM = FASHION_COLORS["cream"]


def test_solid_image_maps_to_its_fashion_color():
    result = extract_colors(solid_png(BLACK))

    assert result == [{"name": "black", "hex": BLACK, "percentage": 100}]


def test_background_is_excluded_from_the_result():
    result = extract_colors(garment_on_background(NAVY, background_hex="#FFFFFF"))
    names = [c["name"] for c in result]

    assert names[0] == "navy"
    assert "white" not in names


def test_alpha_channel_defines_the_garment():
    # A cutout's transparent area must not count as a colour, whatever is under it.
    result = extract_colors(cutout_png([RED]))

    assert result == [{"name": "red", "hex": RED, "percentage": 100}]


def test_colors_are_ordered_by_dominance():
    # Two thirds navy, one third cream.
    result = extract_colors(cutout_png([NAVY, NAVY, CREAM]))
    names = [c["name"] for c in result]

    assert names == ["navy", "cream"]
    assert result[0]["percentage"] > result[1]["percentage"]


def test_result_is_capped_at_the_requested_count():
    result = extract_colors(cutout_png([NAVY, CREAM, RED, MUSTARD]), top=2)

    assert len(result) == 2


def test_colors_below_the_share_threshold_are_dropped():
    # 1 band in 20 = 5%, under the 6% minimum share.
    result = extract_colors(cutout_png([NAVY] * 19 + [MUSTARD]))

    assert [c["name"] for c in result] == ["navy"]


def test_unreadable_bytes_yield_no_colors():
    # Analysis must degrade to "no colours", never raise into the request path.
    assert extract_colors(b"not-an-image") == []
    assert extract_colors(b"") == []


def test_percentages_are_a_share_of_the_garment():
    result = extract_colors(cutout_png([NAVY, CREAM]))

    assert sum(c["percentage"] for c in result) == 100
