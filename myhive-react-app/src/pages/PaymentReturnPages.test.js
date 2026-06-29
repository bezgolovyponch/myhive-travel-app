import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import PaymentSuccessPage from './PaymentSuccessPage';
import PaymentCancelledPage from './PaymentCancelledPage';

test('success page confirms the payment is being processed', () => {
    render(
        <MemoryRouter initialEntries={['/payment/success?booking=b1']}>
            <PaymentSuccessPage />
        </MemoryRouter>
    );
    expect(screen.getByRole('heading', {name: /thank you/i})).toBeInTheDocument();
});

test('cancelled page shows a cancellation notice', () => {
    render(
        <MemoryRouter initialEntries={['/payment/cancelled']}>
            <PaymentCancelledPage />
        </MemoryRouter>
    );
    expect(screen.getByRole('heading', {name: /payment cancelled/i})).toBeInTheDocument();
});
