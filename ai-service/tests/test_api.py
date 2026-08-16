import io

from fastapi.testclient import TestClient

from app.main import app

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
