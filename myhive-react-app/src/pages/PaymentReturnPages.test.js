import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import PaymentSuccessPage from './PaymentSuccessPage';
import PaymentCancelledPage from './PaymentCancelledPage';
import {TripProvider} from '../context/TripContext';
import {WHATSAPP_URL} from '../services/config';

function renderSuccessPage() {
    return render(
        <MemoryRouter initialEntries={['/payment/success?booking=b1']}>
            <TripProvider>
                <PaymentSuccessPage />
            </TripProvider>
        </MemoryRouter>
    );
}

afterEach(() => {
    localStorage.clear();
});

test('success page confirms the payment is being processed', () => {
    renderSuccessPage();
    expect(screen.getByRole('heading', {name: /thank you/i})).toBeInTheDocument();
});

test('success page clears the trip so paid activities are not booked twice', () => {
    localStorage.setItem('myhive-trip-items', JSON.stringify([{id: 'a1', name: 'Beer Tour', price: 25}]));
    localStorage.setItem('myhive-trip-setup', JSON.stringify({travelers: 4, startDate: '2026-08-01', endDate: '2026-08-03', budget: 500}));

    renderSuccessPage();

    expect(JSON.parse(localStorage.getItem('myhive-trip-items'))).toEqual([]);
    const expectedSetup = {travelers: 1, startDate: '', endDate: '', budget: null};
    expect(JSON.parse(localStorage.getItem('myhive-trip-setup'))).toEqual(expectedSetup);
});

test('success page offers the WhatsApp follow-up contact', () => {
    renderSuccessPage();
    const link = screen.getByRole('link', {name: /whatsapp/i});
    expect(link).toHaveAttribute('href', WHATSAPP_URL);
    expect(link).toHaveAttribute('target', '_blank');
});

test('cancelled page shows a cancellation notice and keeps the trip intact', () => {
    localStorage.setItem('myhive-trip-items', JSON.stringify([{id: 'a1', name: 'Beer Tour', price: 25}]));

    render(
        <MemoryRouter initialEntries={['/payment/cancelled']}>
            <TripProvider>
                <PaymentCancelledPage />
            </TripProvider>
        </MemoryRouter>
    );

    expect(screen.getByRole('heading', {name: /payment cancelled/i})).toBeInTheDocument();
    // Cancelling on Stripe must not lose the trip — the user may retry the deposit.
    expect(JSON.parse(localStorage.getItem('myhive-trip-items'))).toHaveLength(1);
});
