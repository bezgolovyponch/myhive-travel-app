# Price Formatter Consolidation — Design

**Date:** 2026-06-15
**Phase:** 4 (sub-project 1 of 4)
**Status:** Approved, ready for implementation plan

## Context

Phase 4 of the frontend cleanup is four independent sub-projects, to be done one
at a time (separate spec + plan each), in this order:

1. **Price formatter consolidation** ← *this spec*
2. Admin inline validation
3. AppContext split
4. CRA → Vite migration (last, as a clean infra swap against a green test suite)

This spec covers only sub-project 1.

## Problem

The app renders euro amounts two different ways, so the same price looks
different across views:

- The shared formatter `src/utils/format.js` (`formatPrice` → `formatAmount`)
  renders numbers with **two decimals** (`€45.00`).
- Eight call sites bypass the formatter and interpolate the euro symbol
  raw (`€${activity.price}/person`, `€{totalPrice}`, inline `.toFixed(2)`),
  which renders **no decimals** (`€45`) or, for the price `12.5`, the
  inconsistent one-decimal `€12.5`.

Result: catalog prices, per-person prices, itinerary totals and the booking
estimate disagree on decimal formatting.

Additionally, forcing `.00` onto whole-euro catalog prices (the common case
here) is visual noise — `€45.00` reads worse than `€45`.

## Goal

One canonical money format, applied through the single shared formatter:

- **Whole amounts → no decimals** (`€45`, `€102`).
- **Fractional amounts → exactly two decimals** (`€40.50`, `€127.50`).
- **Never one decimal** (`€45.5` must not occur).

A price renders identically wherever it appears, and discounted/fractional
totals keep their cents (we never round a payable total).

## Design

### 1. Change the base formatter (the only formatter change)

`src/utils/format.js` — `formatAmount`:

```js
export function formatAmount(amount) {
    if (amount == null) return '—';
    const n = Number(amount);
    // Cents only when present: whole euros render clean (€45), fractional
    // amounts keep exactly two decimals (€40.50). Never one decimal.
    return Number.isInteger(n) ? `€${n}` : `€${n.toFixed(2)}`;
}
```

- `formatPrice` is unchanged: numbers delegate to `formatAmount`; legacy string
  prices (e.g. `"€120"` from localStorage carts) and nullish values pass through
  untouched.
- `formatPricePerPerson` is unchanged: it appends ` / person` to `formatPrice`.

Because every numeric price already flows (or will flow, see §2) through
`formatAmount`, this single change makes the whole app consistent.

### 2. Route the eight raw interpolations through the formatter

| File:line | Current | Becomes |
|-----------|---------|---------|
| `ActivityPreviewModal.js:11` | `` `€${activity.price}/person` `` | `formatPricePerPerson(activity.price)` |
| `SwipeCard.js:130` | `` <span>€{card.price}/person</span> `` | `<span>{formatPricePerPerson(card.price)}</span>` |
| `CuratePage.js:204` | `` <div …>€{a.price}/person</div> `` | `<div …>{formatPricePerPerson(a.price)}</div>` |
| `ContactForm.js:119` | `` €{computeTripTotal(...).toFixed(2)} `` | `{formatPrice(computeTripTotal(...))}` |
| `TripBuilder.js:326` | `` <span …>€{totalPrice}</span> `` | `<span …>{formatPrice(totalPrice)}</span>` |
| `TripBuilderDropdown.js:88` | `` <span …>€{totalPrice}</span> `` | `<span …>{formatPrice(totalPrice)}</span>` |
| `TripBuilder.js:268` | `` `€${item.price} × ${travelers} = €${item.price * travelers}` `` | `` `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}` `` |
| `TripBuilder.js:285` | (same shape as 268) | (same) |

Each file imports the needed helpers from `../utils/format`
(`ContactForm` and the two `TripBuilder` line-item breakdowns need `formatPrice`;
the per-person sites need `formatPricePerPerson`). Line numbers are from the
2026-06-15 audit and will be re-confirmed against the working tree during
implementation.

### 3. Leave non-price euro symbols alone

These are not amount displays and must not change:

- `TripSetupModal.js:171` — label text `Group budget (€, optional)`.
- `TripSetupModal.js:177` — `€` input adornment (absolute-positioned span).
- `AdminActivities.js` — form label `Price per person (€)`.

## Testing (TDD)

Update each pinned expectation to the new format **before** changing source, watch
it fail, then implement.

- `src/utils/format.test.js`
  - `formatPrice(45)` → `€45` (was `€45.00`); rename the "two decimals" whole-number
    case to a "no decimals for whole amounts" case.
  - Fractional cases stay: `formatPrice(12.5)` → `€12.50`; the delegation assertion
    `formatPrice(12.5) === formatAmount(12.5)` still holds.
  - `formatPricePerPerson(45)` → `€45 / person` (was `€45.00 / person`); string
    passthrough `formatPricePerPerson('€120')` → `€120 / person` and nullish
    passthrough stay.
  - Add a case asserting one-decimal input never renders one decimal:
    `formatAmount(45.5)` → `€45.50`.
- `src/components/ActivityPreviewModal.test.js` — the price-meta matcher
  `/€45\/person/` becomes `/€45 \/ person/`.
- `src/components/SwipeCard.test.js`, `src/pages/vote/CuratePage.test.js` —
  confirm whether they assert on a price string; update if so, otherwise no change.
- Full suite green; `npm run build` clean (no eslint warnings).

## Out of scope

- **Legacy string prices in the `× travelers` breakdown** (`TripBuilder.js:268/285`):
  if `item.price` is a legacy string like `"€120"`, `item.price * travelers` is
  `NaN`. This is a pre-existing latent bug; in practice trip-item prices are
  numeric. We do not fix it here — this sub-project is strictly about decimal
  consistency. (`computeTripTotal` already parses legacy strings for totals; the
  inline per-line breakdown does not, and that gap predates this work.)
- The other three Phase-4 sub-projects (admin validation, AppContext split,
  CRA → Vite) — each gets its own spec and plan.

## Risks

- **Low.** One formatter function changes; eight display sites are re-pointed at
  it; a handful of test expectations update. No data, API, or behavior changes
  beyond rendered decimal formatting. The `formatAmount` change is covered by
  unit tests and is the single source of truth, so regressions surface in
  `format.test.js`.
