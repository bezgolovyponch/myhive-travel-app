# Price Formatter Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render every euro amount through the shared formatter with a single "cents only when present" rule (`€45`, `€40.50`, never `€45.5`).

**Architecture:** One change to the base `formatAmount` in `src/utils/format.js` defines the canonical money format; `formatPrice`/`formatPricePerPerson` inherit it unchanged. Eight call sites that interpolate the euro symbol raw are re-pointed at those helpers. Non-price euro symbols (labels, input adornments) are left alone.

**Tech Stack:** React 19, Create React App (react-scripts 5), Jest + React Testing Library. Run all commands from `myhive-react-app/`. Spec: `docs/superpowers/specs/2026-06-15-price-formatter-consolidation-design.md`.

**Conventions:** no wildcard imports; commit per task; run the suite with `CI=true npx react-scripts test --watchAll=false`. Avoid double quotes in commit messages (the shell here-string mangles them) — the messages below already avoid them.

---

## File Structure

- `src/utils/format.js` — Modify `formatAmount` (the only formatter change).
- `src/utils/format.test.js` — Modify pinned expectations; add a one-decimal-never case.
- `src/components/ActivityPreviewModal.js` — Import `formatPricePerPerson`; replace raw meta interpolation.
- `src/components/ActivityPreviewModal.test.js` — Update the price-meta matcher.
- `src/components/SwipeCard.js` — Import `formatPricePerPerson`; replace raw card-meta interpolation.
- `src/pages/vote/CuratePage.js` — Import `formatPricePerPerson`; replace raw finalize-card interpolation.
- `src/components/ContactForm.js` — Import `formatPrice`; replace raw estimated-total interpolation.
- `src/components/TripBuilder.js` — Replace two `× travelers` breakdown strings and the itinerary total (already imports the helpers).
- `src/components/TripBuilderDropdown.js` — Replace the modal total (already imports `formatPrice`).

---

### Task 1: "Cents only when present" in `formatAmount`

**Files:**
- Modify: `src/utils/format.js`
- Test: `src/utils/format.test.js`

- [ ] **Step 1: Update the failing tests**

In `src/utils/format.test.js`, replace the whole-number case (currently lines 10-12) and the per-person whole-number case (currently line 26), and add a one-decimal case. The `formatPrice` describe block's whole-number test becomes:

```js
    it('renders whole numbers without decimals', () => {
        expect(formatPrice(45)).toBe('€45');
    });

    it('keeps two decimals for fractional amounts and never one', () => {
        expect(formatAmount(45.5)).toBe('€45.50');
        expect(formatAmount(40.5)).toBe('€40.50');
    });
```

In the `formatPricePerPerson` describe block, change line 26 to:

```js
        expect(formatPricePerPerson(45)).toBe('€45 / person');
```

Leave unchanged: the `€12.50` delegation test (lines 4-8), the `'€120'` string passthrough (line 15), the per-person string passthrough (line 30), and both nullish-passthrough tests.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern format`
Expected: FAIL — `formatPrice(45)` returns `€45.00`, not `€45`; `formatPricePerPerson(45)` returns `€45.00 / person`.

- [ ] **Step 3: Implement the new `formatAmount`**

In `src/utils/format.js`, replace the `formatAmount` body:

```js
export function formatAmount(amount) {
    if (amount == null) return '—';
    const n = Number(amount);
    // Cents only when present: whole euros render clean (€45), fractional
    // amounts keep exactly two decimals (€40.50). Never one decimal.
    return Number.isInteger(n) ? `€${n}` : `€${n.toFixed(2)}`;
}
```

Leave `formatPrice` and `formatPricePerPerson` unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern format`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/utils/format.js src/utils/format.test.js
git commit -F - <<'EOF'
feat: cents only when present in formatAmount

Whole euros render without decimals (€45); fractional amounts keep exactly
two decimals (€40.50). One decimal (€45.5) can no longer occur. formatPrice
and formatPricePerPerson inherit the rule unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: ActivityPreviewModal per-person price

**Files:**
- Modify: `src/components/ActivityPreviewModal.js` (import + the `meta.push` for price)
- Test: `src/components/ActivityPreviewModal.test.js:25`

- [ ] **Step 1: Update the failing test**

In `src/components/ActivityPreviewModal.test.js`, change line 25 from:

```js
  expect(screen.getByText(/€45\/person/)).toBeInTheDocument();
```

to:

```js
  expect(screen.getByText(/€45 \/ person/)).toBeInTheDocument();
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern ActivityPreviewModal`
Expected: FAIL — the component still renders `€45/person` (no spaces), so the new matcher does not match.

- [ ] **Step 3: Route the price through the formatter**

In `src/components/ActivityPreviewModal.js`, add the import after the existing AppModal import (line 1):

```js
import { formatPricePerPerson } from '../utils/format';
```

Then change the price meta push (currently):

```js
    if (activity.price != null) {
        meta.push(`€${activity.price}/person`);
    }
```

to:

```js
    if (activity.price != null) {
        meta.push(formatPricePerPerson(activity.price));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern ActivityPreviewModal`
Expected: PASS (all 9 tests in the file).

- [ ] **Step 5: Commit**

```bash
git add src/components/ActivityPreviewModal.js src/components/ActivityPreviewModal.test.js
git commit -F - <<'EOF'
refactor: ActivityPreviewModal price via formatPricePerPerson

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: Route the remaining display sites through the formatter

These are mechanical swaps from raw euro interpolation to the shared helpers. The formatting logic is already unit-tested in Task 1; none of these sites has a test that pins a price string (verified: SwipeCard.test.js, CuratePage.test.js, ContactForm has no price assertion, TripBuilder/TripBuilderDropdown have none). Verification is the full suite staying green plus a clean lint build.

**Files:**
- Modify: `src/components/SwipeCard.js`
- Modify: `src/pages/vote/CuratePage.js`
- Modify: `src/components/ContactForm.js`
- Modify: `src/components/TripBuilder.js`
- Modify: `src/components/TripBuilderDropdown.js`

- [ ] **Step 1: SwipeCard card-meta price**

In `src/components/SwipeCard.js`, add the import after the existing `copyToClipboard` import:

```js
import { formatPricePerPerson } from '../utils/format';
```

Change the card-meta price (currently):

```js
                                        {card.price && <span>€{card.price}/person</span>}
```

to:

```js
                                        {card.price && <span>{formatPricePerPerson(card.price)}</span>}
```

- [ ] **Step 2: CuratePage finalize-card price**

In `src/pages/vote/CuratePage.js`, add the import after the existing `ActivityPreviewModal` import (it sits with the other `../../` imports):

```js
import { formatPricePerPerson } from '../../utils/format';
```

Change the finalize-card price (currently line 204):

```js
                  <div className="curate-finalize-card-price">€{a.price}/person</div>
```

to:

```js
                  <div className="curate-finalize-card-price">{formatPricePerPerson(a.price)}</div>
```

- [ ] **Step 3: ContactForm estimated total**

In `src/components/ContactForm.js`, add the import after the existing `computeTripTotal` import:

```js
import { formatPrice } from '../utils/format';
```

Change the estimated-total line (currently):

```js
                        <p><strong>Estimated Total:</strong> €{computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1).toFixed(2)}</p>
```

to:

```js
                        <p><strong>Estimated Total:</strong> {formatPrice(computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1))}</p>
```

- [ ] **Step 4: TripBuilder breakdowns and total**

`src/components/TripBuilder.js` already imports `formatPrice` and `formatPricePerPerson`. Make three edits.

The package-group breakdown (currently line 268):

```js
                                ? `€${item.price} × ${travelers} = €${item.price * travelers}`
```

becomes:

```js
                                ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
```

The standalone-item breakdown (currently line 285) — identical change:

```js
                          ? `€${item.price} × ${travelers} = €${item.price * travelers}`
```

becomes:

```js
                          ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
```

The itinerary total (currently line 326):

```js
                <span className="itinerary-total-price">€{totalPrice}</span>
```

becomes:

```js
                <span className="itinerary-total-price">{formatPrice(totalPrice)}</span>
```

- [ ] **Step 5: TripBuilderDropdown modal total**

`src/components/TripBuilderDropdown.js` already imports `formatPrice`. Change the modal total (currently line 88):

```js
                            <span className="trip-modal-total-price">€{totalPrice}</span>
```

to:

```js
                            <span className="trip-modal-total-price">{formatPrice(totalPrice)}</span>
```

- [ ] **Step 6: Run the full suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS — all suites green (no test pins the old raw shape at these sites).

- [ ] **Step 7: Build to confirm no eslint warnings (unused imports etc.)**

Run: `CI=true npm run build`
Expected: `Compiled successfully.` with no eslint warnings.

- [ ] **Step 8: Commit**

```bash
git add src/components/SwipeCard.js src/pages/vote/CuratePage.js src/components/ContactForm.js src/components/TripBuilder.js src/components/TripBuilderDropdown.js
git commit -F - <<'EOF'
refactor: route remaining euro displays through the shared formatter

SwipeCard, CuratePage finalize card, ContactForm estimated total, the
TripBuilder per-line breakdowns and itinerary total, and the
TripBuilderDropdown modal total now use formatPrice/formatPricePerPerson
instead of raw euro interpolation, so decimals match everywhere.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Final verification

- [ ] Full suite green: `CI=true npx react-scripts test --watchAll=false`.
- [ ] Clean build: `CI=true npm run build` → `Compiled successfully.`, no eslint warnings.
- [ ] Grep `src/` for the euro symbol immediately followed by a JS interpolation (`€${` in template literals and `€{` in JSX) and confirm the only matches left are the non-price ones: the `TripSetupModal` budget label/adornment and the `AdminActivities` "Price per person (€)" form label.
- [ ] Multi-angle code review of the branch diff; fix findings.

## Out of scope (carry-over note)

Legacy string prices in the `× travelers` breakdown (`TripBuilder.js`): if `item.price` is a legacy string like `"€120"`, `item.price * travelers` is `NaN`. Pre-existing latent bug; in practice trip-item prices are numeric. Not fixed here. The other three Phase-4 sub-projects (admin inline validation, AppContext split, CRA → Vite) each get their own spec and plan.
