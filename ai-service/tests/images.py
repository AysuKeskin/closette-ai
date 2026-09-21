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


def two_tone(base_hex: str, accent_hex: str, accent_share: float, size: int = SIZE) -> bytes:
    """A full-frame garment in two colours, the accent taking the given share of it.
    No backdrop: every pixel belongs to the piece."""
    img = Image.new("RGB", (size, size), base_hex)
    rows = int(size * accent_share)
    if rows:
        img.paste(Image.new("RGB", (size, rows), accent_hex), (0, 0))
    return _png(img)


def shaded(hex_color: str, shadow: float = 0.45, share: float = 0.5, size: int = SIZE) -> bytes:
    """One garment colour, part of it lit and part of it in shadow."""
    r, g, b = (int(hex_color.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
    img = Image.new("RGB", (size, size), (r, g, b))
    rows = int(size * share)
    if rows:
        dark = (int(r * shadow), int(g * shadow), int(b * shadow))
        img.paste(Image.new("RGB", (size, rows), dark), (0, size - rows))
    return _png(img)

