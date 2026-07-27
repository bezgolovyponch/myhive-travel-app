import {render, screen, fireEvent} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import WhatsAppWidget from './WhatsAppWidget';
import {WHATSAPP_URL} from '../services/config';

function renderAt(path) {
    return render(<MemoryRouter initialEntries={[path]}><WhatsAppWidget/></MemoryRouter>);
}

test('renders a WhatsApp link and fires the analytics event on click', () => {
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

test('hidden on the participant swipe page', () => {
    renderAt('/vote/tok123/activities');
    expect(screen.queryByRole('link', {name: /whatsapp/i})).toBeNull();
});
