"""Colour extraction is classic code, so it is fully assertable: a known pixel
value must always come back as the same fashion colour name."""
import io

from PIL import Image, ImageDraw

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



def test_a_pale_garment_on_a_white_backdrop_keeps_its_own_colour():
    """
    Cream on a white sheet is the case a fixed background threshold cannot serve.

    Widening it far enough to absorb a noisy backdrop also swallowed the garment,
    and the frame came back as mostly "white" with the real colour second. The
    threshold now scales with how much the backdrop itself varies, so a clean
    sheet leaves a pale garment standing.
    """
    colors = extract_colors(garment_on_background("#F5F0E8", "#FFFFFF"))

    assert colors, "the garment was read as part of the backdrop"
    assert colors[0]["name"] == "cream"


def test_a_garment_the_same_colour_as_the_backdrop_still_reports_that_colour():
    # The opposite failure, and the reason the give-up branch exists: a white
    # shirt on a white bed must not come back as nothing at all.
    colors = extract_colors(garment_on_background("#FFFFFF", "#FFFFFF"))

    assert colors
    assert colors[0]["name"] == "white"


def test_a_mid_grey_garment_is_named_a_grey_and_not_a_colour():
    """
    The neutral ladder used to jump from grey (L≈64) to charcoal (L≈24).

    Anything in between measured closer to a chromatic colour than to any grey,
    so mid-grey trousers catalogued as "mauve". The names are what the stylist
    and the filters reason over, so this is not cosmetic.
    """
    for hex_colour in ("#6F6A66", "#707070", "#5A5A5A"):
        top = extract_colors(solid_png(hex_colour))[0]["name"]
        assert "grey" in top or top == "charcoal", f"{hex_colour} came back as {top}"


def test_a_striped_garment_is_read_from_its_cloth_not_its_stripes():
    """
    Thin light stripes set the bright reference, the cloth between them sat far
    enough below it to look shadowed, and two thirds of the garment was thrown
    away — leaving the stripes to name the piece. The lit part now has to be the
    majority before anything is dropped as shadow.
    """
    img = Image.new("RGB", (240, 240), "#6F6A66")
    draw = ImageDraw.Draw(img)
    for x in range(6, 240, 12):
        draw.line([(x, 0), (x, 240)], fill="#D8D5D0", width=2)
    buf = io.BytesIO()
    img.save(buf, "PNG")

    assert extract_colors(buf.getvalue())[0]["name"] == "dark grey"


def test_a_second_backdrop_colour_does_not_become_one_of_the_garment_colours():
    """
    A photo on a bed usually catches the wall above it too.

    A single background estimate lands on whichever surface covers most of the
    frame, and the other survives as "garment": grey trousers came back with a
    quarter of them beige, which was the wall.
    """
    img = Image.new("RGB", (240, 240), "#EFEDE9")   # bedsheet
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 240, 34], fill="#D6C9B4")  # wall along the top
    draw.rectangle([70, 34, 170, 230], fill="#6F6A66")  # the garment
    buf = io.BytesIO()
    img.save(buf, "PNG")

    names = [c["name"] for c in extract_colors(buf.getvalue())]

    assert names[0] == "dark grey"
    assert "beige" not in names, f"the wall was counted as the garment: {names}"
