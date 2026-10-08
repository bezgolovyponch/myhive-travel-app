import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import WhatsAppWidget, {WhatsAppButton} from './WhatsAppWidget';
import {WHATSAPP_URL} from '../services/config';

function renderAt(path) {
    return render(<MemoryRouter initialEntries={[path]}><WhatsAppWidget/><WhatsAppButton/></MemoryRouter>);
}

test('renders a direct WhatsApp link and fires the analytics event on click', () => {
    window.dataLayer = [];
    renderAt('/');
    const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
    expect(link).toHaveAttribute('href', WHATSAPP_URL);
    expect(link).toHaveAttribute('target', '_blank');
    fireEvent.click(link);
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'whatsapp_header', page: '/',
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

// The button used to float over the bottom-right corner and had to hide or move
// on pages that pin something there. In the header it needs neither.
test('the layout widget itself draws nothing', () => {
    const {container} = render(<MemoryRouter><WhatsAppWidget/></MemoryRouter>);
    expect(container).toBeEmptyDOMElement();
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

test('publishes the consent bar height for what is pinned to the bottom edge', async () => {
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
