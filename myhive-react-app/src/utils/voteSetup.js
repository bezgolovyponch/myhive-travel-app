// The confirmed vote setup has to survive a full page load: the SSR pages
// (homepage, landings) open the travelers/dates modal in place, but the quiz
// lives in the SPA, so the confirm crosses a document boundary — and
// react-router location state dies there. These helpers carry the setup through
// /vote/new's query string instead; VoteEntryPage turns it back into the
// location state the quiz flow runs on. Inside the SPA the same URL is a
// client-side hop and the state arrives as well, so nothing is re-derived.

export function voteEntryUrlWithSetup({travelers, startDate, endDate, destination, budget, budgetBasis}) {
    const params = new URLSearchParams({
        travelers: String(travelers),
        start: startDate,
        end: endDate,
        dest: destination.slug,
    });
    // The budget is optional: the group total, and how it was put.
    if (Number(budget) > 0) {
        params.set('budget', String(budget));
        if (budgetBasis) params.set('basis', budgetBasis);
    }
    return `/vote/new?${params}`;
}

// The setup encoded in a /vote/new query string, with the destination resolved
// against the loaded catalog — or null when the params are absent, incomplete,
// or name an unknown destination (the caller falls back to opening the modal).
export function parseVoteSetupParams(search, destinations) {
    const params = new URLSearchParams(search);
    const travelers = parseInt(params.get('travelers'), 10);
    const startDate = params.get('start');
    const endDate = params.get('end');
    const destSlug = params.get('dest');
    if (!travelers || travelers < 1 || !startDate || !endDate || !destSlug) {
        return null;
    }
    const destination = (destinations || []).find((d) => d.slug === destSlug);
    if (!destination) {
        return null;
    }
    const budget = Number(params.get('budget'));
    const basis = params.get('basis');
    return {travelers, startDate, endDate, destination,
        budget: budget > 0 ? budget : null,
        budgetBasis: budget > 0 && (basis === 'PER_PERSON' || basis === 'TOTAL') ? basis : null};
}
