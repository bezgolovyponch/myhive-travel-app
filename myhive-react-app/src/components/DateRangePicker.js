import { useState, useEffect } from 'react';
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
  return date.toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric' });
}

function formatShort(date) {
  return date.toLocaleDateString('en-US', { day: 'numeric', month: 'short' });
}

function nightsLabel(n) {
  return n === 1 ? '1 night' : `${n} nights`;
}

function getTodayMidnight() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

const TODAY = getTodayMidnight();

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
  const [numMonths, setNumMonths] = useState(() => window.innerWidth >= 640 ? 2 : 1);

  useEffect(() => {
    const handler = () => setNumMonths(window.innerWidth >= 640 ? 2 : 1);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  const fromDate = parseDate(from);
  const toDateObj = parseDate(to);

  const activeField = !from ? 'from' : !to ? 'to' : null;

  const nightCount = fromDate && toDateObj
    ? Math.round((toDateObj - fromDate) / (1000 * 60 * 60 * 24))
    : 0;

  const handleSelect = (range) => {
    const newFrom = toISO(range?.from);
    const newTo = toISO(range?.to);
    if (newFrom && newTo && newFrom === newTo) {
      onChange(newFrom, '');
      return;
    }
    onChange(newFrom, newTo);
  };

  return (
    <div className="drp">
      <div className="drp-fields">
        <div className={`drp-field${activeField === 'from' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">Start</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!from ? ' drp-field-value--empty' : ''}`}>
              {from ? formatField(fromDate) : 'Add date'}
            </span>
            {from && (
              <button
                className="drp-field-clear"
                onClick={() => onChange('', '')}
                type="button"
                aria-label="Clear start date"
              >×</button>
            )}
          </div>
        </div>
        <div className={`drp-field${activeField === 'to' ? ' drp-field--active' : ''}`}>
          <div className="drp-field-label">End</div>
          <div className="drp-field-row">
            <span className={`drp-field-value${!to ? ' drp-field-value--empty' : ''}`}>
              {to ? formatField(toDateObj) : 'Add date'}
            </span>
            {to && (
              <button
                className="drp-field-clear"
                onClick={() => onChange(from, '')}
                type="button"
                aria-label="Clear end date"
              >×</button>
            )}
          </div>
        </div>
      </div>

      <div className="drp-cal-wrap">
        <DayPicker
          mode="range"
          numberOfMonths={numMonths}
          selected={{ from: fromDate, to: toDateObj }}
          onSelect={handleSelect}
          disabled={{ before: TODAY }}
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
            Clear dates
          </button>
        </div>
      )}
    </div>
  );
}

export default DateRangePicker;
