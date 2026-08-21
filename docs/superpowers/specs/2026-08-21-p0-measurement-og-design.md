# P0: Measurement + OG/Tech-Debt Fixes — Design

**Date:** 2026-08-21 · **Branch:** `new-landing-seo-fixes` · **Status:** approved (in-chat)

## Context

Audit of the P0 checklist (see fixes/ТЗ_Аналитика_trivlu_v1.6.md — authoritative analytics spec) found:

- GTM-KB7BJLDS wired in both frontends; GA4/Pixel/Clarity tags live in the GTM container, not code (per ТЗ §3: no hardcoded tag snippets).
- Meta Pixel ID exists: **1482052533162342** (dataset "Trivlu data", CAPI option already activated). No CAPI token generated yet.
- 16+ dataLayer events exist (`myhive-react-app/src/utils/analytics.js`) with auto `event_id` (UUID); missing params: `nights`, `vote_id`, `source_campaign`; `group_size`/`activities_count` only on some events.
- No Meta CAPI on backend; `fbclid` stored on Booking but `fbp`/`fbc` cookies not sent.
- Vote pages are SPA-only (catch-all `app/[...slug]/page.tsx`, `SPA_NESTED`), no OG tags; no groom-name field anywhere in schema.
- `twitter:card` = `summary`; og-image 1000×1000 (`lib/seo.ts:61`, `layout.tsx:30`).
- No invitee tracking on vote sessions (share-link model, only distinct voter tokens who voted).
- Booking has `createdAt`/`paidAt` + full UTM set, but no first-touch timestamp persisted; no export/report endpoints (only `/admin/bookings/stats` status counts).
- Package price is computed (sum of activities − `discount_pct`) → €444.62 artifacts. "Stag Premiumtest" exists only in the production DB (not in code, not in local dev DB).
- "We've done this thousands of times" in TrustBar (`myhive-react-app/src/components/home/TrustBar.js:4` + `myhive-next/legacy-src/.../TrustBar.js:4`).
- New landing mockups (`fixes/trivlu-landing-1-voting-v58.html`) carry the approved verifiable trust copy.

## Package A — Quick wins

1. **TrustBar copy**: replace the "Stag Do Specialists / We've done this thousands of times" card with **"15 years in Prague / Local team. No agency fee."** (from mockup trust strip). Both copies: `myhive-react-app/src/components/home/TrustBar.js` and `myhive-next/legacy-src/components/home/TrustBar.js`.
2. **twitter:card** → `summary_large_image` in `myhive-next/app/layout.tsx` and `myhive-react-app/public/index.html`. New static brand og-image **1200×630** (`public/og-image.png` in both apps); update hardcoded dims in `lib/seo.ts` and `layout.tsx`.
3. **Package price rounding**: round the computed package price to whole euros (HALF_UP) at the backend computation point so €444.62 → €445 everywhere (list, detail, cart). Renaming "Stag Premiumtest" is a production-admin action (see Ops doc).
4. **Event param enrichment**: a funnel context module in the SPA (`analytics.js`) that auto-appends to every `pushEvent` where known: `nights` (from trip start/end dates), `vote_id` (vote session id), `group_size`, `activities_count`, `source_campaign` (= stored `utm_campaign`, last-non-direct). Event **names** unchanged (ТЗ §8 requires exact names).

## Package B — Meta stack

5. **Backend `MetaCapiService`** (Spring): POSTs to `https://graph.facebook.com/v21.0/{pixelId}/events`. Config via env `META_PIXEL_ID`, `META_CAPI_ACCESS_TOKEN`, optional `META_CAPI_TEST_EVENT_CODE`; silently disabled when unset. Async, non-blocking, failures logged not thrown.
   Events sent:
   - `Lead` — on booking creation, `event_id` supplied by client with the booking payload (same UUID the browser pushed to dataLayer → dedup).
   - `Purchase` — on Stripe payment webhook; `event_id` derived deterministically from the Stripe session id (client `payment_completed` push switches to the same derivation).
   - `start_group_vote` (custom) — on vote-session creation.
   User data: hashed email (SHA-256) when known, client IP + UA, `fbp`/`fbc` cookies forwarded by the frontend with booking/vote requests; `fbc` derivable from stored `fbclid`.
6. **Client**: capture `_fbp`/`_fbc` cookies, include with booking submit and vote-session create; push email to dataLayer after `tb_group_submitted` for GTM Advanced Matching.
7. **GTM ops doc** (`docs/ops/gtm-meta-setup.md`): Pixel base tag (PageView), event mapping table from ТЗ §8, `vote_launched` → Meta custom event `start_group_vote` + custom conversion, Advanced Matching, CAPI token generation steps, GA4 custom dimensions (`trip_id`, `user_role`).

## Package C — Vote page OG

8. **Schema**: nullable `groom_name` varchar(100) on `vote_sessions`; optional "Who's the stag?" input in `StartGroupVoteModal`; exposed in vote-session create API + share/result DTOs.
9. **SSR metadata for `/vote/[shareToken]`**: dedicated Next.js route replacing the catch-all for this path. `generateMetadata` fetches the session by shareToken (name, destination, voter count, selected activity images) → `og:title` "Vote on {groom}'s stag do — {n} have voted" (generic fallback without name), `og:description`, `og:url`, `twitter:card summary_large_image`, keeps `noindex`. Page body renders the same SPA client component as today (no behavior change).
10. **Dynamic collage** via `opengraph-image.tsx` (`next/og` ImageResponse, 1200×630): up to 4 activity photos + voter-count badge + Trivlu branding; static brand fallback when session/images unavailable.

## Package D — Data & reports

11. **Vote-link open tracking**: `vote_session_opens` table (session_id, voter_token, first_opened_at, unique per session+token); recorded when a participant opens the vote page (welcome screen fetch). "Invited (opened link)" proxy — true invite counts don't exist in a share-link model.
12. **First-touch attribution**: `attribution.js` additionally stores a never-overwritten first-touch record (timestamp + params of the very first visit); sent as `first_touch_at` (+ `first_utm_source`, `first_utm_campaign`) with booking submit → new nullable columns on `bookings`.
13. **Admin CSV exports** (admin-gated, reuse `ActivityCsvExporter` patterns):
    - `GET /admin/votes/export.csv` — per vote session: id, groom_name, created_at, opened_count, voted_count, booking_id, booking_created_at, paid_at (empty when no booking; joined via `bookings.vote_session_id`).
    - `GET /admin/bookings/first-touch-report.csv` — paid bookings: first_touch_at, created_at, paid_at, days first-touch→paid, utm_source/medium/campaign, ref, vote_session_id.

## Out of code scope (Ops doc `docs/ops/p0-manual-actions.md`)

- Rename "Stag Premiumtest" in production admin; verify price shows €445 after rounding deploy.
- GTM container config (tags per §B7), GA4 key events, Meta custom conversion on `start_group_vote`, CAPI access token generation.
- Meta Sharing Debugger pass over templates + real WhatsApp render check on iOS/Android (after C ships).

## Testing

- Backend: unit tests for price rounding, CAPI payload construction (WireMock-style stub, disabled-when-unconfigured), export CSV contents, migrations.
- Frontend: unit tests for funnel-context enrichment and first-touch storage; existing analytics tests stay green.
- Manual: GA4 DebugView params, Test Events in Meta Events Manager (test_event_code), OG render via local fetch of `/vote/{token}` head + opengraph-image.

## Execution order

A → B → C → D, sequential commits per package on `new-landing-seo-fixes`.
