"""Background-removal seam. Default is a no-model passthrough; BiRefNet plugs in
later (self-hosted) with zero changes to callers. No paid API, ever."""
from __future__ import annotations

import io
from abc import ABC, abstractmethod

from PIL import Image

from app.core.config import get_settings


class Segmenter(ABC):
    @abstractmethod
    def remove_background(self, image_bytes: bytes) -> tuple[bytes, bool]:
        """Return (RGBA PNG bytes, removed?). removed=False means passthrough."""
        ...


class PassthroughSegmenter(Segmenter):
    """No model available yet — hand the image back as RGBA, untouched. Honest:
    reports removed=False so the rest of the pipeline knows there's no real mask."""

    def remove_background(self, image_bytes: bytes) -> tuple[bytes, bool]:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGBA")
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return buf.getvalue(), False


class BiRefNetSegmenter(Segmenter):
    """Real high-quality foreground segmentation (self-hosted). Stub until the
    model + weights are installed on a GPU box."""

    def remove_background(self, image_bytes: bytes) -> tuple[bytes, bool]:
        raise NotImplementedError(
            "BiRefNet not installed. Set SEG_PROVIDER=none for passthrough, or "
            "add the model on a GPU host."
        )


def get_segmenter() -> Segmenter:
    provider = get_settings().seg_provider.lower()
    if provider == "birefnet":
        return BiRefNetSegmenter()
    return PassthroughSegmenter()
