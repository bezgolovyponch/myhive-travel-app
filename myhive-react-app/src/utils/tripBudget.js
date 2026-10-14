// The trip's budget is kept as the group total, the figure the vote session, the lead and the backend
// work with; tripBudgetBasis remembers how the organizer put it ("per person" or "for the group") so the
// form and the planner can say it back the same way. Carts saved before the basis existed held a total.
export const PER_PERSON = 'PER_PERSON';
export const TOTAL = 'TOTAL';

export function budgetBasisOf(state) {
    return state.tripBudgetBasis === PER_PERSON ? PER_PERSON : TOTAL;
}

/** One traveller's share of the budget, in whole euros (rounded down); null without a budget. */
export function budgetPerPerson(state) {
    const total = Number(state.tripBudget);
    if (state.tripBudget == null || !(total > 0)) {
        return null;
    }
    return Math.floor(total / Math.max(1, Number(state.tripTravelers) || 1));
}

/** The budget as the organizer entered it: per person for PER_PERSON, the group total otherwise. */
export function budgetAsEntered(state) {
    if (state.tripBudget == null || !(Number(state.tripBudget) > 0)) {
        return null;
    }
    return budgetBasisOf(state) === PER_PERSON ? budgetPerPerson(state) : Number(state.tripBudget);
}

/** What the AI planner is handed: {amount, basis}, or null without a budget. */
export function budgetLimitOf(state) {
    const amount = budgetAsEntered(state);
    return amount == null ? null : {amount, basis: budgetBasisOf(state)};
}

/** The group total for an amount the form took: per person times the head-count, or the amount itself. */
export function totalFor(amount, basis, travelers) {
    const n = Number(amount);
    if (!(n > 0)) {
        return null;
    }
    return basis === PER_PERSON ? n * Math.max(1, Number(travelers) || 1) : n;
}
