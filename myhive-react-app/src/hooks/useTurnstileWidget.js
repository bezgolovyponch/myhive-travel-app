import {useCallback, useEffect, useRef, useState} from 'react';

// Cloudflare's "always passes" test sitekey — valid on any host (including localhost, where the
// production sitekey is rejected). Used only on localhost so the deposit flow can be exercised
// end-to-end in local dev; the backend dev profile pairs it with Cloudflare's test secret, which
// always verifies. Real hostnames use the production REACT_APP_TURNSTILE_SITE_KEY.
const TURNSTILE_TEST_SITEKEY = '1x00000000000000000000AA';

function turnstileSitekey() {
    const host = window.location.hostname || '';
    const isLocalhost = host === 'localhost' || host === '127.0.0.1' || host.endsWith('.localhost');
    return isLocalhost ? TURNSTILE_TEST_SITEKEY : process.env.REACT_APP_TURNSTILE_SITE_KEY;
}

/**
 * Renders a Cloudflare Turnstile widget into `containerRef` once the Cloudflare script is present
 * (polling until it loads) and exposes the solved token ('' until solved / after expiry).
 * Deactivating (active=false) resets the widget so a re-activation renders a fresh one.
 */
export function useTurnstileWidget(active) {
    const [token, setToken] = useState('');
    const containerRef = useRef(null);
    const widgetIdRef = useRef(null);

    const renderWidget = useCallback(() => {
        if (window.turnstile && containerRef.current && widgetIdRef.current === null) {
            widgetIdRef.current = window.turnstile.render(containerRef.current, {
                sitekey: turnstileSitekey(),
                callback: (newToken) => setToken(newToken),
                'expired-callback': () => setToken(''),
            });
        }
    }, []);

    useEffect(() => {
        if (!active) {
            return undefined;
        }
        if (window.turnstile) {
            renderWidget();
            return undefined;
        }
        const interval = setInterval(() => {
            if (window.turnstile) {
                clearInterval(interval);
                renderWidget();
            }
        }, 100);
        return () => clearInterval(interval);
    }, [active, renderWidget]);

    useEffect(() => {
        if (!active) {
            widgetIdRef.current = null;
            setToken('');
        }
    }, [active]);

    return {token, containerRef};
}
