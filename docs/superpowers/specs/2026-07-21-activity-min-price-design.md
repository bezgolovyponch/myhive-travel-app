# Activity Minimum Price (Group Minimum) — Design

**Date:** 2026-07-21
**Status:** Approved

## Problem

Some activities (boat rental, quad tours) have a supplier-imposed minimum order value per
group. Today the cart prices every activity strictly as `price × travelers`, so a 4-person
group books a €300-minimum boat for 4 × €50 = €200. We need a per-activity minimum price
that floors the line total, and the UI must signal it ("from" pricing) so the quoted total
never surprises the customer.

## Semantics (agreed)

- `minPrice` is a **group minimum** for one activity line:
  `line = max(price × travelers, minPrice)`.
- `minPrice` null or 0 → no minimum; everything behaves exactly as today.
- **Packages:** the floor applies per line **before** the package discount:
  `packageGroup = (Σ floored lines) × (100 − discountPct)%`.
- **Display:** when `minPrice > 0`, show both prices — per-person price prefixed with
  "from" plus a secondary "Group minimum €X" note.

## Data Model

### `Activity` (backend entity)

```java
@Column(name = "min_price", precision = 10, scale = 2)
private BigDecimal minPrice;   // null or 0 = no minimum
```

Prod runs `ddl-auto=update` → column is added automatically; dev/test use `create-drop`.
No SQL migration needed (same as `featured`).

### `BookingItem` (snapshot)

New `min_price` column, same snapshot pattern as `price` and `package_discount_pct`:

- Catalog-anchored lines (`activityId` present): snapshot copied server-side from
  `activity.getMinPrice()`. The client value is **never** trusted (SEC-1).
- Custom lines (lenient lead flow, no `activityId`): snapshot stays null — no floor.

### DTOs

- `ActivityDTO`: `minPrice` with `@PositiveOrZero`, mapped both directions.
- `ResultActivityDTO` (vote result): add `minPrice` so the organizer landing in the Trip
  Builder after the last swipe gets it in hydrated trip items.
- `TripExportRequest.ActivityExport`: optional `minPrice`, used **only for email
  rendering** (never for pricing). The webhook/rebuild path fills it from the
  `BookingItem` snapshot.

## Backend Calculation

- `BookingService.getGroupTotal`: extract a `lineTotal(BookingItem)` helper returning
  `max(price × quantity, minPrice)` (null-safe minPrice → 0). Applies to standalone and
  package lines alike; package discount is applied to the floored sum, as today.
- `verifyChargeablePricing` (C1 guard): unchanged in spirit — it recomputes the total
  from snapshots and `calculateTotal` now floors. Snapshot semantics match `price`:
  fixed at booking creation, later catalog drift does not break payment.
- Deposits/Stripe: automatically consistent — deposit = 30% of `totalAmount`, which is
  already floored.

## Emails

`EmailService.buildDestinationViews` lists per-person line prices. When a line's floor
binds (`price × travelers < minPrice`), append a "Group minimum €X applies" note to that
line so the email math visibly reconciles with the charged total. Package group subtotals
in the email use the floored lines (before discount), mirroring the cart.

## Frontend — Cart Math

`tripPricing.js` mirrors the backend:

- `minPriceOf(item)` helper next to `priceOf` with the same localStorage hardening —
  legacy carts without `minPrice` → 0, no floor (backward compatible).
- `computeTripTotal`: standalone line = `max(priceOf(it) × travelers, minPriceOf(it))`;
  package groups sum floored lines, then apply the discount.

Trip items must carry `minPrice`: every place an activity enters the cart
(ActivityCard, ActivityDetailPage, ActivityPreviewModal, SwipeCard, CuratePage, vote
result hydration in `TripBuilder.js` from `ResultActivityDTO.minPrice`) adds the field.
The booking export payload (`TripBuilder.js` `handleSubmit`) also sends `minPrice` per
activity for email rendering.

## Frontend — Display

- **Cards/detail surfaces** (ActivityCard, ActivityDetailPage, ActivityPreviewModal,
  SwipeCard): when `minPrice > 0` —
  `from €50 per person` + secondary note `Group minimum €300`.
  New helper in `utils/format.js` (e.g. `minPriceNote(activity)`) so the condition lives
  in one place. `minPrice` empty → rendering identical to today.
- **Trip Builder line** (`TripBuilder.js` itinerary rows): today `€50 × 4 = €200`;
  when the floor binds show the floored line — `€50 × 4 = €300 (group min)` — so line
  sums reconcile with the cart total.
- Analytics `value` fields already call `computeTripTotal` → consistent automatically.

## Admin

- **Form** (`AdminActivities.js`): optional numeric field "Min price (per group)",
  validation ≥ 0, empty = no minimum; new table column next to Price.
- **CSV import/export**: `min_price` as an **optional** mutable column (like
  `featured_weight`): old sheets without the column import unchanged ("column absent →
  field untouched"); exporter emits it after `price`; when the column is present, an
  empty cell clears the minimum; validator requires a number ≥ 0.

## Testing

Backend:
- `calculateTotal`: floor binds standalone / floor does not bind / package floor before
  discount / null `minPrice` unchanged.
- `verifyChargeablePricing`: recompute with floored snapshot passes; stored total from a
  floored booking still verifies.
- DTO ↔ entity mapping of `minPrice`.
- CSV parser (optional column), row validator (≥ 0, empty clears), differ, exporter
  header, importer apply.
- `buildDestinationViews`: floor note present when binding, absent otherwise.

Frontend:
- `tripPricing.test.js`: same floor cases as backend.
- `TripBuilder.test.js`: floored line rendering + total.
- `ActivityCard.test.js`: "from" prefix + minimum note; absent when no `minPrice`.
- Admin form validation tests.

## Out of Scope

- No per-package minimum (only per-activity).
- No "minimum participants" modelling (rejected alternative — minimum is in euros).
- No backend-computed cart endpoint (cart stays client-side; duplication of the floor
  formula front/back matches the existing total duplication).
