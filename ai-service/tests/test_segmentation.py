"""Background removal defaults to a passthrough. The contract that matters is
that it stays honest about not having removed anything."""
import io

import pytest
from PIL import Image

from app.providers.segmentation import (
    BiRefNetSegmenter,
    PassthroughSegmenter,
    get_segmenter,
)

from tests.images import jpeg_bytes


def test_passthrough_returns_rgba_png_and_reports_no_removal():
    png, removed = PassthroughSegmenter().remove_background(jpeg_bytes())
    img = Image.open(io.BytesIO(png))

    assert removed is False  # downstream must know there is no real mask
    assert img.format == "PNG"
    assert img.mode == "RGBA"


def test_passthrough_preserves_the_image_dimensions():
    png, _removed = PassthroughSegmenter().remove_background(jpeg_bytes(width=120, height=90))

    assert Image.open(io.BytesIO(png)).size == (120, 90)


def test_segmenter_defaults_to_passthrough(set_env):
    set_env(SEG_PROVIDER=None)

    assert isinstance(get_segmenter(), PassthroughSegmenter)


def test_segmenter_follows_configuration(set_env):
    set_env(SEG_PROVIDER="birefnet")

    assert isinstance(get_segmenter(), BiRefNetSegmenter)


def test_birefnet_says_it_is_not_installed_yet():
    with pytest.raises(NotImplementedError, match="BiRefNet not installed"):
        BiRefNetSegmenter().remove_background(jpeg_bytes())
