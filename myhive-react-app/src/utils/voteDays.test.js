import {dayLabel, groupByDay} from './voteDays';

const dayWord = (n) => `Day ${n}`;
const dayOf = (row) => row.dayNumber ?? null;

test('activities are grouped by day in order, with what has no day last', () => {
    const rows = [
        {id: 'club', dayNumber: 2}, {id: 'brunch'}, {id: 'karting', dayNumber: 1}, {id: 'spa', dayNumber: 2},
    ];

    const groups = groupByDay(rows, dayOf, '2026-10-16', dayWord);

    expect(groups.map((g) => [g.dayNumber, g.rows.map((r) => r.id)]))
        .toEqual([[1, ['karting']], [2, ['club', 'spa']], [null, ['brunch']]]);
    expect(groups[0].label).toMatch(/16/);
    expect(groups[1].label).toMatch(/17/);
    expect(groups[2].label).toBeNull();
});

test('a vote without a day plan stays one plain list', () => {
    expect(groupByDay([{id: 'a'}, {id: 'b'}], dayOf, '2026-10-16', dayWord)).toBeNull();
});

test('without a start date a day is "Day n"', () => {
    expect(dayLabel(2, null, dayWord)).toBe('Day 2');
});
