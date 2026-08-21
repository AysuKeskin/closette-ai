"""The mock embedder is not semantically meaningful, but the pgvector contract it
has to satisfy — 512 dims, unit length, stable per image — is."""
import math

import pytest

from app.providers.embedding import (
    EMBED_DIM,
    FashionSigLIPEmbedder,
    MockEmbedder,
    get_embedder,
)


def test_vector_matches_the_pgvector_contract():
    vector = MockEmbedder().embed_image(b"item-photo")

    assert len(vector) == EMBED_DIM == 768
    assert math.isclose(math.sqrt(sum(v * v for v in vector)), 1.0, rel_tol=1e-6)


def test_same_image_always_embeds_to_the_same_vector():
    # Stability is what makes dedupe and similarity reproducible across restarts.
    assert MockEmbedder().embed_image(b"item-photo") == MockEmbedder().embed_image(b"item-photo")


def test_different_images_embed_differently():
    assert MockEmbedder().embed_image(b"one") != MockEmbedder().embed_image(b"two")


def test_empty_image_still_yields_a_valid_vector():
    vector = MockEmbedder().embed_image(b"")

    assert len(vector) == EMBED_DIM


def test_embedder_defaults_to_the_mock(set_env):
    set_env(EMBED_PROVIDER=None)

    embedder = get_embedder()

    assert isinstance(embedder, MockEmbedder)
    assert embedder.model_name == "mock-hash-768"


@pytest.mark.parametrize("configured", ["fashionsiglip", "siglip", "FashionSigLIP"])
def test_embedder_follows_configuration(set_env, configured):
    set_env(EMBED_PROVIDER=configured)

    assert isinstance(get_embedder(), FashionSigLIPEmbedder)


def test_fashionsiglip_defers_loading_the_model_until_it_is_used():
    # Constructing the embedder must stay free — the weights are hundreds of MB
    # and would otherwise be downloaded on every startup, mock mode included.
    embedder = FashionSigLIPEmbedder()

    assert embedder.model_name == "marqo-fashionSigLIP"
    assert FashionSigLIPEmbedder._model is None
