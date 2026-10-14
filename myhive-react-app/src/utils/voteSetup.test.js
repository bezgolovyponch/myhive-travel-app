import {voteEntryUrlWithSetup, parseVoteSetupParams} from './voteSetup';

const destinations = [
    {id: 'd1', slug: 'prague', name: 'Prague'},
    {id: 'd2', slug: 'budapest', name: 'Budapest'},
];

const setup = {
    travelers: 8,
    startDate: '2026-09-04',
    endDate: '2026-09-06',
    destination: destinations[0],
    budget: null,
};

test('voteEntryUrlWithSetup encodes the whole setup into /vote/new', () => {
    expect(voteEntryUrlWithSetup(setup)).toBe(
        '/vote/new?travelers=8&start=2026-09-04&end=2026-09-06&dest=prague'
    );
});

test('parseVoteSetupParams round-trips what voteEntryUrlWithSetup wrote', () => {
    const url = voteEntryUrlWithSetup(setup);
    const search = url.slice(url.indexOf('?'));
    expect(parseVoteSetupParams(search, destinations)).toEqual(setup);
});

test('parseVoteSetupParams resolves the destination against the catalog', () => {
    const parsed = parseVoteSetupParams(
        '?travelers=4&start=2026-10-01&end=2026-10-03&dest=budapest',
        destinations
    );
    expect(parsed.destination).toBe(destinations[1]);
});

test('parseVoteSetupParams returns null when params are missing or incomplete', () => {
    expect(parseVoteSetupParams('', destinations)).toBeNull();
    expect(parseVoteSetupParams('?travelers=8', destinations)).toBeNull();
    expect(
        parseVoteSetupParams('?travelers=8&start=2026-09-04&dest=prague', destinations)
    ).toBeNull();
});

test('parseVoteSetupParams returns null for an unknown destination or bad travelers', () => {
    expect(
        parseVoteSetupParams('?travelers=8&start=2026-09-04&end=2026-09-06&dest=nowhere', destinations)
    ).toBeNull();
    expect(
        parseVoteSetupParams('?travelers=0&start=2026-09-04&end=2026-09-06&dest=prague', destinations)
    ).toBeNull();
    expect(
        parseVoteSetupParams('?travelers=abc&start=2026-09-04&end=2026-09-06&dest=prague', destinations)
    ).toBeNull();
});
