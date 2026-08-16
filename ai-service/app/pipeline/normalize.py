"""Canonical image normalisation — classic code, NO AI, NO generative model.

Makes every garment cutout sit the same way: content cropped, centred on a
square transparent canvas with consistent padding, resized to one size. This is
the deterministic "make it look like an icon" step (crop/center/pad/scale).
Un-folding / reconstructing hidden parts is explicitly out of scope (Phase 2+).
"""
from __future__ import annotations

import io

import numpy as np
from PIL import Image

_CANVAS = 768
_PAD_RATIO = 0.08


def _content_bbox(img: Image.Image) -> tuple[int, int, int, int]:
    """Bounding box of the real content — from alpha if present, else by trimming
    a border-estimated background colour. Returns (l, t, r, b)."""
    if img.mode == "RGBA":
        alpha = np.asarray(img)[..., 3]
        ys, xs = np.where(alpha > 20)
        if xs.size and ys.size:
            return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
        return 0, 0, img.width, img.height

    arr = np.asarray(img.convert("RGB"), dtype=int)
    h, w = arr.shape[:2]
    frame = max(2, min(h, w) // 20)
    border = np.concatenate([
        arr[:frame].reshape(-1, 3), arr[-frame:].reshape(-1, 3),
        arr[:, :frame].reshape(-1, 3), arr[:, -frame:].reshape(-1, 3),
    ])
    bg = np.median(border, axis=0)
    dist = np.linalg.norm(arr - bg, axis=-1)
    ys, xs = np.where(dist > 24)
    if xs.size and ys.size:
        return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
    return 0, 0, w, h


def canonicalize(image_bytes: bytes, size: int = _CANVAS) -> tuple[bytes, dict]:
    """Return (PNG bytes of the canonical square asset, metadata)."""
    img = Image.open(io.BytesIO(image_bytes))
    original = {"width": img.width, "height": img.height}
    if img.mode not in ("RGB", "RGBA"):
        img = img.convert("RGBA" if "transparency" in img.info else "RGB")

    l, t, r, b = _content_bbox(img)
    cropped = img.crop((l, t, r, b))

    side = int(max(cropped.width, cropped.height) * (1 + 2 * _PAD_RATIO))
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    offset = ((side - cropped.width) // 2, (side - cropped.height) // 2)
    canvas.paste(cropped.convert("RGBA"), offset)
    canvas = canvas.resize((size, size), Image.LANCZOS)

    buf = io.BytesIO()
    canvas.save(buf, "PNG")
    return buf.getvalue(), {
        "original": original,
        "canonical": {"width": size, "height": size},
        "content_bbox": [l, t, r, b],
    }
