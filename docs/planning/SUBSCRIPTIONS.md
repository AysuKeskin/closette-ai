# Closette subscriptions: product, economics and integration plan

Research date: 2026-09-22. Status: proposal, not implemented or published.
Launch markets: Turkey and international together. USD examples below represent
the US storefront, not every international market. Existing working-tree changes
and `.env` files were not modified for this plan.

## Decision proposal

Launch Free + one Plus entitlement, with monthly billing only. Use Apple
In-App Purchase through RevenueCat on iOS; keep the domain model store-neutral
for a later Google Play release. Monetize useful AI allowances and larger
inventory capacity. Do not sell guaranteed speed or unlimited AI.

Start with these price hypotheses, subject to available App Store price points:

| Product | Turkey | US storefront |
|---|---:|---:|
| Plus monthly | TRY 149.99 | USD 5.99 |
| Launch: first month only | TRY 119.99 | USD 4.79 |

Launch pricing is approximately 20% below the regular monthly price. From the
second billing month, renewal is TRY 149.99 / USD 5.99 per month, disclosed before
purchase. Prices remain proposals and must match available store price points.
No annual product is planned. Never represent a never-charged historical price
as a previous price; describe the actual introductory and renewal prices.

Use a scheduled first-30-days launch offer for eligible new subscribers, with
one fixed end date shown in the user's local timezone. This is the eligibility
window: each eligible subscriber receives one discounted billing month, even
when that month ends after the campaign closes. No rolling countdown,
permanent founder price or lifetime AI deal. Start without a recurring free
trial: the Free plan demonstrates value without a payment commitment. A later
trial experiment must be separate from the introductory-price experiment.

## Market evidence

These are public listing snapshots, not in-app purchases we performed. Listings
can include legacy products; they do not prove current eligibility or exact
feature allocation. Validate the live purchase screens before copying a market
positioning assumption.

| Product | Observed public offering | Implication for Closette |
|---|---|---|
| Whering | Normal use remains free; optional contributions provide perks | Basic closet access alone is a weak subscription proposition |
| Stylebook | US USD 4.99 one-time purchase | Do not present basic inventory as inherently requiring an expensive subscription |
| Acloset | US Basic USD 3.99/mo, 27.99/yr; Premium USD 9.99/mo, 59.99/yr | AI wardrobe users already see subscription tiers and usage purchases |
| Acloset, Turkey | Basic TRY 64.99/mo; Premium TRY 129.99/mo, 1,299.99/yr | Closette's proposed monthly price is about 15% higher; beauty and shopping advice must demonstrate additional value |
| Indyx | US membership USD 12.99/mo or 74.99/yr | A higher-priced wardrobe membership exists, but this does not prove willingness to pay for Closette |

Sources: [Whering FAQ](https://whering.co.uk/faq/can-i-use-whering-if-i-dont-pay),
[Stylebook FAQ](https://stylebookapp.com/faq.html),
[Acloset US](https://apps.apple.com/us/app/acloset-ai-fashion-assistant/id1542311809),
[Acloset TR](https://apps.apple.com/tr/app/acloset-ai-fashion-assistant/id1542311809),
[Indyx US](https://apps.apple.com/us/app/indyx-wardrobe-outfit-app/id1599179405).

RevenueCat reports a 2.1% median day-35 download-to-paid conversion for freemium
apps versus 10.7% for hard paywalls. These are observational cross-category
benchmarks, not a causal experiment or a Closette forecast. In particular, they
are **not** the paid share of monthly active users used in our scenario tables.
Closette requires building a useful wardrobe first, so a limited free experience
is the initial hypothesis despite the higher hard-paywall benchmark.
[2026 report](https://www.revenuecat.com/state-of-subscription-apps)

## Plan limits and product behaviour

| Capability | Free | Plus |
|---|---|---|
| View/edit/delete existing inventory, saved looks | Always available | Always available |
| Combined wardrobe/beauty/wishlist inventory | 100 entries | 1,000 entries |
| AI allowance | Separate operation limits below | Separate operation limits below |
| Monthly rollover | None; welcome allowances expire after 30 days | None |
| Photo storage target | Up to 100 compressed item photos | Up to 1,000 compressed item photos |
| Priority/speed guarantee | None | None in v1 |

### User-facing allowances: actions, not credits

Do not display AI credits, token counts or weighted balances in the product.
Use independent, exact operation allowances. All listed allowances can be used
in the same billing month; using photos does not reduce outfit entitlement.
These quantities are proposals pending cost/quality validation, not live offers.
Do not advertise approximate quantities as if they were guaranteed limits.

| Operation | Free recurring/month | Free one-time welcome bonus | Plus/month |
|---|---:|---:|---:|
| Identify one garment or beauty photo (or parse one clothing description) | 5 | 20 | 60 |
| Generate one outfit | 1 | 2 | 40 |
| Assess a potential purchase, including candidate analysis and advice | 1 | 1 | 10 |
| Read one ingredients-label photo (OCR) | 0 | 1 | 10 |
| Generate one uncached ingredient explanation | 0 | 1 | 10 |

Manual entry, viewing existing photos/results and cached ingredient explanations
use no AI allowance. Photo storage remains bounded by inventory and byte caps.
A new AI analysis of the same photo consumes a photo-analysis use; simply saving
or reopening it does not. A completed shopping assessment consumes only its own
allowance, not another photo-analysis allowance. Every regeneration is a new
outfit use; opening a saved outfit is not. Show these rules before the action.

Internal cost weights remain 1 for identification/text parsing, 2 for outfits,
3 for shopping, 2 for OCR and 1 for uncached explanation. Full Plus consumption
is 60*1 + 40*2 + 10*3 + 10*2 + 10*1 = 200 internal cost units. Free recurring
limits total 10 units; the welcome bonus totals 30. Thus the financial model's
full-use ceilings are unchanged. Its variables named `credits` are internal
budget units only, not a user-facing currency. There is no shared product balance
that can prevent someone using another advertised allowance.

Require verified email before granting welcome allowances or incurring paid AI
costs. Welcome grants are single-use; account deletion/recreation must not become
an unlimited trial. Define a minimal, time-bounded anti-abuse retention policy
before retaining identifiers; do not covertly fingerprint devices.

Plus replaces Free recurring allowances. Unexpired welcome allowances remain
separately identifiable and cannot be granted again on purchase. Upgrading must
not duplicate recurring grants: reconcile already-used counts against the new
per-operation entitlement. Existing content stays accessible after expiry;
block only new over-limit additions and paid AI operations. Deletion, privacy
controls and purchase restoration never require payment.

Do not make silent billable explanation calls for every ingredient after OCR.
Use per-user/content/model/language caching where appropriate; never reuse
personal results across users. Safety limits still apply to cached endpoints.
A failed operation restores its own allowance; internally billed provider cost
still appears in the cost ledger.

Display remaining counts per operation, e.g. "Bu ay 18 kombin oluşturma hakkın
kaldı", and the actual reset date. Grant each Plus allowance once per verified
monthly billing period, including the discounted first month. Use store period
boundaries, handling month-end dates explicitly. Free grants use UTC calendar
months; welcome expiry is independent. Reinstalling, restoring or replaying a
webhook must not reset counts. Failed renewal follows the grace policy rather
than creating an unverified new grant.

## AI cost basis and limits of the estimate

The provider is not selected. Current `OpenAICompatibleVLM._chat` discards
provider usage metadata and does not set an output-token limit. Consequently,
there is no measured production cost per successful action yet. Existing
`@RateLimit(cost=...)` values are safety weights, not money or paid allowances.

As one priced candidate, Gemini 3.1 Flash-Lite Standard lists USD 0.25 per million
input text/image tokens and USD 1.50 per million output tokens, including
thinking. This is a costing reference, not a quality endorsement or an existing
provider integration. Gemini is not an explicit provider in the current
configuration: implement and test its adapter, token accounting, output cap,
image semantics and consent disclosure before selecting it. No search grounding,
image generation, external background-removal fees or GPU hosting is included.
[Google pricing](https://ai.google.dev/gemini-api/docs/pricing)

Illustrative token profiles, not measurements:

| Request | Input tokens including image | Output including thinking | Raw USD | With 50% attempt/variance allowance |
|---|---:|---:|---:|---:|
| Photo identification | 1,500 | 600 | 0.001275 | 0.0019125 |
| Outfit generation | 6,000 | 1,200 | 0.003300 | 0.0049500 |

Budget **USD 0.003 per credit** for the initial model, with a **USD 0.010 stress
case**. These are assumptions. A 512px upload does not imply the same token cost
across models. Cap context and output; record image/text/output/thinking tokens,
all provider attempts, model version and priced cost per operation. Unsuccessful
requests may still incur provider cost even when user credits are returned.

Before selling Plus, benchmark at least 200 representative Turkish/English
tasks across the four flows, including difficult labels and long closets. Check
quality, p50/p95 latency, and cost per credit including failures. Allocate about
USD 10 as a capped evaluation budget, not as a forecast of the actual bill.
No live provider calls are authorized or performed by this document.

## Revenue and expense assumptions

Apple Small Business approval is assumed in the base case: 15% commission. It is
not automatic. Also model 30% while approval is absent; standard subscriptions
have different proceeds after the first paid year.
[Small Business](https://developer.apple.com/app-store/small-business-program/),
[subscription proceeds](https://developer.apple.com/app-store/subscriptions/).

RevenueCat currently charges nothing up to USD 2,500 monthly tracked revenue,
then 1% of tracked revenue. The model conservatively reserves 1% of gross from
day one even when no fee is due. Reconcile the reserve with actual tracked revenue and
the vendor invoice. [RevenueCat pricing](https://www.revenuecat.com/pricing/)

Explicit planning inputs:

- TRY/USD = 50; this is a round scenario assumption, **not today's exchange rate**.
  Sensitivity cases use 40 and 60. Provider charges and store payout FX differ.
- TR indirect-tax allowance = 20% included in displayed price, for budgeting
  only; replace with actual App Store Connect proceeds and accountant-confirmed
  tax treatment. US list-price scenario excludes separately collected sales tax.
  Other global storefronts require their own prices, taxes and payout factors.
- Refund allowance = 3% of after-store proceeds; not an observed refund rate.
- All subscribers pay monthly. No intro offer in the normal-price table.
- Base usage: Free 10 credits/month, Plus 100 credits/month.
- Non-AI variable reserve: USD 0.02/free active user and 0.10/paid active user/month
  for storage, egress and transactional services. These are placeholders to
  measure, not quotes. Compress stored images (not only the model input), cap
  upload bytes/counts, and include dormant users' retained storage separately.
- Thus base variable expense = USD 0.05/free and USD 0.40/paid active user/month.
- Fixed operating budget = USD 50/month at 1,000 MAU: roughly USD 25 hosting
  contingency, USD 10 backups/email/monitoring contingency, USD 8.25 Apple
  membership amortization and USD 6.75 domain/other reserve. These are allocations,
  not provider price promises. Apple membership is USD 99/year or local price.
  At 5,000 MAU use USD 100 as an illustrative fixed budget, not a capacity claim.
  [Apple membership](https://developer.apple.com/programs/whats-included/)
- Excluded: founder salary, development, customer-support labour, acquisition,
  accounting/company expenses, income/corporate taxes, exceptional refunds,
  provider-bill taxes/FX fees not already covered by reserves and new-user bonus.
  Results are operating contributions, **not take-home profit**.

At 0.5 MB per stored photo, 1,000 free closets with 100 photos each occupy about
50 GB before backups/thumbnails/versions. A free server is not infinite storage.
Account count and retained bytes matter even when MAU is low.

Formula in each local currency:

```text
net(P) = P / (1 + indirect_tax) * (1 - store_fee) * (1 - refund_rate)
         - 0.01 * P
normal_net_per_payer = net(monthly)
operating_contribution = paid_users * normal_net_per_payer_USD
                        - paid_users * 0.40 - free_users * 0.05 - fixed_USD
```

| Region | Net/normal month | Net/introductory first month |
|---|---:|---:|
| Turkey | TRY 101.56 | TRY 81.24 |
| US | USD 4.88 | USD 3.90 |

Normal receipts per payer/month: Turkey USD 2.0311 at assumed FX;
US USD 4.8789. For the table below assume **50% of paying users in each region**.
The actual country mix can materially change the answer.

| MAU | Paid share of MAU | Payers | Net receipts USD/mo | Variable + fixed USD/mo | Contribution USD/mo |
|---:|---:|---:|---:|---:|---:|
| 1,000 | 2% | 20 | 69.10 | 107.00 | -37.90 |
| 1,000 | 5% | 50 | 172.75 | 117.50 | +55.25 |
| 1,000 | 10% | 100 | 345.50 | 135.00 | +210.50 |
| 5,000 | 5% | 250 | 863.75 | 437.50 | +426.25 |

These paid shares are scenarios, not conversion predictions. Keeping 1,000 MAU
constant, normal-price break-even is approximately 60 Turkish payers, 23 US
payers or 33 payers with a 50/50 mix. If every payer is in their discounted first
month, the figures become 79 Turkish, 29 US or 42 mixed payers. At 50 mixed payers,
that introductory-month contribution is USD 20.66 instead of USD 55.25.
Fixed costs and free users are included; exclusions above still apply. Additional
welcome grants cost up to USD 0.09 per new user under the base credit cost:
500 new users add USD 45, separate from monthly allowances.

Sensitivity at 1,000 MAU / 5% paid / 50:50 market mix:

| Change | Contribution USD/mo |
|---|---:|
| Normal-price base | +55.25 |
| Store fee 30% instead of 15% | +24.37 |
| Every paid user spends all 200 credits at base cost | +40.25 |
| Credit cost USD 0.01; paid uses 200, free uses 10 | -96.25 |
| 500 new welcome grants, normal-price base | +10.25 |
| Everyone in discounted first month | +20.66 |
| Everyone in discounted first month, store fee 30% | -4.04 |

At TRY/USD 40, 50 and 60, Turkey-only normal-price break-even is respectively
about 46, 60 and 75 payers among 1,000 MAU. Reprice future offers if FX or provider
costs erode margin, while honouring active subscription terms. Do not silently
reduce the allowance already sold. At USD 0.01/credit and full usage, Turkish
introductory subscribers lose money before overhead. Change provider/limits/
pricing before launch if measured costs resemble that scenario. A paywall does
not fix negative unit margin.

## Launch discount decision and cash discipline

Offer the first monthly period at TRY 119.99 / USD 4.79 as a bounded experiment.
Subsequent periods renew at TRY 149.99 / USD 5.99 per month until cancelled,
subject to store rules for later changes. Use a one-month introductory offer
on the monthly product; do not create a second permanently cheap subscription.
Apple permits one introductory offer per subscription group; use store-provided
eligibility, not a local flag.
[Apple introductory offers](https://developer.apple.com/help/app-store-connect/manage-subscriptions/set-up-introductory-offers-for-auto-renewable-subscriptions)

At base usage, a Turkish introductory payment leaves about TRY 81.24 after
modelled deductions, before service expense. Reserve TRY 20 for expected monthly
variable service expense (USD 0.40 at assumed FX), leaving TRY 61.24 to fund free
users, overhead and acquisition. At full 200-credit usage reserve TRY 35 instead,
leaving TRY 46.24. Welcome grants are a separate cost. App Store cash arrives on
its payout schedule, not instantly when a purchase appears in RevenueCat.

At a 50/50 market mix, normal monthly contribution per subscriber before fixed
costs/free-user subsidy is about USD 3.05; introductory contribution is USD 2.36.
The offer needs about **29% more first-month purchases** to preserve immediate
contribution at equal usage (33% more in Turkey, 28% more in US). This is not a
lifetime-value threshold: second- and third-month retention may justify a lower
initial contribution. Measure renewals at full price rather than assuming all
introductory subscribers stay. An all-discounted cohort does not automatically
become a same-sized full-price cohort next month.

Start acquisition organically. At the mixed normal-price base 5% paid share,
each payer's contribution after subsidising 19 free users is roughly USD 2.10
per month before fixed expenses. Three retained full-price months supply about
USD 6.31; one introductory month followed by two retained full-price months
supply about USD 5.62. Neither is a safe CAC target without measured retention.
Keep at least three months of operating runway: at 1,000 base-case MAU roughly
USD 300–400 covers three months of illustrated operations, before company costs
and growth. Monthly billing avoids a year-long discounted commitment, but
provides less upfront cash and requires earning the renewal each month.

## Implementation work, in dependency order

### 1. Metering and cost controls before payment

- Extend the AI service boundary to return internal usage metadata alongside
  results. Carry an operation ID through Spring -> FastAPI -> provider; count
  attempts and actual cost even if the user request fails. Never expose raw
  billing credentials or user photos in telemetry.
- Set per-flow context/output limits, bounded concurrency and timeouts. An
  HTTP timeout does not prove the provider stopped charging; do not blindly
  retry. Validate structured results before settling a user charge.
- Separate Redis burst/IP safety limits from PostgreSQL product quotas. Existing
  shared-IP limits must not inadvertently erase paid rights for users on the
  same mobile network. Fail closed on costly work when enforcement is unavailable.
- Introduce global daily/monthly spend admission controls with atomic worst-case
  cost reservations for in-flight work. Alert at 50/75/90%; stop free AI before
  exhausting capacity needed for sold allowances. Do not silently cut off paid
  entitlements to make a budget chart green: provision, pause new sales or remedy
  service failure. Provider dashboards alone are not a reliable hard cap.
- Account for manual uploads, abandoned uploads, image storage and account-creation
  abuse independently of paid AI usage. Mock embedding/local ML compute is not
  production semantic quality evidence or free external inference.

### 2. Backend billing and quota domain

Create feature-first `ai.closette.billing` and `ai.closette.usage` packages.
Allocate the next unused Flyway migration at implementation time; V16 is already
present in the working tree, so do not hardcode an earlier migration number.

Proposed durable records:

- `billing_customers`: user UUID -> unique opaque RevenueCat customer ID.
- `subscription_snapshots`: entitlement/product/store/environment, transaction
  ownership, effective dates, renewal/grace/revocation state and sync timestamps.
- `billing_events`: unique provider event ID, processed status, bounded retry,
  minimal redacted payload and environment; durable inbox for reconciliation.
- `usage_grants`: allowance type, amount, UTC start/end, plan version and unique
  grant source. Each verified monthly period is granted once only.
- `usage_operations`: user, operation ID, payload fingerprint, reserved/settled/
  released units, result reference and lease status. Unique idempotency keys
  scoped to user; changing payload under the same key is rejected.
- `provider_cost_events`: operation/attempt/model usage and immutable USD cost
  snapshot; restricted retention separate from marketing analytics.

On an AI action: authenticate -> consent -> safety checks -> atomic per-operation quota and
global-cost reservation -> provider -> persist validated result -> settle user
operation allowance. Failed/no-result operations release that allowance; billed provider cost
remains recorded. Do not hold a SQL transaction open for a network call. Recovery
jobs resolve abandoned reservations; an expired lease must not immediately
permit a duplicate provider call while the first may still be running. A lost
mobile response can retrieve the stored result without paying twice. Cached
responses bypass product debit, but not ownership/abuse checks.

Proposed API:

- `GET /api/billing/me`: authoritative plan, expiry, allowance, reset date and
  whether subscription management is available. Return remaining and total counts
  per operation, not an AI credit balance.
- `POST /api/billing/sync`: authenticated, rate-limited server lookup after
  purchase/restore; ignore client claims of active status or price.
- `POST /api/billing/webhooks/revenuecat`: dedicated secret-authenticated inbox,
  strict size/schema validation, environment separation and event deduplication.
- Existing AI routes accept an idempotency key; expose distinct machine codes
  `AI_QUOTA_EXHAUSTED`, `AI_BUSY`, `AI_UNAVAILABLE` and `BILLING_SYNC_PENDING`.

Authenticate webhook delivery with a configured high-entropy Authorization
secret. Persist before acknowledging; process asynchronously and reconcile with
RevenueCat's server API so delayed/out-of-order events cannot re-enable an
expired/refunded entitlement. Periodic reconciliation and purchase-triggered
sync cover lost webhooks. New unverified purchases stay pending; known active
entitlements may be honoured only within their recorded expiry/grace policy.
[RevenueCat webhooks](https://www.revenuecat.com/docs/integrations/webhooks)

### 3. Store and RevenueCat configuration

Create one Apple subscription group and one RevenueCat entitlement `plus`.
Product: `closette.plus.monthly`, with a one-month introductory offer.
Do not create an annual product or annual paywall option. Country prices are storefront configuration, not new
entitlements or language-dependent plans. USD is not the price of every country.
Create default and launch offerings referencing actual eligible store products.

Complete paid-app agreement, banking/tax setup, Small Business application,
subscription metadata, introductory dates and sandbox accounts. Choose billing
grace handling deliberately (proposal: store-supported short grace, continue
existing allowance until grace expires without minting duplicate grants).
Cancellation disables renewal but preserves access through paid expiry; refund
or revocation removes future paid usage without deleting wardrobe data.

Use an authenticated, non-email, non-guessable user ID for RevenueCat. Require
Closette login before purchase. Configure restore ownership explicitly and
prevent one Apple purchase from funding multiple Closette accounts. Prefer
keeping purchase ownership with the original account; provide an account-recovery
path and a controlled restore/transfer policy after account deletion. Do not
reinstate deleted wardrobe data when restoring a subscription.
[Customer identity](https://www.revenuecat.com/docs/customers/identifying-customers),
[restore policy](https://www.revenuecat.com/docs/projects/restore-behavior).

### 4. Mobile purchase experience

Add `react-native-purchases`; optionally use RevenueCat's paywall UI if it meets
our design/i18n needs. Build a development client: Expo Go can preview mock
purchase flows but does not test real transactions.
[Expo integration](https://www.revenuecat.com/docs/getting-started/installation/expo)

Integrate with existing auth epochs: clear billing query caches on logout,
switch RevenueCat identity with the session, and reject late purchase/sync
callbacks from the previous account. Backend state gates paid AI even when SDK
UI state is stale. Verify a pending purchase after returning to foreground.

Add a plan screen, allowance display, dismissible paywall, Restore Purchases and
Manage Subscription. Obtain localized price/currency/period and introductory
eligibility from the store SDK, not hardcoded Turkish strings. Handle cancelled,
pending, failed, restored and completed purchases distinctly. Support VoiceOver,
large text and both languages. Show the first-month charge and subsequent
monthly renewal price prominently. Include renewal terms, privacy and Terms of Use links.

Show the paywall after demonstrated value or a specific quota limit. Do not
intercept ordinary inventory access. For quota exhaustion use e.g. "Bu ayki kombin
oluşturma hakkını kullandın. Plus ile ayda 40 kombin oluştur." Offer "Planları gör" and
"Şimdi değil" and the actual reset date. An unavailable AI provider shows a
retry/manual-entry message without an upgrade demand.

A real overload message may say "AI işlemleri şu an yoğun. Biraz sonra tekrar
dene." v1 has no priority queue; therefore it must not promise "subscribe for
instant access." A later priority feature needs separate queues, worker capacity,
fair scheduling, bounded waits and measured latency before it is marketed.

Account deletion must remain available for subscribers, with clear explanation
that store renewal is managed separately and a direct management link. Do not
claim deleting the account cancels Apple's subscription automatically. Keep only
legally/operationally justified billing records with disclosed retention.
[Apple review guidelines](https://developer.apple.com/app-store/review/guidelines/)

### 5. Secrets, privacy and operational setup

Mobile public config: platform-specific RevenueCat public SDK key. Backend secret
config: least-privilege RevenueCat server API credential and webhook authorization
secret. Apple signing / App Store integration keys belong in EAS/RevenueCat or
protected CI secrets where required, never the mobile bundle. Product IDs and
entitlement names are configuration, not secrets. Keep sandbox and production
keys/events distinct and prevent sandbox receipts granting production usage.

Update privacy/support pages, Terms of Use, App Store privacy disclosures and
AI consent where the selected provider changes. RevenueCat adds subscription
identifiers and purchase events; avoid sending wardrobe content to billing or
analytics. Extend production configuration guards to billing before enabling
sales. With billing disabled, remain explicitly Free; no fake purchase button.

### 6. Tests and controlled rollout

Automate subscription expiry, cancelled-but-active state, grace, recovery,
refund, out-of-order/duplicate/lost webhook, restore ownership, logout races,
forged client entitlement, sandbox isolation and unavailable billing provider.
Verify concurrent last-use spending per operation on real PostgreSQL, independent
allowances (exhausted photo allowance must not block outfits), idempotent retries,
month boundaries/leap days, introductory-to-full-price renewal and cost ledger
reconciliation. Verify campaign cutoff and subscriber-specific offer eligibility.
CI uses mocked billing/provider adapters and never spends real money.

Use StoreKit sandbox/TestFlight on real devices for purchase, renewal, decline,
pending approval, eligible/ineligible intro, restore, refund and account switching.
Verify one genuine store purchase -> server entitlement -> usage -> renewal/
expiry path, not merely a paywall screenshot. Test TR/US and a VAT-inclusive
international storefront; language must not determine currency.

Roll out behind flags: cost metering -> quotas in shadow mode -> enforced free
quotas -> sandbox billing -> invited production cohort -> general sales. No
payments until all sold benefits work. Review daily cost and failure metrics;
keep a kill switch for new sales independently of existing subscriber rights.

## Launch experiment and decision gates

Log only necessary events: activation (e.g. five items and one useful outfit),
paywall view/source, offer eligibility, purchase start/completion, restore,
refund, allowance used and provider-cost aggregates. Use store transactions as
payment truth. Do not log garment photos or raw prompts in analytics.

Compare normal monthly versus first-month introductory cohorts within each region,
with stable user assignment and no artificial time pressure. Measure net
contribution per eligible activated user at day 30/60/90, not just purchases.
Report sample sizes/uncertainty; a handful of sales cannot establish a winner.
Measure second- and third-month full-price renewals and cancellations after the
introductory month. A 30-day campaign cutoff does not cut short an active offer.

Before launch, require: measured viable unit cost at full promised usage,
positive region-specific contribution at intro prices, tested billing lifecycle,
working backups/support, capacity for subscribers and explicit spend caps.
At the 50/50 base mix the launch discount needs ~29% more first-month buyers for
immediate contribution parity; retained later renewals must also be evaluated.
If the offer reduces 60/90-day contribution per eligible user, stop it for new customers;
honour existing purchases. If TR demand is weak, test a lower monthly price
separately rather than simultaneously changing quota, renewal price and promotion.

Indicative work estimate for one developer familiar with the repo: 1–2 days for
metering/evaluation, 2–4 for backend quota/billing, 2–3 for mobile/store setup,
2–3 for lifecycle tests and hardening: roughly 7–12 engineering days. This is an
estimate, excludes Apple approval/account setup delays, and may expand for a new
AI provider or production infrastructure. No subscription code was added here.

## Reproduce and change the financial assumptions

The companion script uses only Python's standard library and does not read env
files, call providers or modify the application:

```sh
python3 docs/planning/subscription_economics.py
python3 docs/planning/subscription_economics.py --commission .30
python3 docs/planning/subscription_economics.py --fx 60
python3 docs/planning/subscription_economics.py --credit-cost .01 --paid-credits 200
python3 docs/planning/subscription_economics.py --new-users 500
```

The base and stress outputs were executed and checked against the tables above.
