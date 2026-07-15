import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import TermsPage from './TermsPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <TermsPage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('renders the terms content statically (no injected script)', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /terms & conditions/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /prices and payment/i})).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
});

test('names the operating legal entity', () => {
    renderPage();

    expect(screen.getAllByText(/PRAGOUT GROUP s\.r\.o\./).length).toBeGreaterThan(0);
});

test('covers the group-trip specifics (group leader and removal for conduct)', () => {
    renderPage();

    expect(screen.getByRole('heading', {name: /the group leader/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /conduct, safety and removal/i})).toBeInTheDocument();
});

test('links to the refund and privacy policies', () => {
    renderPage();

    expect(screen.getAllByRole('link', {name: /refund & cancellation policy/i}).length).toBeGreaterThan(0);

    const privacyLinks = screen.getAllByRole('link', {name: /privacy policy/i});
    expect(privacyLinks.length).toBeGreaterThan(0);
    expect(privacyLinks[0]).toHaveAttribute('href', '/privacy-policy');
});
