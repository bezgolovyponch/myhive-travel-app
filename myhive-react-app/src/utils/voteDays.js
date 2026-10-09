import {addDays, formatDayLabel, parseISODate} from './format';

/** "Fri 16 Oct" when the trip's first day is known, else the caller's "Day 1". */
export function dayLabel(dayNumber, startDate, dayWord) {
    const start = parseISODate(startDate);
    return start ? formatDayLabel(addDays(start, dayNumber - 1)) : dayWord(dayNumber);
}

/**
 * A vote's activities day by day, as the planner laid them out: [{dayNumber, label, rows}], days in
 * order, then one group without a day (dayNumber null) for what was added later. Null when no activity
 * has a day - a vote started from the cart or the quiz is one plain list, as before.
 *
 * @param rows     the activities
 * @param dayOf    (row) => its dayNumber, or null
 * @param dayWord  (n) => "Day n", used when the trip has no start date
 */
export function groupByDay(rows, dayOf, startDate, dayWord) {
    if (!rows.some(row => dayOf(row) != null)) return null;
    const days = new Map();
    const undated = [];
    rows.forEach(row => {
        const day = dayOf(row);
        if (day == null) undated.push(row);
        else days.set(day, [...(days.get(day) || []), row]);
    });
    const groups = [...days.keys()].sort((a, b) => a - b)
        .map(day => ({dayNumber: day, label: dayLabel(day, startDate, dayWord), rows: days.get(day)}));
    if (undated.length > 0) groups.push({dayNumber: null, label: null, rows: undated});
    return groups;
}
