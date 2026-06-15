import {render, screen} from '@testing-library/react';
import {HelmetProvider} from 'react-helmet-async';
import CookiePolicyPage from './CookiePolicyPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <CookiePolicyPage/>
        </HelmetProvider>
    );
}

beforeEach(() => {
    window.dataLayer = [];
});

test('renders the heading and the GTM mount point (no CookieYes script in the bundle)', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /cookie policy/i})).toBeInTheDocument();
    expect(container.querySelector('#cookie-policy-content')).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
});

test('signals GTM that the cookie policy view is ready', () => {
    renderPage();

    expect(window.dataLayer).toEqual(
        expect.arrayContaining([{event: 'cookie_policy_view'}])
    );
});
