# Admin Booking Detail: status dropdown

Date: 2026-07-21. Approved by user in-session.

## Problem

Admins cannot change a booking's status from the UI. The backend endpoint
`PATCH /bookings/{id}/status` (ADMIN role) exists but no component calls it;
cancelling test bookings required a hand-crafted API call. Payment statuses
are owned by the Stripe webhook, so unrestricted manual status writes can
desync `booking_payment_shares` bookkeeping.

## Decision

Dropdown on the admin Booking Detail page, limited to **operational**
statuses: `PENDING`, `CONFIRMED`, `CANCELLED`. Payment statuses
(`DEPOSIT_PAID`, `PARTIALLY_PAID`, `PAID`, `REFUNDED`) remain
webhook-only.

## Backend

- `BookingService.updateBookingStatus` accepts only the three operational
  statuses; any payment status is rejected with `BadRequestException`
  ("managed by the Stripe webhook"). The webhook does not use this method,
  so fulfilment is unaffected.
- Transition rule: from an operational status any operational status is
  allowed; from a payment status (money is in) the only legal manual exit is
  CANCELLED — PENDING/CONFIRMED would hide the collected payment.
- Cancelling deactivates every unpaid Stripe Payment Link of the booking
  (webhook would otherwise resurrect a cancelled booking if an old link got
  paid). Fail-loud before the status write: a failed deactivation fails the
  cancel, which the admin can retry.
- The endpoint moved to `PATCH /admin/bookings/{id}/status` (AdminController),
  with an ADMIN-only matcher above the ADMIN|MANAGER `/admin/bookings/**`
  rule — following the CSV-export precedent. The legacy
  `PATCH /bookings/{id}/status` special case in the public block is gone.
- The endpoint no longer accepts `stripeSessionId` (webhook-owned field).
- Unit tests: each operational status succeeds; each payment status is
  rejected; payment→CONFIRMED rejected, payment→CANCELLED allowed; cancel
  deactivates only unpaid links and fails loud on gateway errors; security
  tests cover 401 (anon), 403 (MANAGER), pass-through (ADMIN).

## Frontend

- `adminApi.updateBookingStatus(id, status)` — JWT-authenticated PATCH.
- The JWT-less `api.updateBookingStatus` in `services/api.js` is dead code
  (endpoint requires ADMIN) — removed.
- `AdminBookingDetail`: a "Change status" select next to the status badge,
  options = operational statuses; on change → PATCH → booking state updated
  from the response; disabled while saving; error shown in an Alert.
- Visible only when the user has the ADMIN role (MANAGER would get 403).
- A booking currently in a payment status keeps its badge; the dropdown
  offers only CANCELLED (mirrors the backend transition rule). Money refunds
  still happen in Stripe; the webhook later sets REFUNDED.
- On success the badge updates from the PATCH response locally (no full
  refetch, no page-flashing spinner).
- RTL tests: change calls the API and updates the badge without a refetch;
  payment-status booking offers only CANCELLED; hidden for MANAGER; server
  error surfaces in the alert.
