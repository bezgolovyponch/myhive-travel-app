import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PaymentActions from './PaymentActions';
import {paymentApi} from '../services/paymentApi';

jest.mock('../services/paymentApi', () => ({
    paymentApi: {
        createConsultationLead: jest.fn(),
    },
}));

function renderActions() {
    const makeBookingPayload = jest.fn((cd) => ({userEmail: cd.email, customerName: cd.fullName}));
    render(
        <PaymentActions
            voteShareToken="share-1"
            managerToken="mgr-1"
            tripData={{tripItems: [{id: 'a1', price: 50}]}}
            initialValues={{numberOfTravelers: 4, startDate: '2026-07-01', endDate: '2026-07-05'}}
            makeBookingPayload={makeBookingPayload}
        />
    );
    return {makeBookingPayload};
}

async function fillContact(user) {
    await user.type(screen.getByLabelText(/Full Name/i), 'Jane Doe');
    await user.type(screen.getByLabelText(/Email Address/i), 'jane@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 123 4567');
}

const mockAssign = jest.fn();

beforeAll(() => {
    Object.defineProperty(window, 'location', {
        value: {assign: mockAssign},
        writable: true,
    });
});

beforeEach(() => {
    mockAssign.mockReset();
    paymentApi.createConsultationLead.mockResolvedValue({bookingId: 'b1', message: 'ok'});
});

// Online payment is temporarily disabled (PAYMENTS_ENABLED=false), so the deposit
// CTA must not render — only the consultant action remains.
test('does not render the deposit CTA while payment is disabled', () => {
    renderActions();

    expect(screen.queryByRole('button', {name: /Book & pay 30% prepayment/i})).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: /Contact our consultant/i})).toBeInTheDocument();
});

test('consultant flow creates a lead and shows confirmation', async () => {
    const user = userEvent.setup();
    renderActions();

    await user.click(screen.getByRole('button', {name: /Contact our consultant/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(paymentApi.createConsultationLead).toHaveBeenCalledWith(
        'share-1', 'mgr-1', expect.objectContaining({userEmail: 'jane@example.com'}));
    expect(await screen.findByText(/consultant will contact you/i)).toBeInTheDocument();
    expect(mockAssign).not.toHaveBeenCalled();
});
