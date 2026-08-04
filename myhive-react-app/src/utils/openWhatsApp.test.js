import {openWhatsApp} from './openWhatsApp';
import {WHATSAPP_URL, WHATSAPP_APP_URL} from '../services/config';

// The FAB's whole job is to open a WhatsApp chat without leaving the visitor on
// a blank wa.me tab. openWhatsApp encodes the "app-first, wa.me fallback"
// strategy that the research settled on:
//   mobile  → try the whatsapp:// app scheme in the current tab; only if the
//             app never takes over (page stays visible) fall back to wa.me.
//   desktop → open wa.me in a new tab (no app scheme exists there).

function makeNav(ua) {
    return {userAgent: ua};
}

describe('openWhatsApp', () => {
    let openSpy;
    let assignedHref;
    let doc;

    beforeEach(() => {
        jest.useFakeTimers();
        assignedHref = [];
        openSpy = jest.fn();
        // A minimal fake document whose visibility we can flip.
        doc = {
            visibilityState: 'visible',
            _handlers: {},
            addEventListener(type, fn) { this._handlers[type] = fn; },
            removeEventListener(type) { delete this._handlers[type]; },
            _hide() {
                this.visibilityState = 'hidden';
                this._handlers.visibilitychange?.();
            },
        };
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    const deps = () => ({
        nav: makeNav('iPhone'),
        doc,
        openWindow: openSpy,
        setHref: (url) => assignedHref.push(url),
    });

    test('mobile: navigates to the app scheme first', () => {
        openWhatsApp({...deps(), nav: makeNav('iPhone')});
        expect(assignedHref[0]).toBe(WHATSAPP_APP_URL);
        expect(openSpy).not.toHaveBeenCalled();
    });

    test('mobile: falls back to wa.me when the app never opens', () => {
        openWhatsApp({...deps(), nav: makeNav('Android')});
        jest.advanceTimersByTime(1200);
        expect(assignedHref).toEqual([WHATSAPP_APP_URL, WHATSAPP_URL]);
    });

    test('mobile: does NOT fall back once the app opens (page hidden)', () => {
        openWhatsApp({...deps(), nav: makeNav('iPhone')});
        doc._hide(); // app took over → tab backgrounded
        jest.advanceTimersByTime(5000);
        expect(assignedHref).toEqual([WHATSAPP_APP_URL]); // no wa.me fallback
    });

    test('desktop: opens wa.me in a new tab, no app scheme', () => {
        openWhatsApp({...deps(), nav: makeNav('Mozilla/5.0 (Macintosh; Intel Mac OS X)')});
        expect(assignedHref).toEqual([]);
        expect(openSpy).toHaveBeenCalledWith(WHATSAPP_URL, '_blank', 'noopener,noreferrer');
    });
});
