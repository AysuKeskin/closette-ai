# Closette AI Service

Provider-independent FastAPI service for vision/NLP. The Spring Boot backend
talks only to this HTTP API — it never knows which model is behind it (NFR-13).

## Run

```bash
cd ai-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Docs: http://localhost:8000/docs · Health: http://localhost:8000/health

## Endpoints

| Method | Path                   | Purpose                              |
|--------|------------------------|--------------------------------------|
| POST   | `/analyze/clothing`    | Clothing photo → structured analysis |
| POST   | `/analyze/beauty`      | Beauty photo → brand/product guess   |
| POST   | `/ingredients/explain` | Ingredient name → plain explanation  |
| GET    | `/health`              | Liveness + active provider           |

## Providers

`AI_PROVIDER` (env) selects the backend:

- `mock` (default) — deterministic, realistic responses, no API keys required.
- `qwen` — placeholder for the real Qwen-VL integration (`QWEN_API_KEY` required).

Add a new provider by subclassing `app/providers/base.py::AIProvider` and wiring
it in `app/providers/__init__.py`.

## Test

```bash
pytest
```
