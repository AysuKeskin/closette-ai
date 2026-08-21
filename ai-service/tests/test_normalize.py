"""The canonicalise step is deterministic geometry: crop to content, centre on a
square transparent canvas, resize. Every property below is exactly assertable."""
import io

from PIL import Image

from app.pipeline.normalize import canonicalize

from tests.images import cutout_png, garment_on_background


def _open(png: bytes) -> Image.Image:
    return Image.open(io.BytesIO(png))


def _alpha_bbox(img: Image.Image) -> tuple[int, int, int, int]:
    return img.convert("RGBA").getchannel("A").point(lambda a: 255 if a > 20 else 0).getbbox()


def test_output_is_a_square_png_of_the_requested_size():
    png, _meta = canonicalize(cutout_png(["#22314E"]), size=256)
    img = _open(png)

    assert img.format == "PNG"
    assert img.size == (256, 256)
    assert img.mode == "RGBA"


def test_metadata_reports_the_original_dimensions():
    source = Image.new("RGBA", (120, 300), (0, 0, 0, 0))
    source.paste(Image.new("RGBA", (40, 40), "#22314E"), (10, 10))
    buf = io.BytesIO()
    source.save(buf, "PNG")

    _png, meta = canonicalize(buf.getvalue(), size=128)

    assert meta["original"] == {"width": 120, "height": 300}
    assert meta["canonical"] == {"width": 128, "height": 128}


def test_content_bbox_tracks_the_opaque_region():
    png = cutout_png(["#22314E"], size=160, box=80, offset=(10, 20))

    _out, meta = canonicalize(png)

    assert meta["content_bbox"] == [10, 20, 90, 100]


def test_content_is_centred_with_padding_on_all_sides():
    png, _meta = canonicalize(cutout_png(["#22314E"], offset=(0, 0)), size=256)
    left, top, right, bottom = _alpha_bbox(_open(png))

    # Same margin on opposite sides (±1px of integer rounding), and real padding.
    assert abs(left - (256 - right)) <= 1
    assert abs(top - (256 - bottom)) <= 1
    assert left > 0 and top > 0


def test_opaque_photo_is_cropped_to_its_background_frame():
    # No alpha channel: the background colour is estimated from the border.
    _out, meta = canonicalize(garment_on_background("#22314E", background_hex="#FFFFFF"))
    left, top, right, bottom = meta["content_bbox"]

    assert (left, top, right, bottom) == (40, 40, 120, 120)
