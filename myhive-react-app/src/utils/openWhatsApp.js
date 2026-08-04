import {WHATSAPP_URL, WHATSAPP_APP_URL} from '../services/config';

// How long to wait for the WhatsApp app to take over before assuming it isn't
// installed and falling back to the wa.me web link. ~1.2s is the common value:
// long enough for the OS app switch, short enough not to feel stuck.
const APP_HANDOFF_MS = 1200;

// A phone/tablet — the only place the whatsapp:// scheme (and the interstitial
// problem) exists. Desktop has no app scheme, so it always uses the web link.
function isMobile(nav) {
    return /Android|iPhone|iPad|iPod|Mobile/i.test(nav.userAgent || '');
}

// Open a WhatsApp chat, avoiding the blank-tab bug.
//
// The bug: wa.me is a WEB page. On a phone it loads, hands off to the app, and
// lingers behind — so backing out of WhatsApp lands on a blank wa.me tab.
//
// Strategy (dependency-free "app opener"):
//   mobile  → navigate the CURRENT tab to the whatsapp:// app scheme. No web
//             page loads, so there's nothing to strand the user on; the back
//             button returns straight to our page. If the app never opens (not
//             installed) the tab stays visible, and after APP_HANDOFF_MS we
//             fall back to wa.me in the same tab.
//   desktop → open wa.me in a new tab (there's no app scheme; keep our page).
//
// Deps are injected so the timer/visibility logic is unit-testable.
export function openWhatsApp({
    nav = typeof navigator !== 'undefined' ? navigator : {userAgent: ''},
    doc = typeof document !== 'undefined' ? document : undefined,
    openWindow = typeof window !== 'undefined' ? window.open.bind(window) : () => {},
    setHref = (url) => { window.location.href = url; },
} = {}) {
    if (!isMobile(nav)) {
        openWindow(WHATSAPP_URL, '_blank', 'noopener,noreferrer');
        return;
    }

    // If the tab gets backgrounded, the app took over — cancel the fallback so
    // we never yank a returning user onto the wa.me interstitial.
    let bounced = false;
    const onHidden = () => {
        if (doc && doc.visibilityState === 'hidden') {
            bounced = true;
            doc.removeEventListener('visibilitychange', onHidden);
        }
    };
    doc?.addEventListener('visibilitychange', onHidden);

    setHref(WHATSAPP_APP_URL);

    setTimeout(() => {
        doc?.removeEventListener('visibilitychange', onHidden);
        // Still here and never backgrounded → the app didn't open. Fall back.
        if (!bounced && (!doc || doc.visibilityState === 'visible')) {
            setHref(WHATSAPP_URL);
        }
    }, APP_HANDOFF_MS);
}
