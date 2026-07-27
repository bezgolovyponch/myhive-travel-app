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

// CRA's Jest replaces CSS imports with an empty stub, so getComputedStyle can't
// see stylesheet rules — assert on the declared values in the source CSS instead.
// The widget must stack BELOW the .app-modal overlay so dialogs cover it.
test('widget z-index stays below the app-modal overlay', () => {
    const fs = require('fs');
    const path = require('path');
    const widgetCss = fs.readFileSync(path.join(__dirname, 'WhatsAppWidget.css'), 'utf8');
    const globalCss = fs.readFileSync(path.join(__dirname, '../styles/global.css'), 'utf8');

    // Lazy [^}]*? so we grab the first z-index declaration in the block, not a
    // number mentioned later in a comment.
    const widgetZ = Number(widgetCss.match(/\.whatsapp-widget\s*{[^}]*?z-index:\s*(\d+)/)[1]);
    const modalZ = Number(globalCss.match(/\.app-modal\s*{[^}]*?z-index:\s*(\d+)/)[1]);

    expect(widgetZ).toBeLessThan(modalZ);
});
