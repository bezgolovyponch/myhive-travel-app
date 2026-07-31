import { useState, useEffect, useCallback, useLayoutEffect, useRef } from 'react';
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

function DateRangePicker({ from, to, onChange, collapsible = false, popover = false }) {
  const [numMonths, setNumMonths] = useState(() => window.innerWidth >= 640 ? 2 : 1);
  const [hoveredDay, setHoveredDay] = useState(null);
  // Collapsible mode (inline booking panel): the calendar folds away once the range is
  // complete so the panel fits without scrolling; clicking a field brings it back.
  const [reopened, setReopened] = useState(false);
  // Popover mode: the calendar is hidden until a field is clicked, then shown in an
  // absolutely-positioned dropdown; it closes on range completion or outside click.
  const [popOpen, setPopOpen] = useState(false);
  // Fixed-position anchor for the popover: stuck to the fields, opening
  // upward (or downward when there is no room above).
  const [popPos, setPopPos] = useState(null);
  const calWrapRef = useRef(null);
  const rootRef = useRef(null);
  const fieldsRef = useRef(null);
  const popRef = useRef(null);

  useLayoutEffect(() => {
    if (!popover || !popOpen || !fieldsRef.current) return;
    const rect = fieldsRef.current.getBoundingClientRect();
    const width = Math.min(312, window.innerWidth - 32);
    const left = Math.min(Math.max(16, rect.left), window.innerWidth - width - 16);
    const openUp = rect.top > 340; // enough room above for the compact calendar
    setPopPos(openUp
      ? { left, top: rect.top - 8, transform: 'translateY(-100%)' }
      : { left, top: rect.bottom + 8 });
  }, [popover, popOpen]);

  // A field click unfolds the calendar below the fold of its scroll container
  // (panel body on desktop, page on mobile) — bring the whole calendar into view.
  // Only for click-opens: an initially visible calendar must not steal the scroll.
  useEffect(() => {
    if (reopened) {
      calWrapRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [reopened]);

  useEffect(() => {
    const handler = () => setNumMonths(window.innerWidth >= 640 ? 2 : 1);
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  // Popover mode: clicking anywhere outside the component closes the dropdown.
  // The calendar renders in a position:fixed .drp-pop that is a DOM child of the
  // root, but react-day-picker re-renders the day grid on pointer-down, so the
  // clicked <button> is detached before a plain rootRef.contains(e.target) runs
  // — that false negative was closing the popover before the day's click could
  // fire onSelect, so Start never filled. composedPath() snapshots the event's
  // ancestor chain at dispatch time, immune to that re-render race.
  useEffect(() => {
    if (!popover || !popOpen) return undefined;
    const onDocMouseDown = (e) => {
      // composedPath() over contains(e.target): react-day-picker re-renders the
      // day grid on mouse-down, detaching the clicked <button> before this runs,
      // so contains(e.target) returns a false negative and closes the popover
      // before the day's click can fire onSelect. The path is captured at
      // dispatch time and includes .drp-pop even after the re-render.
      const path = typeof e.composedPath === 'function' ? e.composedPath() : [e.target];
      const insideRoot = rootRef.current && path.includes(rootRef.current);
      const insidePop = popRef.current && path.includes(popRef.current);
      if (!insideRoot && !insidePop) {
        setPopOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocMouseDown);
    return () => document.removeEventListener('mousedown', onDocMouseDown);
  }, [popover, popOpen]);

  const fromDate = parseDate(from);
  const toDateObj = parseDate(to);

  const activeField = !from ? 'from' : !to ? 'to' : null;

  const calendarVisible = popover
    ? popOpen
    : (!collapsible || !(from && to) || reopened);

  const handleFieldClick = () => {
    if (popover) {
      setPopOpen(true);
      return;
    }
    if (collapsible) {
      setReopened(true);
    }
  };

  const handleSelect = (range) => {
    const newFrom = toISO(range?.from);
    const newTo = toISO(range?.to);
    if (newFrom && newTo && newFrom === newTo) {
      onChange(newFrom, '');
      return;
    }
    if (newFrom && newTo) {
      setReopened(false); // range complete — fold the calendar away again (collapsible mode)
      setPopOpen(false); // popover mode: range complete — close the popup
    }
    onChange(newFrom, newTo);
  };

  const showPreview = from && !to && hoveredDay && fromDate && hoveredDay > fromDate;
  const hoverModifiers = showPreview ? {
    hoverStart: fromDate,
    hoverMiddle: { after: fromDate, before: hoveredDay },
    hoverEnd: hoveredDay,
  } : {};
  const hoverModifierClassNames = showPreview ? {
    hoverStart: 'drp-hover-start',
    hoverMiddle: 'drp-hover-middle',
    hoverEnd: 'drp-hover-end',
  } : {};

  const handleDayMouseEnter = useCallback((day) => {
    if (from && !to) setHoveredDay(day);
  }, [from, to]);

  const handleDayMouseLeave = useCallback(() => {
    setHoveredDay(null);
  }, []);

  return (
    <div className={`drp${popover ? ' drp--popover' : ''}`} ref={rootRef}>
      <div className="drp-fields" ref={fieldsRef}>
        <div className={`drp-field${activeField === 'from' ? ' drp-field--active' : ''}`}
             onClick={handleFieldClick}>
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
        <div className={`drp-field${activeField === 'to' ? ' drp-field--active' : ''}`}
             onClick={handleFieldClick}>
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

      {calendarVisible && (
        <div className={popover ? 'drp-pop' : undefined} style={popover ? popPos : undefined} ref={popover ? popRef : undefined}>
          <div className="drp-cal-wrap" ref={calWrapRef}>
            <DayPicker
              mode="range"
              numberOfMonths={popover ? 1 : numMonths}
              selected={{ from: fromDate, to: toDateObj }}
              onSelect={handleSelect}
              onDayMouseEnter={handleDayMouseEnter}
              onDayMouseLeave={handleDayMouseLeave}
              modifiers={hoverModifiers}
              modifiersClassNames={hoverModifierClassNames}
              disabled={{ before: TODAY }}
              classNames={CAL_CLASSES}
            />
          </div>
        </div>
      )}

    </div>
  );
}

export default DateRangePicker;
