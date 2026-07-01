import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import AdminBookingDetail from './AdminBookingDetail';

const mockApi = {
    getBookingById: jest.fn(),
    createBookingPaymentLink: jest.fn(),
};
jest.mock('../hooks/useAdminApi', () => ({
    useAdminApi: () => mockApi,
}));
// Must return a stable reference — it is a dependency of fetchBooking's useCallback;
// a fresh identity per render causes an endless refetch loop.
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

function renderPage() {
    return render(
        <MemoryRouter initialEntries={['/admin/bookings/b1']}>
            <Routes>
                <Route path="/admin/bookings/:id" element={<AdminBookingDetail />} />
            </Routes>
        </MemoryRouter>
    );
}

test('shows Deposit and Balance type labels in payment history', async () => {
    mockApi.getBookingById.mockResolvedValue({
        id: 'b1', status: 'DEPOSIT_PAID', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 12, items: [],
        paymentLinks: [
            {id: 'd1', amount: 12, paid: true, url: 'https://checkout/cs_dep', type: 'DEPOSIT'},
            {id: 'l1', amount: 28, paid: false, url: 'https://pay/plink_1', type: 'BALANCE'},
        ],
    });

    renderPage();

    expect(await screen.findByRole('heading', {name: 'Payment'})).toBeInTheDocument();
    expect(await screen.findByText('Deposit')).toBeInTheDocument();
    expect(await screen.findByText('Balance')).toBeInTheDocument();
    // Both rows are present — one paid (deposit), one unpaid with a URL (balance)
    expect(screen.getByText('https://pay/plink_1')).toBeInTheDocument();
});

test('shows balance and creates a payment link with cents', async () => {
    const user = userEvent.setup();
    // First call (initial load): no payment links.
    // Subsequent calls (after create): booking has the new link.
    mockApi.getBookingById
        .mockResolvedValueOnce({
            id: 'b1', status: 'DEPOSIT_PAID', userEmail: 'x@y.z',
            totalAmount: 40, amountPaid: 12, items: [], paymentLinks: [],
        })
        .mockResolvedValue({
            id: 'b1', status: 'DEPOSIT_PAID', userEmail: 'x@y.z',
            totalAmount: 40, amountPaid: 12, items: [],
            paymentLinks: [{id: 's1', amount: 28, paid: false, url: 'https://pay/plink_1'}],
        });
    mockApi.createBookingPaymentLink.mockResolvedValue({
        url: 'https://pay/plink_1', amount: 28, shareId: 's1',
    });

    renderPage();

    // Wait for booking to load and Payment heading to appear.
    // Use getByRole to avoid ambiguity with "Create payment link" button text.
    expect(await screen.findByRole('heading', {name: 'Payment'})).toBeInTheDocument();

    // balance due: 40 - 12 = 28, prefilled after the booking-id effect fires
    await waitFor(() => expect(screen.getByLabelText(/Amount/i)).toHaveValue(28));

    await user.click(screen.getByRole('button', {name: /Create payment link/i}));

    await waitFor(() => expect(mockApi.createBookingPaymentLink).toHaveBeenCalledWith('b1', 2800));
    expect(await screen.findByText('https://pay/plink_1')).toBeInTheDocument();
});
