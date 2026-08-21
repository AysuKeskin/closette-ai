"""Fashion-embedding seam for visual similarity ("do I own something like this?").

Default is a deterministic mock vector so the whole vector-search path (pgvector)
works offline. Marqo-FashionSigLIP (self-hosted, no API key, CPU-ok) is the real
model — same 768-dim contract, swapped in via EMBED_PROVIDER=fashionsiglip.
"""
from __future__ import annotations

import hashlib
import io
import logging
from abc import ABC, abstractmethod

import numpy as np

from app.core.config import get_settings

log = logging.getLogger(__name__)

# ViT-B-16-SigLIP image embedding dim (Marqo-FashionSigLIP). Mock matches it so
# the pgvector column dimension is identical whichever provider is active.
EMBED_DIM = 768


class Embedder(ABC):
    model_name: str = "unknown"

    @abstractmethod
    def embed_image(self, image_bytes: bytes) -> list[float]:
        ...


class MockEmbedder(Embedder):
    """Deterministic pseudo-embedding from the image bytes. NOT semantically
    meaningful, but stable per image and unit-normalised, so pgvector wiring,
    dedupe and tests all work before the real model is enabled."""

    model_name = "mock-hash-768"

    def embed_image(self, image_bytes: bytes) -> list[float]:
        seed = int.from_bytes(hashlib.sha256(image_bytes or b"seed").digest()[:8], "big")
        rng = np.random.default_rng(seed)
        vec = rng.standard_normal(EMBED_DIM)
        vec /= np.linalg.norm(vec) + 1e-9
        return vec.astype(float).tolist()


class FashionSigLIPEmbedder(Embedder):
    """Marqo-FashionSigLIP via open_clip (self-hosted, CPU-ok). The model is loaded
    lazily and cached on the class, so mock mode never pays the cost."""

    model_name = "marqo-fashionSigLIP"
    _model = None
    _preprocess = None

    @classmethod
    def _load(cls):
        if cls._model is None:
            import open_clip  # heavy; imported only when actually used

            log.info("Loading Marqo-FashionSigLIP (first call downloads weights)…")
            model, _, preprocess = open_clip.create_model_and_transforms(
                "hf-hub:Marqo/marqo-fashionSigLIP"
            )
            model.eval()
            cls._model = model
            cls._preprocess = preprocess
            log.info("Marqo-FashionSigLIP ready.")
        return cls._model, cls._preprocess

    def embed_image(self, image_bytes: bytes) -> list[float]:
        import torch
        from PIL import Image

        model, preprocess = self._load()
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        tensor = preprocess(img).unsqueeze(0)
        with torch.no_grad():
            feats = model.encode_image(tensor)
            feats = feats / feats.norm(dim=-1, keepdim=True)
        return feats[0].cpu().tolist()


def get_embedder() -> Embedder:
    provider = get_settings().embed_provider.lower()
    if provider in ("fashionsiglip", "siglip"):
        return FashionSigLIPEmbedder()
    return MockEmbedder()
