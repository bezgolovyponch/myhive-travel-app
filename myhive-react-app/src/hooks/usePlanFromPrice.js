import {useEffect, useState} from 'react';
import pricingApi from '../services/pricingApi';

const DEBOUNCE_MS = 300;

/**
 * The Trip Builder plan's "from €X", priced on the server from the catalog for
 * these trip items and this many travellers: {fromPrice, fromPricePerPerson}.
 * Each item goes with the package it was added as part of, so the server prices
 * a package at its discount, as the booking will. Null while there is nothing to
 * price, while it loads, or when the quote fails (the plan then shows no price
 * rather than a wrong one).
 */
export function usePlanFromPrice(tripItems, travelers) {
    const [quote, setQuote] = useState(null);
    // The request body as a string: the effect's dependency, and what it sends.
    const linesKey = tripItems.length === 0 ? '' : JSON.stringify(
        tripItems.map(item => ({activityId: item.id, packageId: item.packageId || null})));

    useEffect(() => {
        if (!linesKey || !(travelers >= 1)) {
            setQuote(null);
            return undefined;
        }
        let cancelled = false;
        const id = setTimeout(() => {
            pricingApi.quote({items: JSON.parse(linesKey), travelers})
                .then(result => {
                    if (!cancelled) setQuote(result?.fromPrice != null ? result : null);
                })
                .catch(() => {
                    if (!cancelled) setQuote(null);
                });
        }, DEBOUNCE_MS);
        return () => {
            cancelled = true;
            clearTimeout(id);
        };
    }, [linesKey, travelers]);

    return quote;
}
