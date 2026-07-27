# Lead-Capture Improvements — Design

**Date:** 2026-07-27
**Source:** Miro board "проблема — люди не доходят до квиза" + clarification session
**Related research:** `docs/superpowers/research/2026-07-27-lead-capture-behavioral-patterns.md`
**App:** `myhive-react-app` (myhive-next is untracked scaffolding; out of scope)

## Problem

People drop off before reaching the quiz. The first modal (`TripSetupModal`) is a
toll gate: destination + travelers + a giant two-month inline calendar + email +
consent note, all required before any value is delivered. The browse-activities
funnel captures no email at all before the booking form. Expected result of this
batch: **more captured emails**.

**North-star metric:** leads created / hero CTA clicks. Existing funnel events
(`cta_click`, `tb_start`, `tb_group_submitted`) stay in place.

## Scope

### 1. First modal — small "Create an Itinerary" form (both entry points)

`TripSetupModal` serves two entries: Start Group Vote (hero / How It Works) and
first activity added in browse. In both modes it becomes a small form:

- **Fields:** destination (auto-selected read-only text or picker, unchanged
  logic) + number of travelers (existing stepper) + date range as a **compact
  field that opens the calendar in a small anchored popover** — the giant
  two-month inline calendar is removed.
- **Email and `EmailConsentNote` are removed from the modal entirely** (vote
  mode currently collects them here; it stops doing so).
- Validation semantics otherwise unchanged: vote mode still requires
  destination + dates before Continue; browse mode unchanged.
- **Buttons:** Confirm stays `btn--primary`. Cancel changes from the teal
  secondary style to a neutral/ghost style so it no longer competes with
  Confirm. (New `btn--ghost` or equivalent existing class.)
- **Close on outside click:** `closeOnBackdrop` enabled for `TripSetupModal`
  and `StartGroupVoteModal`.
- **Draft persistence:** values entered (travelers, dates; email in
  `StartGroupVoteModal`) survive close/reopen — today the open-effect wipes
  them (`TripSetupModal.js:48`). Draft lives in trip context or localStorage
  (implementation detail for the plan); it is cleared on successful submit.

### 2. Email moves to the moment of value

- **Vote flow:** email is asked only at the send-the-vote-link step —
  `StartGroupVoteModal`, which already collects `initiatorEmail` (and dates
  when missing via `needsDates`). `leadApi.createLead` moves out of
  `useStartGroupVote.handleVoteConfirm` (no email exists there anymore) to the
  point where the email is actually submitted. The quiz `setup` navigate-state
  and downstream references drop the `email` field.
- **Browse flow:** email is asked at booking checkout (existing form). New:
  when a **valid email is typed at checkout, create/update the lead
  immediately (debounced)**, reusing the existing debounced trip-lead snapshot
  sync — checkout abandoners then enter the existing reminder/restore cadence.
  `EmailConsentNote` must be present next to the checkout email field.
- Analytics: `tb_group_submitted` no longer carries `email`; lead-capture
  events fire where capture now happens. No new event names required.

### 3. Homepage

- **Section order:** hero → **FeaturedActivitiesSection** → TrustBar →
  HowItWorksSection → ReviewsSection → ContactCtaSection (activities move
  directly under hero).
- **Hero title:** «The Easiest Prague Stag Do. All Sorted For You.» Update
  `<title>` and meta description accordingly (keep "stag do" + "Prague"
  phrasing for SEO). Hero vote-tally card stays where it is.

### 4. How It Works visuals

- **Tinder step:** step 2 «Handpick the shortlist» (the step whose current
  screenshot shows the swipe screen — confirm visually before replacing) gets a
  CSS-built full-block "Tinder moment": the **Steak & Tits activity photo**
  fills the step block, with a swipe-choice overlay (card stack edges, ❤️ / ✖
  buttons, LIKE stamp) so the choice moment reads instantly.
- **Vote card:** the hero vote-tally card markup is extracted into a shared
  component and **copied** (hero keeps its own) into the "Send the vote link"
  step, replacing that screenshot.
- **Limo step:** that step's image is replaced with a limousine photo from the
  activities catalog.
- **Assets:** the two photos (Steak & Tits, limo) are committed as local
  assets; all four `cdn.jsdelivr.net/gh/cyrudi/sandbox` hotlinks are removed.

### 5. Mobile alignment — destination page

Visual pass on the destination page (Prague) at mobile viewports: uniform
category-chip grid, consistent card paddings and gutters (16px rhythm), no
horizontal scroll. Judgement call per element; scope limited to alignment and
spacing, no redesign.

### 6. WhatsApp contact widget

Floating "Contact us" WhatsApp button on all public pages (not admin):

- Fixed bottom-right FAB with the `ph-whatsapp-logo` icon, opening the existing
  `WHATSAPP_URL` (`services/config.js`) in a new tab.
- Fires `cta_click` with `cta_label: 'whatsapp_widget'` and the current page.
- z-index below modal overlays; on mobile it must not cover primary CTAs or the
  swipe controls on vote pages (offset or hide there — judgement call in
  implementation).

## Error handling

- Lead creation stays fire-and-forget: failures never block quiz, vote link
  creation, or checkout.
- Debounced checkout lead capture must not duplicate leads: reuse the existing
  supersede-on-recapture behaviour (`86f4ef7`).
- Draft persistence must keep dropping stale past dates on reopen (existing
  `todayLocalIso` guard).

## Testing

- Update `TripSetupModal` tests: email field gone in both modes, backdrop
  close, draft persistence across reopen, compact date field.
- `StartGroupVoteModal` tests: lead created on submit with consent note (pinned
  by existing capture-modal tests).
- Checkout: debounced lead capture on valid email, consent note rendered,
  no lead on invalid email.
- `HomePage` tests: section order, new title/meta.
- `HowItWorksSection` tests: renders shared vote-card component and local
  assets, no CDN URLs.
- WhatsApp widget: rendered on public pages, absent on admin routes, link
  target and analytics event.

## Out of scope

- Email-ask-after-quiz A/B experiment (strongest research bet — separate batch,
  this batch is its control arm).
- `myhive-next` migration work; backend changes (lead API already accepts null
  dates).
