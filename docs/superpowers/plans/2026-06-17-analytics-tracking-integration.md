# Analytics & Tracking Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire a consent-gated dataLayer event layer (13 events for Phase 1), UTM/attribution capture + viral-loop tracking, and a backend `trip_id`/UTM persistence path, then configure GA4 + Meta Pixel + Microsoft Clarity entirely through the existing GTM container — so marketing can measure the organizer funnel and tie campaigns to bookings.

**Architecture:** The React SPA pushes named events to `window.dataLayer` via a single **stateless** helper; the existing GTM container (`GTM-KB7BJLDS`) routes them to GA4, Meta Pixel and Clarity, each gated by CookieYes Consent Mode v2. A single `trip_id` (vote `shareToken`, or a client-minted UUID for the direct-book funnel) threads the funnel; attribution params are captured once and persisted, then attached to the booking (dataLayer event + backend record + notification email). No tracking-vendor snippet is added to the repo — all vendor code loads via GTM (same rule as CookieYes).

**Tech Stack:** React 19 (CRA), React Router v7, Spring Boot 4 / Java 25, GTM (`GTM-KB7BJLDS`), GA4, Meta Pixel `1482052533162342`, Microsoft Clarity `x5bd9qsdhk`, CookieYes CMP (key `29814bffa362e67fefae8c3d10e1dfcf`).

---

## Post-review revisions (v2 — applied after the multi-agent plan review)

The plan was reviewed by 5 independent reviewers (codebase-accuracy, frontend-architecture, GTM/consent/privacy, trip_id/attribution data-flow, spec-coverage). Accepted changes folded into this v2:

1. **`analytics.js` is stateless** — the module-level `tripCtx` singleton was removed. It would have leaked `trip_id`/`user_role` across role switches in one tab (organizer → participant). `trip_id`/`user_role` are now passed **explicitly** by each call site from the source of truth (`TripContext`, the route `shareToken`, or `resolveUserRole()`). (Task A2/A5.)
2. **`<AttributionCapture/>` effect deps pinned** to `location.search` with idempotent capture, so `?ref=invite` is picked up on SPA navigation. (Task A4.)
3. **`trip_id` lifecycle specified** — minted at `tb_group_submitted` (direct path), stored in `localStorage['myhive-trip-id']`, **not** cleared by `CANCEL_TRIP_SETUP`; for the vote funnel `trip_id = shareToken`; **vote-then-book threads `?voteSession=` into the booking** as an explicit step. (Task A5/A19/B1.)
4. **Event fire-sites made precise & idempotent** — fire on API success, once-guarded (`useRef`/`useEffect([isOpen])`), never on re-render. (Event map + A6–A19.)
5. **Email Advanced Matching is consent-gated** — raw email is pushed to `dataLayer` **only** when `ad_storage` consent is granted, via a new `consent.js` reader. (Task A2b/A9.)
6. **Clarity custom tags buffered** against the async SDK-load race; GTM tag firing order + CookieYes category→Consent-Mode mapping documented. (Workstream C.)
7. **GA4 `generate_lead` contradiction resolved** — the booking event is sent to GA4 as `generate_lead` (a GA4 recommended event, marked key event); all other events keep their dataLayer names. (Workstream C / event map.)
8. **`event_id` stays on every event** (spec §5 mandate, CAPI dedup seed) — confirmed, with the note that the `eventID` only changes Meta behaviour on server-deduped events (`Lead`, future `Purchase`).
9. Added an **integration test** for UTM survival across the multi-step SPA flow into the DB/email. (Workstream D.)

Non-issues raised by reviewers and intentionally **not** changed: the `pushEvent` filter does **not** drop `false`/`0` (strict `!==`, no coercion); React Router `<Link>` **does** accept `onClick`; mobile deep-link `ref` restoration is N/A (web SPA, no native app).

---

## Critical findings from codebase recon (read before planning tasks)

1. **GTM already exists** — `GTM-KB7BJLDS` is installed in `myhive-react-app/public/index.html` (loader in `<head>`, noscript after `<body>`), `dataLayer` auto-initialised. "Create a GTM container" = **"add tags to the existing container"**.

2. **No payment system exists — anywhere.** Frontend: zero payment UI. Backend: only a `stripeSessionId` column + `PATCH /bookings/{id}/status`; no payment controller/webhook/splitting/participant-payment. So **`payment_page_viewed`, `payment_completed`, `trip_fully_paid`, `Purchase`, §8.1, CAPI/Measurement-Protocol → Phase 2** (blocked on the payment feature). Launch is unaffected: Meta optimises on **`Lead` (`booking_submitted`)**.

3. **No `trip_id`/reference exists.** `Booking` PK is a UUID only; `VoteSession` has server-generated `shareToken` + `managerToken`; participant link is `/vote/{shareToken}/activities`. There is **no server round-trip at `tb_group_submitted`** (client-only in `TripSetupModal`); the first server write is `POST /vote/sessions` or `POST /bookings/trip`. See **`trip_id` strategy**.

Confirmed reusable facts:
- UUID generator exists (private) in `src/utils/voterToken.js` `generateUuid()` (native Web Crypto, RFC-4122 v4) — Task A1 extracts it to `uuid.js` for reuse as `event_id`.
- Value source: `src/utils/tripPricing.js` `computeTripTotal(tripItems, travelers)` (EUR). Trip state: `src/context/TripContext.js` (`tripItems`, `tripTravelers`, `tripStartDate`, `tripEndDate`, `tripBudget`; localStorage `myhive-trip-items`, `myhive-trip-setup`).
- Role signal in localStorage: `myhive-initiator-{shareToken}`, `myhive-manager-{shareToken}` (organizer), `myhive-voted-{shareToken}` (voted).
- CookieYes consent: gating is in GTM; for **PII gating** (email) the SPA reads consent via a new `consent.js` reader (revision #5).
- Public app mounts under `/*` via `src/context/AppProviders.js` → `Layout`; `/admin/*` is a separate lazy `AdminApp`. **Analytics runs on the public tree only.**

### `trip_id` strategy (decision)
- **Vote funnel:** `trip_id = shareToken` (server-generated, already in the link, extractable by participants). Pre-session organizer events (`tb_start`, `tb_group_submitted`, `quiz_completed`, `shortlist_completed`) carry **no `trip_id`** (spec §8: required only where the trip already exists). From `vote_launched` onward and on every participant event, `trip_id = shareToken`. **Known limitation:** if the organizer abandons mid-funnel and returns in a new browser session, the pre-session events are not retroactively linked — acceptable per §8.
- **Direct-book funnel (no vote):** mint a client `trip_id` (`generateUuid()`) at `tb_group_submitted`, store in `TripContext` + `localStorage['myhive-trip-id']`, thread through `vote_skipped` → `booking_form_viewed` → `booking_submitted`, and send to `POST /bookings/trip`. `CANCEL_TRIP_SETUP` does **not** clear it; a fresh setup mints a new one.
- **Vote-then-book:** `VoteResultPage` → Trip Builder navigates with `?voteSession={shareToken}`; `TripBuilder` reads it (`useSearchParams`) and **must pass it as `tripId` into the booking** (explicit step in A19) so the booking row links to the vote funnel.
- **Backend** persists `trip_id` on `Booking`, generating `TRV-<8 hex upper>` if the client omitted it, so every booking has a stable id for the CRM `utm → trip_id → money` chain.

---

## Workstream decomposition

- **A. Frontend event layer + wiring** (repo code, TDD) — foundation.
- **B. Backend `trip_id` + attribution persistence + email/CRM** (repo code, TDD).
- **C. GTM / GA4 / Meta / Clarity configuration** (browser/dashboard, no repo code).
- **D. Testing & acceptance** (spec §10).
- **Phase 2 (deferred/blocked):** payment feature, payment events, Meta CAPI, GA4 Measurement Protocol, campaign→revenue report, remarketing audience.

---

## File Structure (Workstreams A & B)

**Create (frontend):**
- `src/utils/uuid.js` — extracted `generateUuid()`.
- `src/utils/analytics.js` — **stateless** `pushEvent(event, params)`; the only place that touches `window.dataLayer`.
- `src/utils/userRole.js` — `resolveUserRole(shareToken)` from the localStorage role flags.
- `src/utils/consent.js` — `hasConsent(signal)` reading CookieYes consent (for PII gating).
- `src/utils/attribution.js` — capture/read/persist UTM + click ids + `ref`, 90-day TTL, last-non-direct-click.
- `src/components/AttributionCapture.js` — mount-once + SPA-nav effect calling `attribution.captureFromUrl()`.
- Tests beside each.

**Modify (frontend):** `voterToken.js` (import `generateUuid`); `AppProviders.js` (mount `<AttributionCapture/>`); `TripContext.js` (`tripId` state + `SET_TRIP_ID` + `myhive-trip-id` persistence, not cleared on cancel); the event-trigger components; `TripBuilder.js` & `ContactForm.js` (booking events + attach attribution/trip_id, thread `voteSession`); `api.js` (`createBookingFromTrip` sends `tripId` + attribution + `ref`).

**Modify (backend):** `Booking.java` (+`tripId` + attribution columns), `BookingDTO.java` (+`tripId`), `TripExportRequest.java` (+`tripId`, +attribution), `BookingService.java` (generate/persist `tripId`, email context), `EmailService.java` + `templates/email/itinerary-confirmation.html` + booking-notification template (render `trip_id` + UTM), `templates/email/vote-created.html` (`?ref=invite`). Tests under `src/test/.../service` and `controller`.

---

## Definitive event map (reconciled, with precise fire-sites)

`pushEvent` is stateless: each call passes `trip_id`/`user_role` explicitly (from `TripContext`, the route `shareToken`, or `resolveUserRole`). `event_id` is auto-attached to every event. Names are exact snake_case from spec §8. **Fire-rule:** fire on API success where applicable, exactly once (guard with `useRef`/`useEffect([isOpen])`), never on re-render.

| # | Event | Precise fire-site | trip_id | Extra params | Meta | GA4 |
|---|-------|-------------------|---------|--------------|------|-----|
| 1 | `cta_click` | `onClick` on each CTA: `HomePage.js:44` (hero), `HowItWorksSection.js:27`, `ReviewsSection.js:51`; **add `onClick` to the `<Link>`** in `FeaturedActivitiesSection.js:55` | – | `cta_label`, `block` | custom `CTAClick` | `cta_click` |
| 2 | `contact_click` | `onClick` on the WhatsApp/Messenger `<a>` in `HowBookingWorksSection.js:27/30` | – | `channel` | `Contact` | `contact_click` |
| 3 | `tb_start` | `useEffect` on modal-open (`TripSetupModal.js`, fire once when `isOpen`→true, vote mode) | – | `ref` (`getRef()`) | `ViewContent` | `tb_start` |
| 4 | `tb_group_submitted` | end of `handleConfirm` after validation (`TripSetupModal.js:61–71`); mint `trip_id` (direct path) | mint | `destination`, `group_size`, `has_budget`, `email`*(consent-gated)* | `CompleteRegistration` + AM(email) | `tb_group_submitted` *(key)* |
| 5 | `quiz_completed` | only on **last** question: organizer at navigate-to-curate (`QuizPage.js:73–74`); participant after `submitParticipantQuiz` success (`:79`) | participant: shareToken | `q_daytime`, `q_adrenaline`, `q_food`, `q_classy` | – | `quiz_completed` |
| 6 | `shortlist_completed` | finalize screen reached, once (`CuratePage.js:178`) | – | `selected_count` | – | `shortlist_completed` |
| 7 | `vote_launched` | **after** `createSession` resolves, before navigate (`CuratePage.js` ~`:151`, not `:158`) | **shareToken** | `selected_count` | – | `vote_launched` |
| 8 | `vote_skipped` | `handleBuildMyTrip` (`CuratePage.js:98`, real label "Build my own trip") | client trip_id | `selected_count` | – | `vote_skipped` |
| 9 | `vote_opened` | `useEffect` on mount, `useRef` once-guard (`ActivityVotePage.js:24–33`) | **shareToken** | `user_role=participant` | – | `vote_opened` |
| 10 | `vote_completed` | **after** `castVotes` success, after `VOTED_KEY` set (`ActivityVotePage.js:60–64`) | **shareToken** | `user_role=participant` | – | `vote_completed` |
| 11 | `checkout_viewed` | inside the `.then` after `setData`, once (`VoteResultPage.js:53`, not `:50`); skip on error | **shareToken** | `items_count`, `value`, `currency=EUR` | `InitiateCheckout` | `checkout_viewed` |
| 12 | `booking_form_viewed` | `useEffect([isOpen])` in `ContactForm.js` (fires when form opens; opened by `TripBuilder.js:138`) | if present | `value`, `currency` | – | `booking_form_viewed` |
| 13 | `booking_submitted` | **after** `createBookingFromTrip` success (`TripBuilder.js:182`); dedup guard `sessionStorage['myhive-booked-{tripId}']` set after success | **yes** | `value`, `currency=EUR`, `activities_count`, `destination`, `group_size`, `utm_*`, `ref` | **`Lead`** + value/currency | **`generate_lead`** *(key)* |
| 14–16 | `payment_*`, `trip_fully_paid` | **❌ no payment system** | — | — | — | **Phase 2** |

> Naming reconciliations: modal button reads **"Continue to Categories"**; skip button reads **"Build my own trip"** → goes to Trip Builder. `WHATSAPP_URL` is a placeholder (`wa.me/0000000000`) — flag the real number to marketing.

---

## Workstream A — Frontend event layer + wiring (TDD)

### Task A1: Extract shared `generateUuid` into `uuid.js`
**Files:** Create `src/utils/uuid.js`; Modify `src/utils/voterToken.js`; Test `src/utils/uuid.test.js`
- [ ] **Failing test:** `generateUuid()` matches the v4 regex and differs across calls.
- [ ] **Run → FAIL.**
- [ ] **Implement** `uuid.js` (move the generator body verbatim from `voterToken.js`):
```js
export function generateUuid() {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'));
  return (
    hex.slice(0, 4).join('') + '-' + hex.slice(4, 6).join('') + '-' +
    hex.slice(6, 8).join('') + '-' + hex.slice(8, 10).join('') + '-' +
    hex.slice(10, 16).join('')
  );
}
```
- [ ] **Refactor `voterToken.js`** to `import {generateUuid} from './uuid';` (identical public API).
- [ ] **Run → PASS** (uuid + voterToken). **Commit:** `feat(analytics): extract shared generateUuid util`

### Task A2: `analytics.js` — stateless dataLayer entry point
**Files:** Create `src/utils/analytics.js`; Test `src/utils/analytics.test.js`
- [ ] **Failing tests:** (a) `pushEvent` initialises `window.dataLayer` and pushes `{event, event_id, ...params}` with a uuid `event_id`; (b) explicit `trip_id`/`user_role` in params appear; (c) `undefined`/`null`/`''` are dropped but **`false` and `0` are kept**.
```js
import {pushEvent} from './analytics';
beforeEach(() => { window.dataLayer = []; });
test('pushes event with generated event_id and explicit context', () => {
  pushEvent('vote_opened', {trip_id: 'st-123', user_role: 'participant'});
  const e = window.dataLayer[0];
  expect(e.event).toBe('vote_opened');
  expect(e.trip_id).toBe('st-123');
  expect(e.user_role).toBe('participant');
  expect(e.event_id).toMatch(/^[0-9a-f-]{36}$/);
});
test('keeps false/0, drops undefined/null/empty', () => {
  pushEvent('tb_group_submitted', {has_budget: false, value: 0, ref: undefined, x: ''});
  const e = window.dataLayer[0];
  expect(e.has_budget).toBe(false);
  expect(e.value).toBe(0);
  expect('ref' in e).toBe(false);
  expect('x' in e).toBe(false);
});
```
- [ ] **Run → FAIL.**
- [ ] **Implement:**
```js
import {generateUuid} from './uuid';

// Stateless. trip_id / user_role are passed explicitly by the caller from the
// source of truth (TripContext, the route shareToken, or resolveUserRole) — no
// module state that could leak across role switches in one tab.
export function pushEvent(event, params = {}) {
  window.dataLayer = window.dataLayer || [];
  const payload = {event, event_id: generateUuid()};
  for (const [k, v] of Object.entries(params)) {
    // Strict !== — no coercion, so false and 0 survive; only the three
    // "no value" sentinels are dropped.
    if (v !== undefined && v !== null && v !== '') { payload[k] = v; }
  }
  window.dataLayer.push(payload);
}
```
- [ ] **Run → PASS.** **Commit:** `feat(analytics): add stateless dataLayer pushEvent`

### Task A2b: `userRole.js` + `consent.js`
**Files:** Create `src/utils/userRole.js`, `src/utils/consent.js` (+ tests)
- [ ] **`resolveUserRole` test:** returns `organizer` when `myhive-initiator-{token}` (or `myhive-manager-{token}`) set, else `participant`; returns `organizer` when `shareToken` is falsy (direct/pre-session). Implement:
```js
export function resolveUserRole(shareToken) {
  if (!shareToken) { return 'organizer'; }
  try {
    if (localStorage.getItem(`myhive-initiator-${shareToken}`) ||
        localStorage.getItem(`myhive-manager-${shareToken}`)) {
      return 'organizer';
    }
  } catch (e) { /* localStorage blocked — fall through to participant */ }
  return 'participant';
}
```
- [ ] **`hasConsent` test:** returns `false` when no CookieYes data (deny-by-default); `true` when the mapped category is granted. Implement defensively (verify the exact CookieYes accessor during implementation — `getCkyConsent()`/`window.CookieYes`/`cookieyes-consent` cookie — default to **deny** if unknown):
```js
// signal: 'ad_storage' -> CookieYes 'advertisement'; 'analytics_storage' -> 'analytics'
export function hasConsent(signal) {
  const category = signal === 'ad_storage' ? 'advertisement' : 'analytics';
  try {
    const c = typeof window.getCkyConsent === 'function' ? window.getCkyConsent() : null;
    return !!(c && c.categories && c.categories[category]);
  } catch (e) { return false; }
}
```
- [ ] **Run → PASS.** **Commit:** `feat(analytics): add userRole + consent readers`

### Task A3: `attribution.js` — UTM + click-id + ref capture (90-day, last non-direct)
**Files:** Create `src/utils/attribution.js` (+ test)
Behaviour: `captureFromUrl(search, referrer, nowMs = Date.now())` parses `utm_source/medium/campaign/term/content`, `gclid`, `fbclid`; if **any** utm/click-id present, overwrite `localStorage['myhive-attribution']` with `{...params, referrer, ts: nowMs}` (last non-direct click — a param-less visit does **not** clear/overwrite). `?ref=` is stored **separately** in `localStorage['myhive-ref']`, **never** touched by utm capture. `getAttribution(nowMs)` returns the stored object if `nowMs - ts < 90d` (else clears + `{}`). `getRef()` returns the stored ref. `captureFromUrl` is **idempotent** (safe to call on every navigation).
- [ ] **Failing tests:** (a) capturing `?utm_source=fb&utm_medium=paid_social&utm_campaign=test` stores them + referrer; (b) new utm overwrites; (c) param-less visit does not clear; (d) `?ref=invite` stores ref without touching utm; (e) `>90d` old is expired; (f) calling twice with same `?ref=invite` is idempotent.
- [ ] **Run → FAIL → implement → PASS.** **Commit:** `feat(analytics): add UTM/ref attribution capture util`

### Task A4: `<AttributionCapture/>` (mount-once + SPA-nav, pinned deps)
**Files:** Create `src/components/AttributionCapture.js` (+ test); Modify `src/context/AppProviders.js`
- [ ] **Test (MemoryRouter):** (a) initial `/?utm_source=fb&utm_medium=paid_social` → attribution stored; (b) navigating to `?ref=invite` re-runs capture, stores `ref`, **preserves** `utm_source`; (c) navigating to a param-less route does not clear. Implement render-null with:
```js
const {search} = useLocation();
useEffect(() => {
  captureFromUrl(search, document.referrer);
}, [search]); // re-runs on every SPA navigation; captureFromUrl is idempotent
```
- [ ] Mount `<AttributionCapture/>` as the first child inside `AppProviders` (public-only; `/admin/*` is a separate tree).
- [ ] **Run → PASS.** **Commit:** `feat(analytics): capture attribution on mount + SPA nav`

### Task A5: `trip_id` / `user_role` threading
**Files:** Modify `src/context/TripContext.js`
- [ ] Add `tripId` to state + `SET_TRIP_ID` action + hydrate/persist `localStorage['myhive-trip-id']`. **`CANCEL_TRIP_SETUP` must NOT clear `tripId`** (only items/budget); a fresh group setup mints a new one.
- [ ] **Tests:** `SET_TRIP_ID` sets + persists `tripId`; `CANCEL_TRIP_SETUP` leaves `tripId` intact; hydration restores it.
- [ ] **Commit:** `feat(analytics): add tripId to TripContext (survives cancel)`

### Tasks A6–A19: wire each event (one task per event)
Each task: add `pushEvent(...)` at the **precise fire-site** from the event map, passing `trip_id`/`user_role` explicitly, and a test that mocks `../utils/analytics` (`jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}))`), renders with the needed providers, triggers the action, and asserts `pushEvent` fired **once** with the right name + params. Highlights:
- [ ] **A6 `cta_click`** — `onClick` on each CTA; the `FeaturedActivitiesSection` "View All Activities" `<Link>` gets `onClick={() => pushEvent('cta_click', {cta_label:'View All Activities', block:'activities'})}` (navigation proceeds normally).
- [ ] **A7 `contact_click`** — `onClick` on the WhatsApp/Messenger anchors (`channel`).
- [ ] **A8 `tb_start`** — `useEffect` firing once when the modal opens; `ref: getRef()`.
- [ ] **A9 `tb_group_submitted`** — in `handleConfirm` after validation: mint+`SET_TRIP_ID` (direct path), build `{trip_id, destination, group_size, has_budget}`, and **add `email` only if `hasConsent('ad_storage')`**.
- [ ] **A10 `quiz_completed`** — last-question only; organizer vs participant branch.
- [ ] **A11 `shortlist_completed`** — `selected_count`.
- [ ] **A12 `vote_launched`** — after `createSession` success; `trip_id: shareToken`, `user_role:'organizer'`, `selected_count`.
- [ ] **A13 `vote_skipped`** — `trip_id` from TripContext; `selected_count`.
- [ ] **A14 `vote_opened`** — mount `useEffect` + `useRef` once-guard; `trip_id: shareToken`, `user_role:'participant'`.
- [ ] **A15 `vote_completed`** — after `castVotes` success; same context.
- [ ] **A16 `checkout_viewed`** — inside `.then` after `setData`; `trip_id: shareToken`, `user_role:'organizer'`, `items_count`, `value`, `currency:'EUR'`.
- [ ] **A17 `booking_form_viewed`** — `useEffect([isOpen])` in `ContactForm`; `value`, `currency`.
- [ ] **A18 `booking_submitted`** — after API success; spread `getAttribution()` + `ref:getRef()`, `value`, `currency:'EUR'`, `activities_count`, `destination`, `group_size`; **dedup:** `if (!sessionStorage.getItem('myhive-booked-'+tripId)) { pushEvent(...); sessionStorage.setItem('myhive-booked-'+tripId,'true'); }`. Test re-render/reload fires once.
- [ ] **A19 `api.createBookingFromTrip`** — send `tripId` (**= `voteSession` query param if present, else `TripContext.tripId`**), `getAttribution()`, `getRef()` in the `POST /bookings/trip` body. Add explicit threading of `?voteSession=` in `TripBuilder.handleContactSubmit`.
- [ ] Each: run test → PASS → **commit** `feat(analytics): emit <event>`.

---

## Workstream B — Backend `trip_id` + attribution persistence + email/CRM (TDD)

### Task B1: `Booking` gains `tripId` + attribution columns
- [ ] Test (`BookingServiceTest`, `expected`-prefixed values): `createBookingFromExport` persists a supplied `tripId` + utm fields; with `tripId` omitted, generates `TRV-<8 hex upper>` (assert prefix + length).
- [ ] `Booking.java`: `tripId` (String) + `utmSource/utmMedium/utmCampaign/utmTerm/utmContent/ref/gclid/fbclid/referrer` (nullable String). `BookingDTO.java`: `tripId`. `TripExportRequest.java`: same optional fields.
- [ ] `BookingService.createBookingFromExport`: `booking.setTripId(req.getTripId() != null ? req.getTripId() : "TRV-" + UUID.randomUUID().toString().substring(0,8).toUpperCase())`; copy attribution; save.
- [ ] **Run → PASS.** **Commit:** `feat(booking): persist trip_id and attribution`.

### Task B2: `trip_id` + UTM into the booking-notification email
- [ ] Test: the booking-notification context (to `info@trivlu.com`) includes `tripId` + utm.
- [ ] `EmailService`: add `tripId`/utm to the notification context; render a "Trip ID / Source" block in the notification template (not necessarily the customer itinerary).
- [ ] **Run → PASS.** **Commit:** `feat(email): surface trip_id + UTM in booking notification`.

### Task B3: `ref=invite` on participant links
- [ ] Test: `vote-created.html` participant link contains `?ref=invite`.
- [ ] Append `?ref=invite` in `templates/email/vote-created.html` and any frontend share-link builder (`CuratePage`/`VoteWaitingPage`).
- [ ] **Run → PASS.** **Commit:** `feat(vote): tag participant invite links with ref=invite`.

---

## Workstream C — GTM / GA4 / Meta / Clarity configuration (browser runbook, no repo code)

### C1 — Create the GA4 property (spec §6/§11)
Create property "Trivlu" (currency **EUR**) → **Web** stream `https://trivlu.com` → copy **Measurement ID `G-XXXXXXXXXX`** into §11; Data retention **14 months**; marketing user **Administrator**; register **`trip_id`** + **`user_role`** as **custom dimensions (event scope)**; later mark **`generate_lead`**, **`tb_group_submitted`** (and Phase-2 `purchase`) as **key events**. **Skip** GA4's own tag-install prompt.

### C2 — GTM (container `GTM-KB7BJLDS`)
**Tag firing order (document + enforce):** the CookieYes CMP tag fires first on *Consent Initialization – All Pages* and sets Consent Mode v2 defaults (already configured in the prior session); the **GA4 Configuration** tag fires after it (same trigger; use tag sequencing so GA4 never runs before the consent default). 

**CookieYes category → Consent Mode mapping (verify in dashboard):** CookieYes "Analytics" → `analytics_storage` (gates **Clarity** + GA4 cookie behaviour); CookieYes "Advertisement" → `ad_storage` (gates **Meta**).

- **Data Layer Variables** for every param incl. `event_id`, `trip_id`, `user_role`, `value`, `currency`, `email`, `cta_label`, `block`, `channel`, `destination`, `group_size`, `has_budget`, `selected_count`, `items_count`, `activities_count`, `q_*`, `ref`, `utm_*`.
- **Triggers:** one Custom Event trigger per event name.
- **GA4 Config tag** (Measurement ID) on Consent Init/All Pages; Consent Mode handles cookieless pings pre-consent.
- **GA4 Event tags** — one per event. **The booking event is sent to GA4 as `generate_lead`** (GA4 recommended lead event → key event); every other GA4 event keeps its dataLayer name (resolves the spec §6 vs §8 naming conflict). Always include `trip_id`/`user_role` params.
- **Meta Pixel** — use the **official Meta/Facebook Pixel community template** (not Custom HTML — Custom HTML bypasses GTM's native consent enforcement and is error-prone). Base `PageView` all pages; **requires `ad_storage`**. Per-event tags: `ViewContent`←`tb_start`, `Contact`←`contact_click`, `CompleteRegistration`←`tb_group_submitted` (+ **Advanced Matching email = `{{email}}`**, present only when consent-gated push provided it), `InitiateCheckout`←`checkout_viewed`, **`Lead`←`booking_submitted`** with `value`+`currency`, `Purchase`←`payment_completed` (**Phase 2**). Set **Event ID = `{{event_id}}`** on every Meta event (consequential for dedup on `Lead`/future `Purchase`; the future server CAPI call must reuse the same `event_id`).
- **Microsoft Clarity** — official **Microsoft Clarity GTM template**, Project ID **`x5bd9qsdhk`**, **requires `analytics_storage`**. For custom tags, **buffer against the async SDK load**: on Consent Init, define `window.clarity = window.clarity || function(){(window.clarity.q=window.clarity.q||[]).push(arguments)}`, then push `clarity('set','trip_id',{{trip_id}})` / `clarity('set','user_role',{{user_role}})` on relevant events; the real SDK flushes the queue when it loads. Verify no console errors and that recordings show the tags.
- **Consent:** Meta + Clarity must **not** fire on Reject; GA4 stays cookieless pre-consent.

### C3 — Clarity dashboard
Confirm masking hides Email / Full Name / Phone (Balanced default; switch to **Strict** if any leak); enable **Clarity ↔ GA4** integration; confirm recording on all screens incl. `/vote/*` + swipe mechanic.

### C4 — Publish
Preview (Tag Assistant) → verify mapping + consent gating → **publish** a named container version.

---

## Workstream D — Testing & acceptance (spec §10)

- [ ] GA4 **DebugView**: every event with correct params incl. `trip_id`/`user_role`.
- [ ] Meta **Test Events** + **Pixel Helper**: all Meta events fire, no dup/error; `Lead` has `value`+`currency`; `event_id` present.
- [ ] **Clarity**: recordings on all screens; Email/Name/Phone masked; filter by `trip_id`/`user_role` works; no SDK-race console errors.
- [ ] **Consent (EU, Network panel):** before Accept → Meta + Clarity **not** loaded; **Reject** → never loaded (no `clarity.ms`/Meta requests), site + Trip Builder fully functional; GA4 cookieless pings only. Toggle the CookieYes Analytics/Advertisement categories and confirm correct gating.
- [ ] **Email consent gate:** `tb_group_submitted` pushes `email` only with `ad_storage` granted (inspect dataLayer with consent denied → no email).
- [ ] **Attribution integration test:** visit `trivlu.com/?utm_source=fb&utm_medium=paid_social&utm_campaign=test`, navigate 3–4 SPA steps, submit booking → assert the `Booking` row + notification email carry `utm_*` + `trip_id` (DB/email assertion, not just dataLayer).
- [ ] **Viral loop:** open a `?ref=invite` participant link, later start the Trip Builder → `tb_start` carries `ref=invite` and saved `utm_*` are intact.
- [ ] **Mobile** (primary Meta traffic): consent banner, swipe events, SPA events, no CTA overlap.

---

## Phase 2 (separate task — blocked on the payment feature)
1. Build the payment feature (UI + Stripe checkout + webhook + cost-splitting + participant-payment tracking) — none exists. Then wire `payment_page_viewed`, `payment_completed` (`Purchase`), `trip_fully_paid` (§8 rows 14–16, §8.1, §8.4 payment dedup).
2. **Meta CAPI**: server `Lead`/`Purchase` by webhook, deduped by the same `event_id`.
3. **GA4 Measurement Protocol**: server `purchase`/`trip_fully_paid`.
4. **Campaign→revenue report** from CRM via `utm → trip_id → sum(payments)`.
5. **Remarketing audience** "paid a share but isn't an organizer".
6. **Privacy review** before any server-side PII send (CAPI/MP): no raw email without consent, HTTPS, consent-typed.

---

## §11 identifiers (live status)

| Asset | ID / value | Status |
|-------|-----------|--------|
| Meta Pixel (dataset "Trivlu data") | `1482052533162342` | ready |
| Domain verification (Meta) | — | ready |
| **GTM container** | **`GTM-KB7BJLDS`** | **exists — reuse** |
| GA4 Measurement ID | `G-XXXXXXXXXX` | create in C1, then fill |
| Microsoft Clarity | `x5bd9qsdhk` | ready |
| CookieYes CMP | key `29814bffa362e67fefae8c3d10e1dfcf` | live (Consent Mode v2 + Reject + policies done) |
| CRM / booking inbox | UTM+`trip_id` added to the notification to **`info@trivlu.com`** by default | marketing to confirm if a dedicated CRM is wanted |

---

## Self-review (spec coverage)
- §5 Meta: base PageView (C2), standard events mapped, `event_id` on every event (A2/C2), Advanced Matching email **consent-gated** at `tb_group_submitted` (A9/C2), `value`/`currency` on `InitiateCheckout`/`Lead` (A16/A18), optimisation on `Lead`. Purchase → Phase 2.
- §6 GA4: property runbook (C1), tag via GTM only, events same names **except** booking→`generate_lead` (documented), key events (C1), custom dimensions (C1). ✔
- §7 Clarity: official template + Project ID, consent-gated, all screens, masking/Strict, GA4 integration, buffered custom tags. ✔
- §8 dataLayer: stateless `pushEvent` (A2), 13 events wired with precise idempotent fire-sites (A6–A18), `trip_id` where the trip exists. §8.1/§8.4-payment → Phase 2. §8.2 sequential `trip_id` (A5/A12/A19/B1). §8.3 roles excluded from conversions = only `booking_submitted`/`generate_lead` is the key/optimisation event. ✔
- §9 UTM + viral loop: capture/persist 90-day last-non-direct (A3), SPA-nav safe (A4), attached to booking + email/CRM (A18/A19/B1/B2), `ref=invite` separate + on links + carried into `tb_start` (A3/A8/B3). ✔
- §10 acceptance: Workstream D incl. consent-gate + attribution integration tests. ✔  §11: table. ✔  §12: Phase 2. ✔

**Coverage gaps = only the payment-dependent items**, correctly deferred to Phase 2.
