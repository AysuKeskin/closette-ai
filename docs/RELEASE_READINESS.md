# Security changes and release preparation

The application code is prepared for production configuration; this document is
not a claim that a production service or App Store listing has been deployed.

## Data and session changes

- Flyway V12 adds revocable sessions and password-reset attempt counters. Existing
  stateless tokens have no live session and stop working on rollout: users must
  sign in again. Deploy the mobile logout changes with this backend release.
- Refresh tokens rotate on use. Replaying an older token revokes that session.
  Logout revokes the session; password reset revokes every session. A session has
  an absolute expiry, rather than an indefinitely sliding refresh lifetime.
  Offline logout clears the device; remote revocation requires network access.
- Five wrong reset codes consume the code. A database lock prevents concurrent
  requests from losing attempts. New reset codes start a fresh counter.
- Rate limits now enforce by default and fail closed if Redis is unavailable.
  The backend ignores raw forwarded headers. Production trusts only the reverse
  proxy address specified in `application-prod.yml`.
- Flyway V13 tracks photo ownership and pending deletion. Keys must have the
  authenticated owner's UUID prefix and a valid generated filename. A new item
  must claim a tracked upload; arbitrary keys cannot be attached or presigned.
- Unsaved uploads expire after 24 hours. Product deletion queues the image for
  removal after the final wardrobe/wishlist reference disappears. Account
  deletion queues all tracked images, including pending uploads, in the database
  transaction. Tracking rows survive account deletion. Cleanup runs every minute;
  failures retry after five minutes. Multiple workers lock each image before
  deleting. A daily reconciliation discovers older untracked uploads. Existing
  custom bucket names are supported when adopting an owned legacy reference.
- The cleanup ledger also handles account deletion while an upload is in flight.
  Failed or interrupted uploads retain a cleanup record. Deletion is eventual:
  storage outages can delay physical removal even after the app hides an item.
- Flyway V14 stores AI consent and its disclosure version. AI endpoints enforce
  consent before processing. Changing the provider, policy URL or processing
  details changes the version and requires new permission. Profile supports
  withdrawal. Both backend and AI service must use the same `AI_PROVIDER` value;
  the production Compose file supplies it from one variable.
- Each mobile session gets a fresh query cache. Old requests/refreshes cannot
  update the next session. Sensitive credentials remain in SecureStore.
- Tomcat is pinned to 10.1.59. The old Trivy exclusions are removed. A clean image
  vulnerability scan remains a release gate; unit tests do not substitute for it.
- Expo/Metro patch updates remove the vulnerable `image-size` dependency.
  npm overrides keep xmldom on patched 0.8.15/0.9.12 and js-yaml on 4.3.2.
  The `xcode` override uses uuid 11.1.1, the CommonJS-compatible release; xcode
  only calls its unchanged `v4()` interface. Review these overrides on SDK updates.
  CI runs npm audit and Expo's dependency compatibility check.

## Privacy and support URLs

The backend serves `/privacy` and `/support`, in English and Turkish (`?lang=tr`).
No separate website hosting is required. Once the API hostname is selected:

- Privacy policy: `https://<API_HOST>/privacy`
- Support page: `https://<API_HOST>/support`

These are also linked from Profile. Enter these same URLs in App Store Connect.
The pages interpolate operator/contact/provider details from backend settings.
The policy text must be checked against the actual providers and contracts before
release; configuration cannot verify a provider's retention or training practice.

Set `PRIVACY_OPERATOR` to the actual operator's legal identity and `SUPPORT_EMAIL`
to a monitored address. `AI_PROCESSING_DETAILS` must state the receiving provider(s),
processing regions, retention and training use. `AI_PRIVACY_URL` must be the real
provider policy. `INFRASTRUCTURE_DETAILS` must name hosting, storage and email
processors and regions. Write details understandable to both supported audiences.

`BACKUP_RETENTION_DAYS` describes the backup lifecycle you actually configure.
This patch does not create a backup service. Configure encrypted backups, expiry,
restore testing, restricted access and a deletion-replay process before launch.
Never publish the policy with an unimplemented retention promise. Account export
and privacy requests are handled through the support address.

## Production deployment

1. Copy `.env.production.example` to `.env.production` (git-ignored). Supply the
   API and storage hostnames, strong independent database/Redis/storage/JWT
   secrets, chosen AI provider/key/model, verified email sender and legal details.
   Blank values deliberately prevent startup. Do not put these secrets in Expo.
2. Point the two DNS names at the server. Open inbound 80/443 for Caddy. Run:

   ```sh
   docker compose --env-file .env.production -f compose.production.yml up -d --build
   ```

   This is a **standalone** Compose file, not an override of the development stack.
   Do not use the development override for production. Only Caddy publishes ports;
   PostgreSQL, Redis, MinIO admin and the AI service remain on the private network.
   MinIO's S3 API is served through HTTPS for short-lived signed photo links; the
   buckets remain private. The public signing endpoint is separate from the
   backend's internal storage endpoint.
3. `SPRING_PROFILES_ACTIVE=prod` refuses development mail, mock AI disclosures,
   weak secrets, HTTP public URLs and disabled quota enforcement. The AI container
   refuses mock providers and silent mock fallbacks in production. Choose and
   validate a real provider; a provider outage must not masquerade as successful
   AI analysis. Internal rule-based outfit fallbacks remain explicitly labelled.
4. The example proxy has static private address `172.29.83.2`. If the network range
   changes, update the trusted proxy regex too. Caddy overwrites `X-Forwarded-For`;
   never publish backend:8080 around the proxy. A different host platform needs
   equivalent private-network, TLS and trusted-proxy configuration.
5. Run migrations against a backup/staging copy before rollout. Verify provider
   calls, mail delivery, consent withdrawal, reset/logout, signed image links,
   account deletion and scheduled cleanup. Monitor queued deletions, storage
   errors, rate-limit store failures and AI spend. Choose explicit image digests
   after vulnerability scanning for repeatable production deployments.

Local Compose ports now bind to `127.0.0.1`. For physical-device development use
an intentional HTTPS tunnel or a private-LAN-only override, and set both
`EXPO_PUBLIC_API_BASE_URL` and `STORAGE_PUBLIC_ENDPOINT` to device-reachable URLs.
Do not open database, Redis or AI ports for mobile access.

## iOS / App Store

Set these values in the EAS **production environment**:

- `EXPO_PUBLIC_API_BASE_URL`: the public HTTPS API origin, with no `/api` suffix.
- `IOS_BUNDLE_IDENTIFIER`: your registered unique bundle identifier.
- `EAS_PROJECT_ID`: the UUID from your Expo project.

`mobile/app.config.js` rejects missing production configuration and local HTTP
addresses. `mobile/eas.json` defines a store build and remote build numbering.
After setting the Apple Developer credentials and creating the App Store Connect
record, from `mobile/` run:

```sh
eas build --platform ios --profile production
eas submit --platform ios --profile production
```

Select a build environment meeting Apple's current Xcode/iOS SDK requirements
(Xcode 26 and iOS 26 SDK or later as of this review). This patch does not produce
or sign an iOS binary. Test in TestFlight on physical iPhone/iPad: camera/library
permissions, image uploads, background/foreground, offline logout, switching
accounts, expired sessions and the AI consent dialogue. The app declares iPad
support, so include iPad layout testing.

Complete screenshots, support/privacy URLs, app privacy data disclosures, age
rating and export compliance in App Store Connect. Provide Apple with a working
review account and keep the backend online. Uploading a build is not publishing:
submit for App Review and release it after approval. GitHub is optional; CI checks
and image publication do not automatically deploy this server or publish iOS.

## Verification commands

```sh
(cd backend && ./gradlew test bootJar)
(cd ai-service && .venv/bin/python -m pytest -q)
(cd mobile && npm run typecheck && npm run check:i18n && npm run test:security)
```

The new regression tests cover image ownership/lifecycle/retry, reset attempt
limits under concurrency, session replay/revocation, forged forwarded headers,
AI consent, production guards and mobile account-transition races. CI also runs
Flyway/pgvector and MinIO smoke tests and the existing container security gates.

Verified locally on 2026-09-21: 572 backend tests, 134 AI tests, seven mobile
security tests, TypeScript and both locale dictionaries. An isolated real
PostgreSQL/MinIO stack verified cross-account image rejection, physical file
deletion after item/account deletion, and session logout/replay invalidation.
V12–V14 also migrated a seeded V11 database successfully. The temporary stack
was removed after testing.

Expo dependency compatibility, prebuild configuration and an iOS Hermes bundle
export also passed. The export is not a signed native build or a device test.

The final npm audit reported zero known vulnerabilities. Trivy's local database
(updated 2026-09-21) reported zero HIGH/CRITICAL findings in the Python/mobile
lockfiles and the backend's resolved 137-component Maven runtime SBOM. These
checks do not cover final container OS layers or constitute a penetration test;
the CI image scans and physical-device/TestFlight checks remain required.
