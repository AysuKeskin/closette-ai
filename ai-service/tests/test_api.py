import base64
import io
import math

from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.pipeline.color import FASHION_COLORS

from tests.images import cutout_png, jpeg_bytes

client = TestClient(app)


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_analyze_clothing_shape():
    fake_image = io.BytesIO(b"fake-image-bytes")
    resp = client.post(
        "/analyze/clothing",
        files={"file": ("item.jpg", fake_image, "image/jpeg")},
    )
    assert resp.status_code == 200
    body = resp.json()
    for key in ("category", "subcategory", "colors", "pattern", "styles", "seasons", "confidence"):
        assert key in body
    assert isinstance(body["colors"], list)
    assert 0.0 <= body["confidence"] <= 1.0


def test_analyze_clothing_is_deterministic():
    files = {"file": ("item.jpg", b"same-bytes", "image/jpeg")}
    first = client.post("/analyze/clothing", files=files).json()
    second = client.post("/analyze/clothing", files=files).json()
    assert first == second


def test_analyze_beauty_uses_camelcase_alias():
    resp = client.post(
        "/analyze/beauty",
        files={"file": ("cream.jpg", b"beauty", "image/jpeg")},
    )
    assert resp.status_code == 200
    assert "productName" in resp.json()


def test_explain_known_ingredient():
    resp = client.post("/ingredients/explain", json={"name": "Niacinamide"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["name"] == "Niacinamide"
    assert "vitamin b3" in body["explanation"].lower()


def test_health_reports_the_active_providers():
    body = client.get("/health").json()

    # Ops needs to see which backends are live without reading the container env.
    assert set(body) == {"status", "vlm", "segmentation", "embedding"}


def test_analyze_clothing_colors_come_from_the_image_not_the_model():
    navy_cutout = cutout_png([FASHION_COLORS["navy"]])

    body = client.post(
        "/analyze/clothing",
        files={"file": ("item.png", navy_cutout, "image/png")},
    ).json()

    assert body["colors"] == ["navy"]
    assert body["color_details"] == [
        {"name": "navy", "hex": FASHION_COLORS["navy"], "percentage": 100}
    ]


def test_analyze_clothing_keeps_model_colors_when_extraction_finds_nothing():
    body = client.post(
        "/analyze/clothing",
        files={"file": ("item.jpg", b"not-an-image", "image/jpeg")},
    ).json()

    assert body["colors"]  # falls back to the provider's guess rather than empty
    assert body["color_details"] == []


def test_analyze_requires_a_file():
    assert client.post("/analyze/clothing").status_code == 422


def test_embed_item_returns_a_unit_vector_of_the_contract_dimension():
    body = client.post(
        "/embed/item",
        files={"file": ("item.jpg", jpeg_bytes(), "image/jpeg")},
    ).json()

    assert body["dim"] == len(body["vector"]) == 768
    assert body["model"]
    assert math.isclose(math.sqrt(sum(v * v for v in body["vector"])), 1.0, rel_tol=1e-6)


def test_embed_item_is_stable_across_calls():
    files = {"file": ("item.jpg", jpeg_bytes(), "image/jpeg")}
    first = client.post("/embed/item", files=files).json()
    second = client.post("/embed/item", files=files).json()

    assert first["vector"] == second["vector"]


def test_canonicalize_returns_a_square_png():
    resp = client.post(
        "/image/canonicalize",
        files={"file": ("item.png", cutout_png([FASHION_COLORS["navy"]]), "image/png")},
    )
    body = resp.json()
    decoded = Image.open(io.BytesIO(base64.b64decode(body["image_base64"])))

    assert body["width"] == body["height"] == 768
    assert body["background_removed"] is False
    assert decoded.format == "PNG"


def test_remove_background_admits_when_it_is_a_passthrough():
    body = client.post(
        "/image/remove-background",
        files={"file": ("item.jpg", jpeg_bytes(width=90, height=60), "image/jpeg")},
    ).json()

    assert body["background_removed"] is False
    assert (body["width"], body["height"]) == (90, 60)


def test_explain_unknown_ingredient_does_not_invent_an_answer():
    body = client.post("/ingredients/explain", json={"name": "Unobtainium"}).json()

    assert "don't have a verified description" in body["explanation"]


def test_explain_ingredient_requires_a_name():
    assert client.post("/ingredients/explain", json={}).status_code == 422


def test_generate_outfit_picks_from_the_items_it_was_given():
    # RAG: the response must only ever contain ids from the retrieved wardrobe.
    wardrobe = [
        {"id": "dress-1", "name": "Mini dress", "category": "dresses"},
        {"id": "shoes-1", "name": "Ankle boots", "category": "shoes"},
    ]

    body = client.post("/generate/outfit", json={"occasion": "dinner", "items": wardrobe}).json()

    assert body["itemIds"] == ["dress-1", "shoes-1"]
    assert body["title"]
    assert "dinner" in body["rationale"]


def test_generate_outfit_with_no_wardrobe_returns_an_empty_look():
    body = client.post("/generate/outfit", json={"occasion": "dinner", "items": []}).json()

    assert body["itemIds"] == []


def test_generate_outfit_defaults_the_optional_fields():
    # The backend omits `preferences` today; the schema must not require it.
    assert client.post("/generate/outfit", json={"items": []}).status_code == 200
