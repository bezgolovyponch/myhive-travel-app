# Admin payment link — design

- **Date:** 2026-07-01
- **Branch:** `feat/vote-prepayment` (built directly on top of the existing deposit work; the
  `feat/vote-balance-collection` split branch is **not** touched)
- **Status:** approved design, pending spec review

## Context & problem

The deposit feature collects a 30% prepayment via Stripe Checkout at the end of the vote/booking
flow. The remaining balance is currently handled entirely off-platform. The team needs a way to
collect further payment against a booking.

The original balance design (`feat/vote-balance-collection`) auto-split the balance among vote
participants (SPLIT / INITIATOR_PAYS modes). That is being abandoned: in practice the balance is
usually **not** split — one person pays — and the amount is **not** fixed, because the price is
often renegotiated during a conversation with the customer.

## Goal

Give an admin a simple tool, on the booking detail page, to **create a Stripe Payment Link for an
arbitrary amount** against a booking. The amount field is pre-filled with the outstanding balance
(`totalAmount − amountPaid`) but is freely editable. The admin copies the URL and sends it however
they like; when the customer pays, the amount is credited to the booking. The same tool also collects
**add-on charges on already-paid bookings** (e.g. an upsell agreed after the trip was fully paid).

### Non-goals (v1)

- No auto-splitting among participants (the SPLIT branch is untouched and unused here).
- No automatic delivery — the admin copies the link and sends it manually (no customer email).
- No editing of the booking's `totalAmount`. This tool only collects money; it does not renegotiate
  the recorded trip total. (A custom amount simply credits `amountPaid`; status is derived from the
  existing total. An add-on charge on a `PAID` booking pushes `amountPaid` above `totalAmount`, which
  is expected and leaves the booking `PAID`.)

## User flow

1. Deposit paid → booking is `DEPOSIT_PAID`, `amountPaid` = deposit, `balance = totalAmount − amountPaid`.
2. Admin opens the booking in the admin panel (`AdminBookingDetail`), sees a **Payment** panel with
   Total / Deposit paid / Balance due / status.
3. Amount field is pre-filled with the balance; admin optionally edits it to any value.
4. Admin clicks **Create payment link** → a row appears with the Stripe URL and a **Copy** button.
5. Admin copies the URL and sends it to whoever will pay.
6. Customer opens the link and pays → Stripe fires `checkout.session.completed` carrying our
   metadata → the webhook credits the matching share.
7. `amountPaid` is recomputed; status becomes `PAID` when `amountPaid ≥ totalAmount`, otherwise
   `PARTIALLY_PAID`. The paid Payment Link is deactivated so it cannot be paid twice.

## Data model

- New `PaymentShareType.BALANCE`.
- A `BookingPaymentShare` (type `BALANCE`) per created link, storing `amount`, `stripePaymentLinkId`,
  `paymentUrl`, `paid`, `paidAt`, `stripePaymentIntentId`, `payerEmail` (same share entity as the
  deposit uses).
- **Multiple links allowed.** Because the amount can differ between attempts, there is **no reuse** —
  each "Create payment link" click creates a new link + share. The booking page lists all links with
  their status.

## Backend

### Endpoint

`POST /admin/bookings/{id}/payment-link` — JWT, `ADMIN`/`MANAGER` (same guard as other `/admin/**`
routes). Body: `{ "amountCents": <long> }`. Response: `{ "url": <string>, "amount": <decimal>,
"shareId": <uuid> }`. Also expose the existing links via the booking DTO (`GET /admin/bookings/{id}`)
so the UI can list them.

### Service — `PaymentService.createAdminPaymentLink(bookingId, amountCents)`

- Load the booking; 404 if missing.
- Guard: reject only if status is `CANCELLED` (400/409, clear message). `PAID` bookings are allowed —
  the link then collects an add-on (`amountPaid` rises above `totalAmount`; status stays `PAID`).
- Validate `amountCents`: `≥ STRIPE_MIN_CHARGE_CENTS` (50) and `≤` a sanity cap
  (`ADMIN_PAYMENT_LINK_MAX_CENTS`, in cents; default `5_000_000` = €50,000) — guards against typos.
  The admin is trusted (JWT), so client-price-tampering (C1) protection is not required here.
- Create a Stripe Payment Link for `amountCents` with metadata `{ booking_id, share_id }`.
- Persist a `BALANCE` share (amount, `stripePaymentLinkId`, `paymentUrl`, `paid=false`).
- Return `{ url, amount, shareId }`.

### Webhook — reuse `handlePaymentSucceeded`

A Payment Link payment produces a `checkout.session.completed` event carrying our metadata, so the
existing share-resolution + fulfilment path already applies:

- Resolve the `BALANCE` share by metadata; if already paid, no-op (idempotent).
- `SEC-2` amount check still holds: Stripe's charged amount must equal the share amount.
- Mark the share paid; recompute `amountPaid` (sum of paid, non-refunded shares); set status to
  `PAID` (`amountPaid ≥ totalAmount`) or `PARTIALLY_PAID`.
- **New:** after fulfilment, deactivate the Payment Link (`deactivatePaymentLink`) so the URL cannot
  be paid again. Best-effort (wrapped in try/catch — a deactivation failure must not break the webhook).
- Send the existing payment-received email (fully-paid subject when `amountPaid ≥ totalAmount`).

### Stripe gateway

Add to the gateway abstraction:

- `createPaymentLink(amountCents, currency, description, metadata) → { id, url }`
- `deactivatePaymentLink(id)` (sets `active=false`)

## Admin UI — `AdminBookingDetail.js`

- **Payment panel:** Total, Deposit paid, Balance due (`max(0, total − amountPaid)`), status badge.
- **Amount input** pre-filled with the outstanding balance when it is > 0; empty for fully-`PAID`
  bookings (admin types the add-on amount). Editable in all cases. + **Create payment link** button →
  `adminApi.createAdminPaymentLink(id, amountCents)`.
- **Links list:** each created link shows amount, status (Unpaid / Paid), and a **Copy** button for
  unpaid links (reuse the shared clipboard util).
- The create control is shown for all non-`CANCELLED` bookings (including `PAID`, for add-ons).

## Edge cases & guards

- Booking `CANCELLED` → no create control (and 400/409 if forced). `PAID` bookings are allowed
  (add-on charge) — `amountPaid` rises above `totalAmount` and the booking stays `PAID`.
- Amount below Stripe minimum or above the sanity cap → 400 with a clear message.
- Double-submit → each click makes a distinct link (accepted by design); the list shows all.
- Overpayment (custom amount pushes `amountPaid` past `totalAmount`) → booking becomes `PAID`
  (existing `fullyPaid` logic); acceptable.
- Underpayment → `PARTIALLY_PAID`; admin can create another link for the rest.
- Refund after payment → existing `charge.refunded` webhook nets `amountPaid` and recomputes status.
- Paid link paid again (race) → webhook event idempotency + `share.isPaid` guard prevent
  double-credit; deactivation shrinks the window.

## Security

- Endpoint behind the admin JWT filter (`ADMIN`/`MANAGER`), like all `/admin/**` routes.
- Amount is admin-set and trusted; only min/max sanity validation.
- Metadata-based webhook resolution + signature verification are unchanged from the deposit flow.

## Testing

- **Service:** creates link + `BALANCE` share for a valid amount; rejects amounts below min / above
  cap; rejects `PAID`/`CANCELLED` bookings; allows multiple links.
- **Webhook:** paying a `BALANCE` share credits `amountPaid`, transitions to `PAID`/`PARTIALLY_PAID`,
  and deactivates the Payment Link; already-paid share is a no-op.
- **Controller:** endpoint requires an admin JWT; returns `{ url, amount, shareId }`; unauthenticated
  → 401/403.
- **Frontend:** `AdminBookingDetail` renders the Payment panel + prefilled amount; create calls the
  API with the entered `amountCents`; the links list renders statuses and the copy action; the create
  control is shown for `PAID` bookings (add-on) and hidden for `CANCELLED`.

## Resolved decisions

- Admin-created link only — no split (split branch untouched).
- Manual delivery — admin copies the URL; no auto-email.
- Stripe **Payment Link** (persistent, no ~24h expiry) — deactivated on payment.
- **Editable** amount, pre-filled with the balance.
- Multiple links allowed (no reuse), listed on the booking page.
- Allowed on `PAID` bookings too (add-on charges); only `CANCELLED` is blocked.
- Built on `feat/vote-prepayment`.
