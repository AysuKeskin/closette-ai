"""Colour extraction is classic code, so it is fully assertable: a known pixel
value must always come back as the same fashion colour name."""
from app.pipeline.color import FASHION_COLORS, extract_colors

from tests.images import cutout_png, garment_on_background, shaded, solid_png, two_tone

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


def test_a_garment_the_colour_of_its_backdrop_keeps_its_colour():
    """The backdrop is estimated from the border frame, so a white piece shot on a
    white bed had its main colour deleted as "background" and came back as whatever
    small accent it happened to carry."""
    image = two_tone("#FFFFFF", "#B0413E", accent_share=0.20)

    colors = extract_colors(image)
    names = [c["name"] for c in colors]

    assert names == ["white", "red"]
    assert colors[0]["percentage"] == 80
    assert colors[1]["percentage"] == 20


def test_a_real_backdrop_is_still_removed():
    """The fix must not cost us background removal where there genuinely is one."""
    assert [c["name"] for c in extract_colors(
        garment_on_background("#22314E", "#FFFFFF"))] == ["navy"]
    assert [c["name"] for c in extract_colors(
        garment_on_background("#B0413E", "#9A9A9A"))] == ["red"]


def test_shadow_does_not_turn_a_light_garment_dark():
    """A shadow lowers lightness without adding colour, so a cream dress half in
    shadow used to come back half mauve, and a pink one half charcoal."""
    assert [c["name"] for c in extract_colors(shaded("#EFE3D0"))] == ["cream"]
    assert [c["name"] for c in extract_colors(shaded("#FFFFFF"))] == ["white"]
    assert [c["name"] for c in extract_colors(shaded("#D9A6AF"))] == ["dusty pink"]


def test_a_genuinely_dark_garment_is_left_alone():
    """The reference is the garment's own bright end, so nothing is dropped from a
    piece that is dark all over."""
    assert [c["name"] for c in extract_colors(shaded("#1C1C1C", shadow=0.6))] == ["black"]
    assert [c["name"] for c in extract_colors(shaded("#9A9A9A", shadow=0.85))] == ["grey"]


def test_a_dark_colour_is_not_mistaken_for_a_shadow():
    """Saturation is what separates them: navy and burgundy are dark AND coloured,
    so a navy-and-white piece keeps both halves."""
    names = [c["name"] for c in extract_colors(two_tone("#22314E", "#FFFFFF", accent_share=0.5))]
    assert sorted(names) == ["navy", "white"]

