import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import WhatsAppWidget from './WhatsAppWidget';
import {WHATSAPP_URL} from '../services/config';

function renderAt(path) {
    return render(<MemoryRouter initialEntries={[path]}><WhatsAppWidget/></MemoryRouter>);
}

test('renders a direct WhatsApp link and fires the analytics event on click', () => {
    window.dataLayer = [];
    renderAt('/');
    const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
    expect(link).toHaveAttribute('href', WHATSAPP_URL);
    expect(link).toHaveAttribute('target', '_blank');
    fireEvent.click(link);
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'whatsapp_widget', page: '/',
    }));
});

// Cancel-must-return-to-site: the click opens WhatsApp in a separate context
// via window.open and prevents the default navigation, so backing out of
// WhatsApp (desktop or mobile) leaves the visitor on the page they were on
// rather than stranding them on the wa.me interstitial / site root.
test('opens WhatsApp in a new window without navigating the current page', () => {
    window.dataLayer = [];
    const openSpy = jest.spyOn(window, 'open').mockImplementation(() => null);
    try {
        renderAt('/destination/prague');
        const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
        const clickEvent = new MouseEvent('click', {bubbles: true, cancelable: true});
        link.dispatchEvent(clickEvent);
        expect(clickEvent.defaultPrevented).toBe(true);
        expect(openSpy).toHaveBeenCalledWith(WHATSAPP_URL, '_blank', 'noopener,noreferrer');
    } finally {
        openSpy.mockRestore();
    }
});

test('hidden on the participant swipe page', () => {
    renderAt('/vote/tok123/activities');
    expect(screen.queryByRole('link', {name: /whatsapp/i})).toBeNull();
});

test('hidden on the organizer curate page (same full-screen swipe deck)', () => {
    renderAt('/vote/new/curate');
    expect(screen.queryByRole('link', {name: /whatsapp/i})).toBeNull();
});

test('hidden on the organizer quiz page (fixed full-screen flow)', () => {
    renderAt('/vote/new/quiz');
    expect(screen.queryByRole('link', {name: /whatsapp/i})).toBeNull();
});

test('offset above the mobile Add-to-Trip bar on activity detail pages', () => {
    renderAt('/destination/prague/activity/pub-crawl');
    const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
    expect(link).toHaveClass('trv-chat-fab--above-add-bar');
});

test('not offset on other pages', () => {
    renderAt('/');
    const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
    expect(link).not.toHaveClass('trv-chat-fab--above-add-bar');
});

// Regression: on production the consent banner (CookieScript, whose loader
// index.html skips on localhost — so it never shows up in local dev) is a fixed
// sheet that can cover the bottom half of a phone screen. The FAB sat
// underneath it: invisible and untappable until the visitor consented.
function fakeConsentBar(height) {
    const bar = document.createElement('div');
    bar.id = 'cookiescript_injected';
    // jsdom has no layout engine — declare the box the real banner occupies.
    bar.getBoundingClientRect = () => ({
        height, width: 390, top: 664 - height, bottom: 664, left: 0, right: 390, x: 0, y: 664 - height,
        toJSON() {},
    });
    document.body.appendChild(bar);
    return bar;
}

test('publishes the consent bar height so the FAB clears it', async () => {
    const bar = fakeConsentBar(388);
    renderAt('/');

    await waitFor(() => expect(
        document.documentElement.style.getPropertyValue('--consent-bar-h'),
    ).toBe('388px'));

    bar.remove();
    await waitFor(() => expect(
        document.documentElement.style.getPropertyValue('--consent-bar-h'),
    ).toBe('0px'));
});

test('leaves the offset at zero when no consent bar is present', async () => {
    renderAt('/');
    await waitFor(() => expect(
        document.documentElement.style.getPropertyValue('--consent-bar-h'),
    ).toBe('0px'));
});

// The FAB's bottom offset must add that height, or the measurement is inert.
test('FAB bottom offset adds the consent bar height', () => {
    const fs = require('fs');
    const path = require('path');
    const css = fs.readFileSync(path.join(__dirname, 'WhatsAppWidget.css'), 'utf8');
    const block = css.match(/\.trv-chat-fab\s*{[^}]*}/)[0];
    expect(block).toMatch(/bottom:\s*calc\([^;]*var\(--consent-bar-h/);
});

// CRA's Jest replaces CSS imports with an empty stub, so getComputedStyle can't
// see stylesheet rules — assert on the declared values in the source CSS instead.
// The widget must stack BELOW the .app-modal overlay so dialogs cover it.
test('widget z-index stays below the app-modal overlay', () => {
    const fs = require('fs');
    const path = require('path');
    const widgetCss = fs.readFileSync(path.join(__dirname, 'WhatsAppWidget.css'), 'utf8');
    const modalCss = fs.readFileSync(path.join(__dirname, 'AppModal.css'), 'utf8');

    // Lazy [^}]*? so we grab the first z-index declaration in the block, not a
    // number mentioned later in a comment.
    const widgetZ = Number(widgetCss.match(/\.trv-chat-fab\s*{[^}]*?z-index:\s*(\d+)/)[1]);
    const modalZ = Number(modalCss.match(/\.app-modal\s*{[^}]*?z-index:\s*(\d+)/)[1]);

    expect(widgetZ).toBeLessThan(modalZ);
});
