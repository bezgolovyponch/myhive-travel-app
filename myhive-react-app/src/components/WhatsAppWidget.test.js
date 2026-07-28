import {render, screen, fireEvent} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import WhatsAppWidget from './WhatsAppWidget';
import {WHATSAPP_URL} from '../services/config';

function renderAt(path) {
    return render(<MemoryRouter initialEntries={[path]}><WhatsAppWidget/></MemoryRouter>);
}

afterEach(() => {
    jest.restoreAllMocks();
});

test('FAB opens the chat teaser and fires the analytics event', () => {
    window.dataLayer = [];
    renderAt('/');
    fireEvent.click(screen.getByRole('button', {name: /chat with us on whatsapp/i}));
    expect(screen.getByRole('dialog', {name: /chat with us on whatsapp/i})).toBeInTheDocument();
    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByText(/typically replies instantly/i)).toBeInTheDocument();
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'whatsapp_widget', page: '/',
    }));
});

test('send opens WhatsApp with the typed message prefilled', async () => {
    window.dataLayer = [];
    const open = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderAt('/');
    fireEvent.click(screen.getByRole('button', {name: /chat with us on whatsapp/i}));
    await userEvent.type(screen.getByPlaceholderText(/type your destination or dates/i), 'Prague, 12-14 Sep');
    fireEvent.click(screen.getByRole('button', {name: /send on whatsapp/i}));
    expect(open).toHaveBeenCalledWith(
        `${WHATSAPP_URL}?text=${encodeURIComponent('Prague, 12-14 Sep')}`,
        '_blank',
        'noopener,noreferrer',
    );
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'whatsapp_widget_send', page: '/',
    }));
    // The teaser closes once the conversation is handed over to WhatsApp.
    expect(screen.queryByRole('dialog')).toBeNull();
});

test('send with an empty input opens the plain WhatsApp link', () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderAt('/');
    fireEvent.click(screen.getByRole('button', {name: /chat with us on whatsapp/i}));
    fireEvent.click(screen.getByRole('button', {name: /send on whatsapp/i}));
    expect(open).toHaveBeenCalledWith(WHATSAPP_URL, '_blank', 'noopener,noreferrer');
});

test('Enter in the input sends like the send button', async () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderAt('/');
    fireEvent.click(screen.getByRole('button', {name: /chat with us on whatsapp/i}));
    await userEvent.type(screen.getByPlaceholderText(/type your destination or dates/i), 'Karting?{enter}');
    expect(open).toHaveBeenCalledWith(
        `${WHATSAPP_URL}?text=${encodeURIComponent('Karting?')}`,
        '_blank',
        'noopener,noreferrer',
    );
});

test('close button dismisses the teaser without opening WhatsApp', () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => null);
    renderAt('/');
    fireEvent.click(screen.getByRole('button', {name: /chat with us on whatsapp/i}));
    fireEvent.click(screen.getByRole('button', {name: /close chat/i}));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(open).not.toHaveBeenCalled();
});

test('hidden on the participant swipe page', () => {
    renderAt('/vote/tok123/activities');
    expect(screen.queryByRole('button', {name: /whatsapp/i})).toBeNull();
});

test('hidden on the organizer curate page (same full-screen swipe deck)', () => {
    renderAt('/vote/new/curate');
    expect(screen.queryByRole('button', {name: /whatsapp/i})).toBeNull();
});

test('hidden on the organizer quiz page (fixed full-screen flow)', () => {
    renderAt('/vote/new/quiz');
    expect(screen.queryByRole('button', {name: /whatsapp/i})).toBeNull();
});

test('offset above the mobile Add-to-Trip bar on activity detail pages', () => {
    const {container} = renderAt('/destination/prague/activity/pub-crawl');
    expect(container.querySelector('.whatsapp-widget')).toHaveClass('whatsapp-widget--above-add-bar');
});

test('not offset on other pages', () => {
    const {container} = renderAt('/');
    expect(container.querySelector('.whatsapp-widget')).not.toHaveClass('whatsapp-widget--above-add-bar');
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
