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

    // On phones a bare `href` to wa.me navigates the current document to the
    // interstitial before handing off to the app; backing out of WhatsApp then
    // leaves the visitor stranded on that page (or, after another back, on the
    // site root — the "redirect to the main page" bug). Opening it as a
    // separate context via window.open keeps our page untouched: cancelling in
    // WhatsApp returns the user exactly where they were. Keep the real href so
    // the control is a genuine link (middle-click, right-click, no-JS).
    const openChat = (e) => {
        pushEvent('cta_click', {cta_label: 'whatsapp_widget', page: pathname});
        e.preventDefault();
        window.open(WHATSAPP_URL, '_blank', 'noopener,noreferrer');
    };

    return (
        <a
            className={className}
            href={WHATSAPP_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Chat with us on WhatsApp"
            onClick={openChat}
        >
            {/* Official WhatsApp glyph (filled bubble + handset), inlined so it
                needs no external request — matches the pissup FAB rather than
                the thin Phosphor outline we had before. */}
            <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 0 1-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 0 1-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 0 0-3.48-8.413z"/>
            </svg>
        </a>
    );
}

export default WhatsAppWidget;
