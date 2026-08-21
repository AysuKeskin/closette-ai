## What changed

<!-- One or two sentences. What does this do for the user? -->

## Why

<!-- The problem, or the flow (FR-xx) this belongs to. -->

## How to verify

<!-- The commands or the steps in the app that show it works. -->

## Checklist

- [ ] Unit tests cover the new behaviour (happy path, branch logic, ownership)
- [ ] `./gradlew test` / `pytest` / `npx tsc --noEmit` pass locally for what I touched
- [ ] Schema changes are a new `V<n>__*.sql` migration, not an edit to an applied one
- [ ] Log lines say what failed and for which entity; no secrets or image bytes
- [ ] Comments explain *why*, and only where the code isn't self-evident
- [ ] No new hard-coded colours or spacing in mobile — tokens only
