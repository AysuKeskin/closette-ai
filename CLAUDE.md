# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

Closette-AI — an AI wardrobe & beauty inventory assistant. Three deployables:

| Path          | Stack                                  | Runs on |
|---------------|----------------------------------------|---------|
| `mobile/`     | Expo / React Native, TypeScript        | Expo Go |
| `backend/`    | Spring Boot 3.3, Java 21, Gradle       | :8080   |
| `ai-service/` | FastAPI, Python 3.10, Pydantic v2      | :8000   |

Infra via `docker compose up -d`: Postgres (pgvector) · Redis · MinIO.
The mobile app talks only to the backend; the backend talks to the AI service
only over HTTP. Never let mobile call the AI service directly.

## Commands

```bash
# backend — refuses to boot on the placeholder JWT_SECRET, so export a real one
cd backend && set -a && . ../.env && set +a && ./gradlew bootRun
cd backend && ./gradlew test               # tests (H2, profile "test")
cd backend && ./gradlew test --tests '*WardrobeServiceTest'

# ai-service
cd ai-service && source .venv/bin/activate && uvicorn app.main:app --reload --port 8000
cd ai-service && pytest
cd ai-service && pytest tests/test_api.py::test_health

# mobile
cd mobile && npx expo start
cd mobile && npm run typecheck             # also proves en/tr have the same keys
cd mobile && npm run check:i18n            # placeholders + nothing left untranslated

# end-to-end, against the compose stack
docker compose --profile app up -d --build && bash .github/scripts/smoke.sh
```

## CI

`.github/workflows/ci.yml` gates every PR: the three component suites, Gitleaks,
the compose smoke test, then image build → Trivy CRITICAL/HIGH gate → GHCR push
→ cosign signature + SBOM (publishing steps are skipped on PRs). Keep it green
and keep it honest:

- A job that needs a credential or a running model does not belong in CI. The
  suites are hermetic by design — if a change makes one need a key, fix the test.
- Schema changes are proven by the smoke job, not by H2. It is the only place
  Flyway runs on real Postgres with pgvector.
- Adding a step? Prefer a script under `.github/scripts/` over inline YAML, so it
  can be run locally the same way CI runs it.

## Two languages

The app ships in English and Turkish, and both are first-class. One rule decides
where every string belongs:

> **Prose is translated. Catalogue values are not.**

A colour is stored as `"navy"` and a category as `TOPS` whatever the user reads,
because the backend filters and the AI service match on those values — a Turkish
user's wardrobe has to stay comparable to an English one. Only the label on
screen changes.

- **Mobile** — `src/i18n/en.ts` is the source of truth; `tr.ts` is typed as
  `Dictionary`, so a missing key is a compile error. Screens read copy through
  `useT()` and never inline a sentence. Catalogue values are rendered through
  `useDomainLabels()` (`src/i18n/domain.ts`), which maps a stored value to a
  label and falls back to the raw value for anything it doesn't know.
- **Backend** — every user-facing sentence is a key in
  `messages_{en,tr}.properties`, resolved by `Messages` against the request's
  `Accept-Language`. Look up **whole sentences**, never fragments you concatenate:
  English puts a count before its noun, Turkish inflects around it, and the two
  cannot share a template. Validation messages use `{key}` in the annotation.
- **AI service** — prose endpoints (`/ingredients/explain`, `/generate/*`) take a
  `lang`; the attribute endpoints deliberately do not. The mock provider answers
  in both languages so the offline app is fully bilingual, and its text parser
  understands Turkish words but always emits canonical English attributes.
- **Turkish casing**: `toUpperCase()` turns "i" into "I", not "İ". Use
  `toLocaleUpperCase('tr-TR')` anywhere user text is capitalised.
- **A sentence with something styled inside it** (an emphasised email, a name)
  uses `splitAround()` — the placeholder sits in a different position in each
  language, so prefix/suffix key pairs break.

Guards: `MessageBundleTest` (key parity, placeholder parity, nothing left
untranslated or unused), `AcceptLanguageTest` (the header actually switches a
live response), `tests/test_language.py` (a Turkish description catalogues
identically to its English twin), `npm run check:i18n` (the same checks on the
mobile side, including keys nothing renders), and the smoke job, which asserts
the error *code* stays put while the message changes.

A key the app builds at runtime — `t(rule.label)` — is invisible to the mobile
checker, so its prefix goes in `DYNAMIC_PREFIXES` in `scripts/check-i18n.mjs`.
Keep that list short: every entry is a spot the checker cannot see.

## Architecture rules

- **Backend is feature-first**: `ai.closette.<feature>/{controller,service,repository,model,dto}`.
  A new feature gets its own package — do not widen `common/`.
- **Controllers stay thin**: validate + delegate. Business logic lives in the
  service. Every response is wrapped in `ApiResponse<T>` (`{success,data,error}`);
  the mobile client's `unwrap()` depends on that shape.
- **Secrets have no working defaults.** `JwtService` refuses to start on the
  placeholder in `application.yml`: a default that boots is a default that ships.
  Anything new that signs or encrypts follows the same rule.
- **User scoping is non-negotiable.** Get the caller from
  `SecurityUtil.currentUserId()` and scope every query by it
  (`findByIdAndUserId(...)`, not `findById`). A missing row for another user's id
  is a 404, never a 403 leak.
- **Errors**: throw `ApiException` with an `ErrorCode` and a **message key** from
  `MessageKeys` — never a sentence. `GlobalExceptionHandler` resolves the key in
  the caller's language and wraps it in the envelope. Do not return raw
  exceptions or ad-hoc maps.
- **Schema is Flyway's.** `ddl-auto: none`. Any schema change is a new
  `V<n>__name.sql` in `backend/src/main/resources/db/migration/` — never edit an
  applied migration.
- **AI layer (see also `ai-service/app/core/config.py`)**: the VLM is used *only*
  where a model is genuinely needed (attribute extraction, OCR, reasoning).
  Colour extraction, normalisation, scoring and similarity plumbing are classic
  code and must stay that way. Providers are swappable behind
  `app/providers/base.py::AIProvider` — adding a backend means adding one
  subclass and wiring `app/providers/__init__.py`, nothing else.
- **AI calls are best-effort.** Embedding/analysis failures must degrade
  gracefully (log a warning, keep the user's data) — they never break a save.
- **Mobile**: server state through React Query hooks in `src/features/`, HTTP in
  `src/api/`, auth token in `src/store/auth.ts`. All colours/spacing/typography
  come from `src/theme/tokens.ts` — no hard-coded hex or magic padding in
  screens.

## Comments

Comments explain **why**, not **what**. The bar: would a competent reader be
confused without it?

Write a comment when:
- a decision is non-obvious (`// Flush so the row exists before the JDBC embedding UPDATE hits the same row.`)
- something is a deliberate trade-off or a workaround for external behaviour
- a class/record/service needs one sentence on its role (Javadoc / docstring)
- a value is subtle (unit, ordering guarantee, security implication)

Do **not** write:
- restatements of the code (`// increment counter`, `// set the name`)
- section banners for the sake of decoration, `// --- imports ---`
- narration of a change (`// added new field here`, `// updated per request`)
- TODOs without an owner or a concrete next step
- commented-out code — delete it, git remembers

No AI-slop comments. If a comment could be deleted without losing information,
it should be deleted. Existing files show the right density: mostly bare code,
one short line where the intent is genuinely hidden.

## Tests

Every feature ships with the tests that prove it. A PR-sized change without a
test is incomplete unless the change is purely cosmetic (formatting, copy,
tokens).

- **Backend** — JUnit 5 + AssertJ, `@SpringBootTest @ActiveProfiles("test")`
  against H2. Test the service layer, not getters. Cover: the happy path, the
  filter/branch logic, and the ownership check (another user's id → not found).
  Mirror the source package: `src/test/java/ai/closette/<feature>/...`, and use
  `support/TestData` to arrange users and items. Pure logic (JWT, converters,
  the AI client, the exception handler) gets a plain JUnit test with no context.
  Mock `AIService`/`StorageService` with `@MockBean` only where a test really
  needs MinIO or the model — every extra mock combination spawns another Spring
  context.
- **AI service** — pytest + `TestClient`. Cover response *shape*, determinism of
  the mock provider, field aliases, and known-input behaviour. New endpoint →
  new test in `ai-service/tests/`. Images come from `tests/images.py`, generated
  at 160px so no resampling blurs the pixels an assertion depends on; env-driven
  provider selection goes through the `set_env` fixture, which clears the cached
  `Settings`.
- **Mobile** — no runner configured; `npm run typecheck` and `npm run check:i18n`
  must pass. Keep logic out of components so it stays testable when a runner is
  added.
- **Anything user-facing** — add the key to both bundles in the same change. A
  new sentence with only an English side fails `MessageBundleTest` or
  `check-i18n.mjs`, whichever layer it lives in.

Name tests for the behaviour (`createsAndFiltersItems`,
`test_analyze_clothing_is_deterministic`), not the method under test. Assert on
outcomes; don't assert on log output — the one exception is a swallowed failure
whose only trace *is* the log line (the VLM→mock fallback). Never delete or
weaken a failing test to make a build green — fix the cause or say the test is
failing.

Two things H2 cannot do, so don't try to test them there: pgvector's `<=>`
operator (visual similarity), and the `embedding` column, which is deliberately
absent from the JPA entity. Cover the code *around* the vector query instead —
ownership checks, and that a failed embedding never blocks a save.

## Logging

A log line is written to be read at 3am by someone who doesn't have the code
open. Make it say *what failed, for which entity, and what happens next.*

- Backend: SLF4J, `private static final Logger log = LoggerFactory.getLogger(X.class);`
  Parameterised messages, exception as the last argument:
  `log.warn("Embedding failed for item {}", item.getId(), e);`
- Levels: `error` = needs a human; `warn` = degraded but handled (AI/storage
  fallbacks); `info` = notable state change, not per-request chatter; `debug` =
  developer detail (`ai.closette` is at DEBUG locally).
- Include the identifier that makes the line actionable (item id, bucket + key,
  path). Never log secrets, JWTs, passwords, raw image bytes, or full request
  bodies; emails only where the flow is about email.
- No `log.info("here")`, no logging inside hot loops, no logging an exception and
  rethrowing it at the same level (pick one owner for the message).
- Mobile has no `console.log` in committed code — surface failures in the UI via
  `toApiError`.

## Style

- Match the file you're editing. Java: constructor injection, records for DTOs,
  no Lombok (not on the classpath). Python: type hints, Pydantic models in
  `app/schemas/`. TS: `type`-only imports where possible, no `any`.
- Keep English for code, comments, logs and docs — the whole repo is English.
- Commits follow `type(scope): summary` — `feat(backend): ...`,
  `chore(infra): ...`, `fix(mobile): ...`.
