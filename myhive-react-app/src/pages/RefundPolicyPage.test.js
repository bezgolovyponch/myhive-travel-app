import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import RefundPolicyPage from './RefundPolicyPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <RefundPolicyPage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('renders the refund policy content statically (no injected script)', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /refund & cancellation policy/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /cancellation by you/i})).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
});

test('states the tiered cancellation windows', () => {
    renderPage();

    expect(screen.getByText(/30 or more days before the start date/i)).toBeInTheDocument();
    expect(screen.getByText(/15 to 29 days before the start date/i)).toBeInTheDocument();
    expect(screen.getByText(/14 or fewer days before the start date/i)).toBeInTheDocument();
});

test('links back to the terms and conditions', () => {
    renderPage();

    const termsLinks = screen.getAllByRole('link', {name: /terms & conditions/i});
    expect(termsLinks.length).toBeGreaterThan(0);
    expect(termsLinks[0]).toHaveAttribute('href', '/terms');
});
