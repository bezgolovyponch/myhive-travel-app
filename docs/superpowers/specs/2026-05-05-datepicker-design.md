# DateRangePicker — Design Spec
_Date: 2026-05-05_

## Goal

Replace the native `<input type="date">` fields in `TripSetupModal` and `ContactForm` with an Airbnb-style inline dual-month range calendar.

## Visual Design

Exact reference: Airbnb listing page datepicker (airbnb.com/rooms/*).

### Date fields (top)
- Two side-by-side boxes sharing a thin gray border (`#b0b0b0`), split by a vertical divider
- Left box: label **НАЧАЛО** (10px, bold, uppercase) + date value below + `×` clear button
- Right box: label **КОНЕЦ** + date value + `×` clear button
- Active/focused box: 2px solid black border, `border-radius: 8px`, z-index raised
- Empty state value color: `#717171`

### Calendar
- Two months side by side, navigation arrows on outer edges only
- Day names: S M T W T F S, 12px gray
- Selected start/end: filled black circle (`#222`), white bold text
- Range highlight: `#EBEBEB` background strip, full cell height (no radius on middle cells)
  - Strip starts at right-half of start cell, ends at left-half of end cell
  - Month boundary: strip ends/starts at cell midpoint
- Past dates: `text-decoration: line-through`, gray, not clickable
- Hover on unselected day: thin black circle border

### Footer
- Left: "**N ночей** · DD MMM – DD MMM"
- Right: "Очистить даты" (underlined, bold)

## Component API

```jsx
// myhive-react-app/src/components/DateRangePicker.js
<DateRangePicker
  from={string}          // YYYY-MM-DD or ''
  to={string}            // YYYY-MM-DD or ''
  onChange={(from, to) => void}
/>
```

Fully controlled — no internal date state. Parent owns `from` and `to`.

## Library

`react-day-picker` v9 with `mode="range"`.

- `numberOfMonths={2}` for dual-month view
- `disabled={{ before: today }}` to block past dates
- Custom CSS class modifiers for range start/end/middle styling
- No default stylesheet imported — fully custom CSS

## Integration Points

### TripSetupModal.js
- Replace the two `<input type="date">` in the `form-row` div
- `startDate` / `endDate` state unchanged, passed as `from` / `to`
- `onChange` updates both state values

### ContactForm.js
- Replace the two `<input type="date">` fields in the second `form-row`
- `formData.startDate` / `formData.endDate` unchanged
- Validation logic (`validateForm`) unchanged — still checks for empty strings and end > start
- `onChange` calls `setFormData` updating both fields at once; clears both error keys

## Files Changed

| File | Change |
|------|--------|
| `src/components/DateRangePicker.js` | New — the component |
| `src/components/DateRangePicker.css` | New — all custom styles |
| `src/components/TripSetupModal.js` | Replace two date inputs |
| `src/components/ContactForm.js` | Replace two date inputs |
| `package.json` | Add `react-day-picker` |

## Tests

New file: `src/components/DateRangePicker.test.js`

1. Renders with no dates selected — both boxes show empty placeholder
2. Selecting a start date — `onChange` called with `(date, '')`, start box shows date
3. Selecting a full range — `onChange` called with `(start, end)`, night counter correct
4. Clear button — `onChange` called with `('', '')`

## Out of Scope

- Flexible dates toggle (±1 day, ±2 days, etc.)
- Mobile single-month view
- Keyboard navigation beyond library defaults
