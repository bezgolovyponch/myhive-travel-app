import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import PrivacyPolicyPage from './PrivacyPolicyPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <PrivacyPolicyPage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('renders the privacy policy content statically (no injected script)', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /^privacy policy$/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /what data we collect/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /your rights/i})).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
});

test('identifies the data controller and a contact email', () => {
    renderPage();

    expect(screen.getAllByText(/PRAGOUT GROUP s\.r\.o\./).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('link', {name: /info@trivlu\.com/i}).length).toBeGreaterThan(0);
});
