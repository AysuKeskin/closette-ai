# Closette-AI

[![CI](https://github.com/AysuKeskin/closette-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/AysuKeskin/closette-ai/actions/workflows/ci.yml)

AI-powered personal wardrobe and beauty inventory assistant for outfit planning,
product tracking, and smarter shopping decisions.

## Architecture

```
 Mobile (Expo / React Native, TypeScript)
        │  REST + JWT
        ▼
 Backend (Spring Boot, Java 21)  ──►  PostgreSQL · Redis · MinIO (S3)
        │  provider-independent AI seam (NFR-13)
        ▼
 AI Service (FastAPI, Python)     ──►  provider: mock (default) | qwen | …
```

- **mobile/** — the app: 5-tab navigation, dusty-pink design system, Flow A end-to-end.
- **backend/** — domain-first Spring Boot: `auth · user · wardrobe · beauty · outfit · wishlist · recommendation · ai`.
- **ai-service/** — FastAPI vision/NLP with a swappable provider; ships a no-keys **mock** so everything runs offline.

Backend packages are organized by feature first, then by layer:

```text
backend/src/main/java/ai/closette/<feature>/
  controller/   # HTTP endpoints
  service/      # business use cases
  repository/   # Spring Data persistence
  model/        # entities, enums, domain records
  dto/          # request/response contracts
```

## Quick start

### 1. Infrastructure (Postgres, Redis, MinIO)

```bash
cp .env.example .env
docker compose up -d          # postgres:5432 · redis:6379 · minio:9000 (console :9001)
```

### 2. AI service (FastAPI)

```bash
cd ai-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000     # http://localhost:8000/docs
```

### 3. Backend (Spring Boot)

```bash
cd backend
./gradlew bootRun             # http://localhost:8080/swagger-ui
```

Flyway applies the schema (`V1__init.sql`) on first boot.

### 4. Mobile (Expo)

```bash
cd mobile
npm install
npx expo start                # press i / a, or scan with Expo Go
```

> Android emulator reaches the host at `http://10.0.2.2:8080` (handled
> automatically). For a physical device, run with
> `EXPO_PUBLIC_API_BASE_URL=http://<your-LAN-ip>:8080 npx expo start`.

### Optional: full Docker stack

```bash
cp .env.example .env
docker compose --profile app up --build
```

This starts Postgres, Redis, MinIO, the FastAPI AI service, and the Spring Boot
backend in containers. The Expo mobile app still runs locally.

## MVP backbone flows

- **Flow A** — photo → AI analysis → confirm → add to wardrobe ✅ (wired end-to-end)
- **Flow B** — beauty add + ingredients (backend + shells; search/barcode later)
- **Flow C** — Get Ready: describe an occasion → complete look from your items ✅
- **Flow D** — Should I Buy This: compare a candidate against your wardrobe ✅

## Tests

```bash
cd backend && ./gradlew test          # services, auth, AI seam (H2)
cd ai-service && pytest               # pipeline, providers, API shape
cd mobile && npx tsc --noEmit         # type safety
```

The backend suite runs every feature service against H2 — wardrobe, beauty,
outfits, recommendations, wishlist, profile — plus auth (tokens, verification,
password reset) and the AI client's fallback behaviour. The AI-service suite
covers the classic-code pipeline (colour, normalisation, embeddings) against
generated images, provider selection, and every endpoint.

Both suites are hermetic: no network, no API key, no model call. The AI service
pins every provider to its offline mock, and the backend mocks the AI seam, so a
run means the same thing on a laptop with a live key configured as it does in CI.

## CI

`.github/workflows/ci.yml` runs on every pull request and push to `main`:

| Job | What it guards |
|---|---|
| `backend` | `./gradlew build` — compile, 123 tests, bootJar |
| `ai-service` | `pytest` — 83 tests |
| `mobile` | `npm ci` + `tsc --noEmit` |
| `secret-scan` | Gitleaks over the full history |
| `smoke` | The compose stack end-to-end: register → analyze a photo → save → read back, over real Postgres/pgvector and MinIO |
| `build-scan-push` | Builds both images, Trivy gate on CRITICAL/HIGH, pushes to GHCR (never from a PR) |
| `sign-and-sbom` | Keyless cosign signature + SPDX SBOM attested to the image |
| `release` | On a `v*` tag: GitHub release with CHANGELOG notes and the SBOMs |

A pull request validates and scans but publishes nothing. The default token is
read-only; each job escalates only what it needs.

The smoke test is the one place the wiring is proven — the unit suites use H2 and
mocked storage, so Flyway on pgvector, MinIO presigned URLs, JWT through the
filter chain and the backend → AI-service hop are only ever exercised there. Run
it locally against a running stack with:

```bash
docker compose --profile app up -d --build
bash .github/scripts/smoke.sh
```

## Design tokens

All UI colors/spacing/typography come from `mobile/src/theme/tokens.ts` — change
the theme in one place. Core palette: dusty pink `#D9A6AF`, warm white `#FAF7F5`,
light rose `#F4E6E8`, mauve `#8D6670`, charcoal `#292527`.
