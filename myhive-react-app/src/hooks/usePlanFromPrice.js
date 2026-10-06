import {useEffect, useState} from 'react';
import pricingApi from '../services/pricingApi';

const DEBOUNCE_MS = 300;

/**
 * The Trip Builder plan's "from €X", priced on the server from the catalog for
 * these activities and this many travellers. Null while there is nothing to
 * price, while it loads, or when the quote fails (the plan then shows no price
 * rather than a wrong one).
 */
export function usePlanFromPrice(activityIds, travelers) {
    const [fromPrice, setFromPrice] = useState(null);
    const key = activityIds.join(',');

    useEffect(() => {
        if (!key || !(travelers >= 1)) {
            setFromPrice(null);
            return undefined;
        }
        let cancelled = false;
        const id = setTimeout(() => {
            pricingApi.quote({activityIds: key.split(','), travelers})
                .then(quote => {
                    if (!cancelled) setFromPrice(quote.fromPrice);
                })
                .catch(() => {
                    if (!cancelled) setFromPrice(null);
                });
        }, DEBOUNCE_MS);
        return () => {
            cancelled = true;
            clearTimeout(id);
        };
    }, [key, travelers]);

    return fromPrice;
}
