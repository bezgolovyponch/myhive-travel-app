import {useEffect, useRef, useState} from 'react';

// Generic fetch-by-route-param hook: resets error on param change and ignores
// responses that resolve after the param has already moved on. fetchFn is read
// through a ref so an inline arrow per render cannot cause a refetch loop —
// only the slug drives refetches.
export function useFetchBySlug(fetchFn, slug) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const fetchFnRef = useRef(fetchFn);
    fetchFnRef.current = fetchFn;

    useEffect(() => {
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
    }, [slug]);

    return {data, loading, error};
}
