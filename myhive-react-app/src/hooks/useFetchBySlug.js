import {useEffect, useRef, useState} from 'react';

// Generic fetch-by-route-param hook: resets error on param change and ignores
// responses that resolve after the param has already moved on. fetchFn is read
// through a ref so an inline arrow per render cannot cause a refetch loop —
// only the slug drives refetches.
// `initialData` lets a server renderer supply the record so it reaches the
// initial HTML — the fetch below runs in an effect, which no crawler executes.
// When provided the fetch is skipped entirely. Omitted in the SPA, which fetches
// exactly as before.
export function useFetchBySlug(fetchFn, slug, initialData) {
    const seeded = initialData !== undefined && initialData !== null;
    const [data, setData] = useState(seeded ? initialData : null);
    const [loading, setLoading] = useState(!seeded);
    const [error, setError] = useState(false);
    const fetchFnRef = useRef(fetchFn);
    fetchFnRef.current = fetchFn;

    useEffect(() => {
        if (seeded) {
            return undefined;
        }
        let cancelled = false;
        setLoading(true);
        setError(false);
        fetchFnRef.current(slug)
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
    }, [slug, seeded]);

    return {data, loading, error};
}
