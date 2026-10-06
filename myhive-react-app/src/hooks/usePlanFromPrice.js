import {useEffect, useState} from 'react';
import pricingApi from '../services/pricingApi';

const DEBOUNCE_MS = 300;

/**
 * The Trip Builder plan's "from €X", priced on the server from the catalog for
 * these activities and this many travellers: {fromPrice, fromPricePerPerson}.
 * Null while there is nothing to price, while it loads, or when the quote fails
 * (the plan then shows no price rather than a wrong one).
 */
export function usePlanFromPrice(activityIds, travelers) {
    const [quote, setQuote] = useState(null);
    const key = activityIds.join(',');

    useEffect(() => {
        if (!key || !(travelers >= 1)) {
            setQuote(null);
            return undefined;
        }
        let cancelled = false;
        const id = setTimeout(() => {
            pricingApi.quote({activityIds: key.split(','), travelers})
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
    }, [key, travelers]);

    return quote;
}
