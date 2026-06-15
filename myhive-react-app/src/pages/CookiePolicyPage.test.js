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

test('renders the policy content statically (no injected script)', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /^cookie policy$/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /what are cookies/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /how do we use cookies/i})).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
});

test('exposes a CookieYes revisit control to reopen the consent banner', () => {
    renderPage();

    const revisit = screen.getByRole('button', {name: /consent preferences/i});
    expect(revisit).toHaveClass('cky-banner-element');
});
