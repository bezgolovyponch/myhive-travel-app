# DateRangePicker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace native `<input type="date">` fields in `TripSetupModal` and `ContactForm` with a single reusable Airbnb-style `DateRangePicker` component.

**Architecture:** New controlled component `DateRangePicker` wraps `react-day-picker` v9 in range mode. It renders two Airbnb-style field boxes (Начало/Конец) above an inline dual-month calendar and a night-count footer. `TripSetupModal` and `ContactForm` each replace their two `<input type="date">` fields with one `<DateRangePicker>`.

**Tech Stack:** React 19, react-day-picker v9 (range mode), custom CSS (no default DayPicker stylesheet), @testing-library/react + jest.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/components/DateRangePicker.js` | Component: field boxes, DayPicker wrapper, footer |
| Create | `src/components/DateRangePicker.css` | All visual styles — no DayPicker default stylesheet |
| Create | `src/components/DateRangePicker.test.js` | Unit tests |
| Modify | `src/components/TripSetupModal.js` | Replace two date inputs |
| Modify | `src/components/ContactForm.js` | Replace two date inputs, rewire error display |
| Modify | `myhive-react-app/package.json` | Add react-day-picker |

---

## Task 1: Install react-day-picker

**Files:**
- Modify: `myhive-react-app/package.json`

- [ ] **Step 1: Install the package**

```bash
cd myhive-react-app
npm install react-day-picker
```

Expected: package installs without errors. Any peer-dependency warnings about React 19 can be ignored — react-day-picker v9 supports React 18+.

- [ ] **Step 2: Verify**

```bash
grep react-day-picker package.json
```

Expected output contains: `"react-day-picker": "^9.x.x"`

- [ ] **Step 3: Commit**

```bash
cd ..
git add myhive-react-app/package.json myhive-react-app/package-lock.json
git commit -m "chore: add react-day-picker"
```

---

## Task 2: Write failing tests

**Files:**
- Create: `myhive-react-app/src/components/DateRangePicker.test.js`

- [ ] **Step 1: Create the test file**

Create `myhive-react-app/src/components/DateRangePicker.test.js`:

```jsx
import { render, screen, fireEvent } from '@testing-library/react';
import DateRangePicker from './DateRangePicker';

describe('DateRangePicker', () => {
  describe('date fields display', () => {
    it('shows placeholder in both fields when no dates selected', () => {
      render(<DateRangePicker from="" to="" onChange={() => {}} />);
      expect(screen.getAllByText('Добавить дату')).toHaveLength(2);
    });

    it('hides from placeholder when from is set', () => {
      render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryAllByText('Добавить дату')).toHaveLength(1);
    });

    it('shows night count footer when both dates are set', () => {
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      expect(screen.getByText(/3 ноч/)).toBeInTheDocument();
    });

    it('hides footer when only from is set', () => {
      render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryByText(/ноч/)).not.toBeInTheDocument();
    });
  });

  describe('active field state', () => {
    it('marks from field active when no dates selected', () => {
      const { container } = render(<DateRangePicker from="" to="" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).toHaveClass('drp-field--active');
      expect(fields[1]).not.toHaveClass('drp-field--active');
    });

    it('marks to field active when only from is set', () => {
      const { container } = render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).not.toHaveClass('drp-field--active');
      expect(fields[1]).toHaveClass('drp-field--active');
    });

    it('marks no field active when both dates are set', () => {
      const { container } = render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).not.toHaveClass('drp-field--active');
      expect(fields[1]).not.toHaveClass('drp-field--active');
    });
  });

  describe('clear buttons', () => {
    it('calls onChange("","") when from clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: 'Очистить начало' }));
      expect(onChange).toHaveBeenCalledWith('', '');
    });

    it('calls onChange(from,"") when to clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: 'Очистить конец' }));
      expect(onChange).toHaveBeenCalledWith('2026-06-12', '');
    });

    it('calls onChange("","") when footer clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: /Очистить даты/i }));
      expect(onChange).toHaveBeenCalledWith('', '');
    });
  });
});
```

- [ ] **Step 2: Run — verify all tests FAIL**

```bash
cd myhive-react-app
npm test -- --testPathPattern=DateRangePicker --watchAll=false
```

Expected: all 9 tests fail with `Cannot find module './DateRangePicker'`.

---

## Task 3: Implement DateRangePicker

**Files:**
- Create: `myhive-react-app/src/components/DateRangePicker.js`
- Create: `myhive-react-app/src/components/DateRangePicker.css`

- [ ] **Step 1: Create the component**

Create `myhive-react-app/src/components/DateRangePicker.js`:

```jsx
import { DayPicker } from 'react-day-picker';
import './DateRangePicker.css';

function toDate(iso) {
  return iso ? new Date(iso + 'T00:00:00') : undefined;
}

function toISO(date) {
  if (!date) return '';
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatField(date) {
  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
}

function formatShort(date) {
  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' });
}

function nightsLabel(n) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} ночь`;
  if ([2, 3, 4].includes(mod10) && ![12, 13, 14].includes(mod100)) return `${n} ночи`;
  return `${n} ночей`;
}

const CAL_CLASSES = {
  root: 'drp-cal',
  months: 'drp-months',
  month: 'drp-month',
  month_caption: 'drp-month-caption',
  caption_label: 'drp-month-name',
  nav: 'drp-nav',
  button_previous: 'drp-nav-prev',
  button_next: 'drp-nav-next',
  month_grid: 'drp-month-grid',
  weekdays: 'drp-weekdays',
  weekday: 'drp-weekday',
  weeks: 'drp-weeks',
  week: 'drp-week',
  day: 'drp-day',
  day_button: 'drp-day-btn',
  range_start: 'drp-range-start',
  range_end: 'drp-range-end',
  range_middle: 'drp-range-middle',
  selected: 'drp-selected',
  disabled: 'drp-disabled',
  outside: 'drp-outside',
  today: 'drp-today',
  hidden: 'drp-hidden',
};

function DateRangePicker({ from, to, onChange }) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const fromDate = toDate(from);
  const toDate = toDate(to);

  const activeField = !from ? 'from' : !to ? 'to' : null;

  const nightCount = fromDate && toDate
    ? Math.round((toDate - fromDate) / (1000 * 60 * 60 * 24))
    : 0;

  const handleSelect = (range) => {
    onChange(toISO(range?.from), toISO(range?.to));
  };

  return (
    <div className="drp">
      <div className="drp-fields">
        <div className={`drp-field${activeField === 'from' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">Начало</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!from ? ' drp-field-value--empty' : ''}`}>
              {from ? formatField(fromDate) : 'Добавить дату'}
            </span>
            {from && (
              <button
                className="drp-field-clear"
                onClick={() => onChange('', '')}
                type="button"
                aria-label="Очистить начало"
              >×</button>
            )}
          </div>
        </div>
        <div className={`drp-field${activeField === 'to' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">Конец</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!to ? ' drp-field-value--empty' : ''}`}>
              {to ? formatField(toDate) : 'Добавить дату'}
            </span>
            {to && (
              <button
                className="drp-field-clear"
                onClick={() => onChange(from, '')}
                type="button"
                aria-label="Очистить конец"
              >×</button>
            )}
          </div>
        </div>
      </div>

      <div className="drp-cal-wrap">
        <DayPicker
          mode="range"
          numberOfMonths={2}
          navLayout="around"
          selected={{ from: fromDate, to: toDate }}
          onSelect={handleSelect}
          disabled={{ before: today }}
          classNames={CAL_CLASSES}
        />
      </div>

      {from && to && (
        <div className="drp-footer">
          <span className="drp-nights">
            <strong>{nightsLabel(nightCount)}</strong>
            {` · ${formatShort(fromDate)} – ${formatShort(toDate)}`}
          </span>
          <button
            className="drp-clear"
            onClick={() => onChange('', '')}
            type="button"
          >
            Очистить даты
          </button>
        </div>
      )}
    </div>
  );
}

export default DateRangePicker;
```

**Note on variable name collision:** `toDate` is used both as a function name and a variable name above. Rename the variable:

```jsx
// Replace the variable declarations and usage:
const fromDate = toDate(from);    // toDate = the helper function
const toDateObj = toDate(to);     // rename variable to toDateObj

// Then use toDateObj everywhere fromDate/toDate variables are used:
const nightCount = fromDate && toDateObj
  ? Math.round((toDateObj - fromDate) / (1000 * 60 * 60 * 24))
  : 0;
// ...
selected={{ from: fromDate, to: toDateObj }}
// ...
{to ? formatField(toDateObj) : 'Добавить дату'}
// ...
{` · ${formatShort(fromDate)} – ${formatShort(toDateObj)}`}
```

The final `DateRangePicker.js` with this fix applied in full:

```jsx
import { DayPicker } from 'react-day-picker';
import './DateRangePicker.css';

function parseDate(iso) {
  return iso ? new Date(iso + 'T00:00:00') : undefined;
}

function toISO(date) {
  if (!date) return '';
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function formatField(date) {
  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
}

function formatShort(date) {
  return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' });
}

function nightsLabel(n) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return `${n} ночь`;
  if ([2, 3, 4].includes(mod10) && ![12, 13, 14].includes(mod100)) return `${n} ночи`;
  return `${n} ночей`;
}

const CAL_CLASSES = {
  root: 'drp-cal',
  months: 'drp-months',
  month: 'drp-month',
  month_caption: 'drp-month-caption',
  caption_label: 'drp-month-name',
  nav: 'drp-nav',
  button_previous: 'drp-nav-prev',
  button_next: 'drp-nav-next',
  month_grid: 'drp-month-grid',
  weekdays: 'drp-weekdays',
  weekday: 'drp-weekday',
  weeks: 'drp-weeks',
  week: 'drp-week',
  day: 'drp-day',
  day_button: 'drp-day-btn',
  range_start: 'drp-range-start',
  range_end: 'drp-range-end',
  range_middle: 'drp-range-middle',
  selected: 'drp-selected',
  disabled: 'drp-disabled',
  outside: 'drp-outside',
  today: 'drp-today',
  hidden: 'drp-hidden',
};

function DateRangePicker({ from, to, onChange }) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const fromDate = parseDate(from);
  const toDateObj = parseDate(to);

  const activeField = !from ? 'from' : !to ? 'to' : null;

  const nightCount = fromDate && toDateObj
    ? Math.round((toDateObj - fromDate) / (1000 * 60 * 60 * 24))
    : 0;

  const handleSelect = (range) => {
    onChange(toISO(range?.from), toISO(range?.to));
  };

  return (
    <div className="drp">
      <div className="drp-fields">
        <div className={`drp-field${activeField === 'from' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">Начало</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!from ? ' drp-field-value--empty' : ''}`}>
              {from ? formatField(fromDate) : 'Добавить дату'}
            </span>
            {from && (
              <button
                className="drp-field-clear"
                onClick={() => onChange('', '')}
                type="button"
                aria-label="Очистить начало"
              >×</button>
            )}
          </div>
        </div>
        <div className={`drp-field${activeField === 'to' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">Конец</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!to ? ' drp-field-value--empty' : ''}`}>
              {to ? formatField(toDateObj) : 'Добавить дату'}
            </span>
            {to && (
              <button
                className="drp-field-clear"
                onClick={() => onChange(from, '')}
                type="button"
                aria-label="Очистить конец"
              >×</button>
            )}
          </div>
        </div>
      </div>

      <div className="drp-cal-wrap">
        <DayPicker
          mode="range"
          numberOfMonths={2}
          navLayout="around"
          selected={{ from: fromDate, to: toDateObj }}
          onSelect={handleSelect}
          disabled={{ before: today }}
          classNames={CAL_CLASSES}
        />
      </div>

      {from && to && (
        <div className="drp-footer">
          <span className="drp-nights">
            <strong>{nightsLabel(nightCount)}</strong>
            {` · ${formatShort(fromDate)} – ${formatShort(toDateObj)}`}
          </span>
          <button
            className="drp-clear"
            onClick={() => onChange('', '')}
            type="button"
          >
            Очистить даты
          </button>
        </div>
      )}
    </div>
  );
}

export default DateRangePicker;
```

- [ ] **Step 2: Create the CSS**

Create `myhive-react-app/src/components/DateRangePicker.css`:

```css
/* ── Date field boxes ── */
.drp-fields {
  display: flex;
  margin-bottom: 16px;
}

.drp-field {
  flex: 1;
  border: 1px solid #b0b0b0;
  padding: 11px 14px;
  cursor: pointer;
  position: relative;
}

.drp-field:first-child {
  border-radius: 8px 0 0 8px;
  border-right: none;
}

.drp-field:first-child::after {
  content: '';
  position: absolute;
  right: 0;
  top: 8px;
  bottom: 8px;
  width: 1px;
  background: #b0b0b0;
}

.drp-field:last-child {
  border-radius: 0 8px 8px 0;
}

.drp-field--active {
  border: 2px solid #222;
  border-radius: 8px;
  z-index: 1;
}

.drp-field--active::after {
  display: none;
}

.drp-field-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #222;
  margin-bottom: 3px;
}

.drp-field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drp-field-value {
  font-size: 14px;
  color: #222;
}

.drp-field-value--empty {
  color: #717171;
}

.drp-field-clear {
  font-size: 16px;
  color: #717171;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: none;
  background: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
  font-family: inherit;
  line-height: 1;
}

.drp-field-clear:hover {
  background: #f0f0f0;
}

/* ── Calendar wrapper ── */
.drp-cal-wrap {
  border: 1px solid #ddd;
  border-radius: 12px;
  padding: 20px 24px 16px;
}

/* ── Months layout ── */
.drp-months {
  display: flex;
  gap: 32px;
  position: relative;
}

.drp-month {
  flex: 1;
  min-width: 0;
}

/* ── Month caption ── */
.drp-month-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.drp-month-name {
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

/* ── Nav buttons ── */
.drp-nav-prev,
.drp-nav-next {
  width: 32px;
  height: 32px;
  border: 1px solid #ddd;
  border-radius: 50%;
  background: white;
  cursor: pointer;
  font-size: 18px;
  color: #222;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  flex-shrink: 0;
  font-family: inherit;
}

.drp-nav-prev:hover,
.drp-nav-next:hover {
  background: #f7f7f7;
}

/* ── Calendar grid (table) ── */
.drp-month-grid {
  width: 100%;
  border-collapse: collapse;
}

.drp-weekday {
  font-size: 12px;
  font-weight: 500;
  color: #717171;
  text-align: center;
  padding: 4px 0 10px;
  font-weight: 400;
}

/* ── Day cells ── */
.drp-day {
  height: 44px;
  padding: 0;
  position: relative;
  text-align: center;
  vertical-align: middle;
}

.drp-day-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 400;
  color: #222;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
  padding: 0;
  font-family: inherit;
}

.drp-day-btn:hover {
  outline: 1px solid #222;
  outline-offset: -1px;
}

/* ── Disabled (past) days ── */
.drp-day.drp-disabled .drp-day-btn {
  color: #b0b0b0;
  text-decoration: line-through;
  cursor: default;
  pointer-events: none;
}

.drp-day.drp-disabled .drp-day-btn:hover {
  outline: none;
}

/* ── Hidden days (outside month when showOutsideDays is false) ── */
.drp-day.drp-hidden {
  visibility: hidden;
  pointer-events: none;
}

/* ── Range highlight ── */
.drp-day.drp-range-middle {
  background: #ebebeb;
}

.drp-day.drp-range-start {
  background: linear-gradient(to right, transparent 50%, #ebebeb 50%);
}

.drp-day.drp-range-end {
  background: linear-gradient(to left, transparent 50%, #ebebeb 50%);
}

.drp-day.drp-range-start .drp-day-btn,
.drp-day.drp-range-end .drp-day-btn {
  background: #222;
  color: white;
  font-weight: 600;
}

.drp-day.drp-range-start .drp-day-btn:hover,
.drp-day.drp-range-end .drp-day-btn:hover {
  outline: none;
}

/* ── Footer ── */
.drp-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #ebebeb;
}

.drp-nights {
  font-size: 13px;
  color: #717171;
}

.drp-nights strong {
  color: #222;
}

.drp-clear {
  font-size: 13px;
  font-weight: 600;
  color: #222;
  text-decoration: underline;
  cursor: pointer;
  background: none;
  border: none;
  padding: 0;
  font-family: inherit;
}
```

- [ ] **Step 3: Run tests — verify all 9 pass**

```bash
cd myhive-react-app
npm test -- --testPathPattern=DateRangePicker --watchAll=false
```

Expected: `Tests: 9 passed, 9 total`.

If `navLayout` prop is unrecognized (console warning), it's not a test failure — fix it in Task 5 visual step.

- [ ] **Step 4: Commit**

```bash
cd ..
git add myhive-react-app/src/components/DateRangePicker.js myhive-react-app/src/components/DateRangePicker.css myhive-react-app/src/components/DateRangePicker.test.js
git commit -m "feat: add DateRangePicker component"
```

---

## Task 4: Integrate into TripSetupModal

**Files:**
- Modify: `myhive-react-app/src/components/TripSetupModal.js`

Currently lines 59–80 contain a `<div className="form-row">` with two `<input type="date">` fields. Replace the entire `form-row` div with `<DateRangePicker>`.

- [ ] **Step 1: Update TripSetupModal.js**

Add the import at the top (after the existing imports):

```jsx
import DateRangePicker from './DateRangePicker';
```

Remove lines 59–80 (the `<div className="form-row">` with both date inputs) and replace with:

```jsx
<DateRangePicker
  from={startDate}
  to={endDate}
  onChange={(from, to) => {
    setStartDate(from);
    setEndDate(to);
  }}
/>
```

Also remove this line (no longer needed):
```jsx
const today = new Date().toISOString().split('T')[0];
```

The final `TripSetupModal.js` body section should look like:

```jsx
<div className="app-modal-body">
  <p className="trip-setup-description">
    Tell us about your group so we can calculate the right price.
  </p>
  <form className="contact-form" onSubmit={e => e.preventDefault()}>
    <div className="form-group">
      <label htmlFor="tripTravelers">Number of Travelers *</label>
      <input
        type="number"
        id="tripTravelers"
        value={travelers}
        onChange={e => setTravelers(Math.max(1, parseInt(e.target.value, 10) || 1))}
        min="1"
        max="20"
      />
    </div>
    <DateRangePicker
      from={startDate}
      to={endDate}
      onChange={(from, to) => {
        setStartDate(from);
        setEndDate(to);
      }}
    />
  </form>
</div>
```

- [ ] **Step 2: Start dev server and verify visually**

```bash
cd myhive-react-app
npm start
```

1. Open http://localhost:3000
2. Add any activity to the trip — the "Set Up Your Trip" modal should open
3. Verify: dual-month calendar renders inside the modal
4. Verify: clicking a date sets it as "Начало"
5. Verify: clicking a second date sets "Конец" and shows the range highlight
6. Verify: nav arrows switch months correctly
7. Verify: "Очистить даты" button clears both fields
8. Click Confirm — verify the trip setup completes successfully

If nav arrow positioning looks wrong (arrows not at outer edges), check if `navLayout` is a valid v9 prop by looking at the browser console for warnings. If it's unrecognized, remove `navLayout="around"` from the component and add this CSS instead to position the nav outside the months:

```css
/* Add to DateRangePicker.css if navLayout is not supported */
.drp-cal { position: relative; }
.drp-nav {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  pointer-events: none;
  height: 48px;
  align-items: center;
}
.drp-nav-prev,
.drp-nav-next {
  pointer-events: all;
}
```

- [ ] **Step 3: Commit**

```bash
cd ..
git add myhive-react-app/src/components/TripSetupModal.js
git commit -m "feat: use DateRangePicker in TripSetupModal"
```

---

## Task 5: Integrate into ContactForm

**Files:**
- Modify: `myhive-react-app/src/components/ContactForm.js`

Currently lines 160–181 contain a `<div className="form-row">` with two `<input type="date">` fields and their error messages. Replace with `<DateRangePicker>`.

- [ ] **Step 1: Update ContactForm.js**

Add the import at the top (after existing imports):

```jsx
import DateRangePicker from './DateRangePicker';
```

Remove lines 160–181 (the `<div className="form-row">` with both date inputs and error spans) and replace with:

```jsx
<DateRangePicker
  from={formData.startDate}
  to={formData.endDate}
  onChange={(from, to) => {
    setFormData(prev => ({ ...prev, startDate: from, endDate: to }));
    setErrors(prev => ({ ...prev, startDate: '', endDate: '' }));
  }}
/>
{(errors.startDate || errors.endDate) && (
  <span className="error-message">{errors.startDate || errors.endDate}</span>
)}
```

The surrounding form structure is unchanged — the `DateRangePicker` replaces the `form-row` div in place.

- [ ] **Step 2: Verify visually**

1. Open http://localhost:3000
2. Add activities and proceed to "Complete Booking"
3. Verify: DateRangePicker renders inside the ContactForm modal
4. Try submitting with no dates — verify validation error appears below the calendar
5. Select dates — verify error clears
6. Complete a booking — verify dates are sent correctly

- [ ] **Step 3: Run full test suite**

```bash
cd myhive-react-app
npm test -- --watchAll=false
```

Expected: all tests pass. The existing `AppContext.test.js` tests are unaffected (they test the reducer, not the UI).

- [ ] **Step 4: Commit**

```bash
cd ..
git add myhive-react-app/src/components/ContactForm.js
git commit -m "feat: use DateRangePicker in ContactForm"
```

---

## Self-Review Checklist (completed)

- **Spec coverage:**
  - ✅ Two field boxes with НАЧАЛО/КОНЕЦ labels, active border, × clear button
  - ✅ Inline dual-month calendar via react-day-picker
  - ✅ `#EBEBEB` range highlight with gradient half-cells at start/end
  - ✅ Black circles on selected dates
  - ✅ Past dates disabled
  - ✅ Night counter footer
  - ✅ "Очистить даты" button
  - ✅ TripSetupModal integration
  - ✅ ContactForm integration + validation unchanged
  - ✅ Unit tests for all 4 spec scenarios
- **No placeholders:** All code is complete
- **Type consistency:** `from`/`to` strings (YYYY-MM-DD) used consistently across component, tests, and integration points
