import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {WHATSAPP_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import './WhatsAppWidget.css';

// Full-screen fixed flows (swipe deck / quiz) where the FAB would sit on top
// of their own fixed controls — hidden entirely rather than offset.
const FULL_SCREEN_ROUTES = [
    /^\/vote\/[^/]+\/activities$/, // participant swipe page
    /^\/vote\/new\/curate$/,       // organizer swipe deck (same UI as above)
    /^\/vote\/new\/quiz$/,         // organizer quiz — fixed full-screen flow
];

// Activity detail pages render a fixed mobile Add-to-Trip bar; the FAB is
// offset above it there so it never covers that primary CTA.
const ADD_BAR_ROUTE = /^\/destination\/[^/]+\/activity\/[^/]+$/;

// CookieYes' consent bar is a fixed z-index 9999999 sheet anchored to the bottom
// of the viewport. On a phone it is ~390px tall — full width, over half the
// screen — so the FAB sat underneath it: invisible and untappable until the
// visitor consented. It arrives via GTM, which index.html skips on localhost,
// so it never shows up in local dev; only production has it.
// We publish its height and the FAB floats clear of it (never over it: the
// consent controls must stay reachable).
const CONSENT_BAR = '.cky-consent-container';
const CONSENT_HEIGHT_VAR = '--cky-consent-h';

function useConsentBarClearance() {
    useEffect(() => {
        const root = document.documentElement;
        let barObserver = null;

        const measure = () => {
            const bar = document.querySelector(CONSENT_BAR);
            // A dismissed bar often stays in the DOM with a zero-height/hidden
            // box rather than being removed — height 0 covers both cases.
            const height = bar ? Math.round(bar.getBoundingClientRect().height) : 0;
            root.style.setProperty(CONSENT_HEIGHT_VAR, `${height}px`);
        };

        // The bar is injected (and later torn down) asynchronously by GTM, and
        // resizes when "Customise" expands it — watch both events.
        const track = () => {
            barObserver?.disconnect();
            barObserver = null;
            const bar = document.querySelector(CONSENT_BAR);
            if (bar && typeof ResizeObserver === 'function') {
                barObserver = new ResizeObserver(measure);
                barObserver.observe(bar);
            }
            measure();
        };

        track();
        // childList only, on body's direct children: the bar is appended there,
        // and a deep/attribute observer would force a layout on every render.
        const bodyObserver = new MutationObserver(track);
        bodyObserver.observe(document.body, {childList: true});
        window.addEventListener('resize', measure);

        return () => {
            bodyObserver.disconnect();
            barObserver?.disconnect();
            window.removeEventListener('resize', measure);
            root.style.removeProperty(CONSENT_HEIGHT_VAR);
        };
    }, []);
}

// Floating "chat with us" FAB — opens the WhatsApp conversation directly.
function WhatsAppWidget() {
    const {pathname} = useLocation();
    useConsentBarClearance();
    if (FULL_SCREEN_ROUTES.some(re => re.test(pathname))) {
        return null;
    }
    const aboveAddBar = ADD_BAR_ROUTE.test(pathname);
    const className = aboveAddBar ? 'trv-chat-fab trv-chat-fab--above-add-bar' : 'trv-chat-fab';
    return (
        <a
            className={className}
            href={WHATSAPP_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Chat with us on WhatsApp"
            onClick={() => pushEvent('cta_click', {cta_label: 'whatsapp_widget', page: pathname})}
        >
            <i className="ph ph-whatsapp-logo" aria-hidden="true"/>
        </a>
    );
}

export default WhatsAppWidget;
