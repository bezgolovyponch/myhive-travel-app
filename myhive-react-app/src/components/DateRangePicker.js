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
