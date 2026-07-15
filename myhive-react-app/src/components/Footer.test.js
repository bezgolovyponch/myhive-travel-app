import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import Footer from './Footer';

function renderFooter() {
    return render(
        <MemoryRouter>
            <Footer/>
        </MemoryRouter>
    );
}

test('links to the legal policy pages', () => {
    renderFooter();

    expect(screen.getByRole('link', {name: /^terms$/i})).toHaveAttribute('href', '/terms');
    expect(screen.getByRole('link', {name: /refund policy/i})).toHaveAttribute('href', '/refund-policy');
    expect(screen.getByRole('link', {name: /cookie policy/i})).toHaveAttribute('href', '/cookie-policy');
    expect(screen.getByRole('link', {name: /privacy policy/i})).toHaveAttribute('href', '/privacy-policy');
});

test('exposes a CookieYes revisit element for reopening the consent banner', () => {
    renderFooter();

    const revisit = screen.getByRole('button', {name: /cookie settings/i});
    expect(revisit).toHaveClass('cky-banner-element');
});
