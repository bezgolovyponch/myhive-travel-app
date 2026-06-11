import {useEffect, useState} from 'react';

// Generic fetch-by-route-param hook: resets error on param change and ignores
// responses that resolve after the param has already moved on.
// fetchFn must be referentially stable (module-level api methods qualify).
export function useFetchBySlug(fetchFn, slug) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(false);
        fetchFn(slug)
            .then(result => {
                if (!cancelled) {
                    setData(result);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setError(true);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [fetchFn, slug]);

    return {data, loading, error};
}
