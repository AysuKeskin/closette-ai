"""Fashion colour extraction — pure classic code, NO AI (per the AI-layer plan).

A VLM would say "pink"; a fashion app needs "dusty pink" vs "blush" vs "mauve".
So colour never touches the VLM: we read the actual garment pixels, convert to
CIE-LAB, and snap each dominant cluster to the nearest curated fashion colour.
Deterministic, fast, free — 0 model calls.
"""
from __future__ import annotations

import io

import numpy as np
from PIL import Image

# Curated fashion palette (name -> hex). Kept close to the app's own vocabulary
# so results speak the same language as the UI (dusty pink, blush, mauve …).
FASHION_COLORS: dict[str, str] = {
    "black": "#1C1C1C",
    "charcoal": "#3A3A3A",
    "grey": "#9A9A9A",
    "silver": "#C4C4C4",
    "white": "#F7F7F5",
    "cream": "#EFE7DA",
    "beige": "#D8C4A8",
    "tan": "#B08D57",
    "camel": "#C19A6B",
    "brown": "#6B4A34",
    "dusty pink": "#D9A6AF",
    "blush": "#EBC6CC",
    "rose": "#C97B84",
    "mauve": "#8D6670",
    "burgundy": "#5E2233",
    "red": "#B23A48",
    "coral": "#F08080",
    "orange": "#D2793A",
    "mustard": "#C99A2E",
    "yellow": "#E4C560",
    "olive": "#6B6B3A",
    "sage": "#A3B18A",
    "green": "#4E7A51",
    "teal": "#2E7D7B",
    "sky blue": "#8FB8DE",
    "blue": "#3E5C99",
    "navy": "#22314E",
    "lavender": "#B9A5D1",
    "purple": "#6D4C7D",
    "gold": "#C6A15B",
}

_MAX_DIM = 160            # downscale for speed; colour doesn't need resolution
_BG_DELTA = 14.0         # LAB distance under which a pixel counts as "background"
_MIN_SHARE = 0.06        # ignore colours below 6% of the garment

# Shading, not colour: a shadow lowers a pixel's lightness while leaving it about as
# neutral as it was, so a cream dress half in shadow used to catalogue as half mauve.
# A genuinely dark colour is dark AND saturated (navy sits at chroma 20, burgundy 29),
# which is what keeps a navy-and-white piece from being read as white with shadows.
_SHADOW_DROP_L = 15.0     # lightness below the garment's own bright end
_SHADOW_MAX_CHROMA = 12.0 # above this, the pixel is a colour rather than a shadow
_SHADOW_MAX_SHARE = 0.70  # never read a garment from a highlight alone


def _hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _srgb_to_lab(rgb: np.ndarray) -> np.ndarray:
    """rgb: (...,3) floats in 0..1 -> CIE-LAB (D65). Vectorised."""
    mask = rgb > 0.04045
    lin = np.where(mask, ((rgb + 0.055) / 1.055) ** 2.4, rgb / 12.92)
    r, g, b = lin[..., 0], lin[..., 1], lin[..., 2]
    x = 0.4124 * r + 0.3576 * g + 0.1805 * b
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = 0.0193 * r + 0.1192 * g + 0.9505 * b
    x /= 0.95047
    z /= 1.08883

    def f(t: np.ndarray) -> np.ndarray:
        return np.where(t > 0.008856, np.cbrt(t), 7.787 * t + 16.0 / 116.0)

    fx, fy, fz = f(x), f(y), f(z)
    lab = np.stack([116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)], axis=-1)
    return lab


_NAMES = list(FASHION_COLORS)
_PALETTE_RGB = np.array([_hex_to_rgb(FASHION_COLORS[n]) for n in _NAMES], dtype=float) / 255.0
_PALETTE_LAB = _srgb_to_lab(_PALETTE_RGB)


def _load_rgb(image_bytes: bytes) -> tuple[np.ndarray, np.ndarray | None]:
    """Return (rgb 0..1 HxWx3, alpha HxW or None) downscaled."""
    img = Image.open(io.BytesIO(image_bytes))
    alpha = None
    if img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info):
        img = img.convert("RGBA")
        img.thumbnail((_MAX_DIM, _MAX_DIM))
        arr = np.asarray(img, dtype=float) / 255.0
        return arr[..., :3], arr[..., 3]
    img = img.convert("RGB")
    img.thumbnail((_MAX_DIM, _MAX_DIM))
    return np.asarray(img, dtype=float) / 255.0, None


def _foreground_mask(lab: np.ndarray, alpha: np.ndarray | None) -> np.ndarray:
    """Boolean HxW mask of garment pixels. Uses the alpha channel when present
    (e.g. after background removal); otherwise estimates the background colour
    from the border frame and drops pixels close to it — all classic code."""
    if alpha is not None:
        return alpha > 0.5
    h, w = lab.shape[:2]
    frame = max(2, min(h, w) // 20)
    border = np.concatenate([
        lab[:frame].reshape(-1, 3), lab[-frame:].reshape(-1, 3),
        lab[:, :frame].reshape(-1, 3), lab[:, -frame:].reshape(-1, 3),
    ])
    bg = np.median(border, axis=0)

    # A border colour only means "backdrop" if the middle of the frame differs from
    # it. A white shirt shot on a white bed has the same colour at both, and treating
    # that as background deletes the garment's main colour — the shirt comes back as
    # nothing but its buttons. When the centre matches the border, keep every pixel.
    cy, cx = h // 2, w // 2
    ch, cw = max(1, h // 6), max(1, w // 6)
    centre = np.median(lab[cy - ch:cy + ch, cx - cw:cx + cw].reshape(-1, 3), axis=0)
    if np.linalg.norm(centre - bg) <= _BG_DELTA:
        return np.ones((h, w), dtype=bool)

    dist = np.linalg.norm(lab - bg, axis=-1)
    mask = dist > _BG_DELTA
    if mask.mean() < 0.05:  # no clear background (flat-lay / full-frame) → use all
        return np.ones((h, w), dtype=bool)
    return mask


def _drop_shadow(pixels: np.ndarray) -> np.ndarray:
    """Discards shaded pixels so they cannot vote for a colour the garment is not.

    The garment's own 90th-percentile lightness is the reference: measuring against
    the piece itself is what leaves a black garment alone, since its darkest pixels
    are not far below its brightest. Everything is kept when the shadow would swallow
    most of the frame — better an under-lit reading than one taken off a highlight.
    """
    lightness = pixels[:, 0]
    chroma = np.linalg.norm(pixels[:, 1:], axis=1)
    lit = np.percentile(lightness, 90)
    shadow = (lightness < lit - _SHADOW_DROP_L) & (chroma < _SHADOW_MAX_CHROMA)
    if 0 < shadow.mean() <= _SHADOW_MAX_SHARE:
        return pixels[~shadow]
    return pixels


def extract_colors(image_bytes: bytes, top: int = 3) -> list[dict]:
    """Dominant fashion colours as [{name, hex, percentage}], most-dominant first."""
    try:
        rgb, alpha = _load_rgb(image_bytes)
    except Exception:
        return []
    lab = _srgb_to_lab(rgb)
    mask = _foreground_mask(lab, alpha)
    pixels = lab[mask]
    if pixels.size == 0:
        return []
    pixels = _drop_shadow(pixels)

    # Nearest palette colour for every garment pixel (CIE76 ΔE).
    d = np.linalg.norm(pixels[:, None, :] - _PALETTE_LAB[None, :, :], axis=2)
    nearest = d.argmin(axis=1)
    counts = np.bincount(nearest, minlength=len(_NAMES)).astype(float)
    total = counts.sum()
    if total == 0:
        return []

    order = np.argsort(counts)[::-1]
    out: list[dict] = []
    for idx in order:
        share = counts[idx] / total
        if share < _MIN_SHARE or len(out) >= top:
            continue
        name = _NAMES[idx]
        out.append({"name": name, "hex": FASHION_COLORS[name], "percentage": round(share * 100)})
    return out
