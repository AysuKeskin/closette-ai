"""In-memory image builders for the tests.

Everything is generated at 160px — the colour pipeline's max working size — so
no resampling happens and pixel counts stay exact and deterministic.
"""
from __future__ import annotations

import io

from PIL import Image

SIZE = 160


def _png(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


def solid_png(hex_color: str, size: int = SIZE) -> bytes:
    """A full-frame photo with no visible background."""
    return _png(Image.new("RGB", (size, size), hex_color))


def garment_on_background(garment_hex: str, background_hex: str = "#FFFFFF",
                          size: int = SIZE, box: int = 80) -> bytes:
    """A hard-edged garment square centred on a plain background."""
    img = Image.new("RGB", (size, size), background_hex)
    offset = (size - box) // 2
    img.paste(Image.new("RGB", (box, box), garment_hex), (offset, offset))
    return _png(img)


def cutout_png(hex_colors: list[str], size: int = SIZE, box: int = 80,
               offset: tuple[int, int] | None = None) -> bytes:
    """A background-removed cutout: transparent canvas, one opaque block split
    into equal vertical bands (one per colour)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    left, top = offset if offset else ((size - box) // 2, (size - box) // 2)
    band = box // len(hex_colors)
    for i, hex_color in enumerate(hex_colors):
        width = box - i * band if i == len(hex_colors) - 1 else band
        img.paste(Image.new("RGBA", (width, box), hex_color), (left + i * band, top))
    return _png(img)


def jpeg_bytes(width: int = 64, height: int = 64, hex_color: str = "#3E5C99") -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (width, height), hex_color).save(buf, "JPEG")
    return buf.getvalue()
