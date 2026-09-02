import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import AboutPage from './AboutPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <AboutPage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('publishes the operating legal entity, address and company id', () => {
    renderPage();

    expect(screen.getByRole('heading', {name: /company/i})).toBeInTheDocument();
    expect(screen.getByText(/PRAGOUT GROUP s\.r\.o\./)).toBeInTheDocument();
    expect(screen.getByText(/Na Folimance 2155\/15/)).toBeInTheDocument();
    expect(screen.getByText('11692111')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'info@trivlu.com'})).toHaveAttribute('href', 'mailto:info@trivlu.com');
});
