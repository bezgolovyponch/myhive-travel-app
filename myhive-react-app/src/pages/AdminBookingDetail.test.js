import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Routes, Route} from 'react-router-dom';
import AdminBookingDetail from './AdminBookingDetail';

const mockApi = {
    getBookingById: jest.fn(),
    createBookingPaymentLink: jest.fn(),
    updateBookingStatus: jest.fn(),
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
// Plain function (not jest.fn) so CRA's resetMocks cannot strip the implementation.
let mockRoles = ['ADMIN'];
jest.mock('../context/AuthContext', () => ({
    useAuth: () => ({user: {email: 'admin@trivlu.com', roles: mockRoles}}),
}));

beforeEach(() => {
    mockRoles = ['ADMIN'];
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

// --- Status dropdown: operational statuses only, ADMIN-only, webhook owns payment statuses ---

test('admin changes the status via the dropdown and the badge updates', async () => {
    const user = userEvent.setup();
    const expectedStatus = 'CANCELLED';
    mockApi.getBookingById.mockResolvedValue({
        id: 'b1', status: 'PENDING', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 0, items: [], paymentLinks: [],
    });
    // The badge must update from the PATCH response alone — no refetch.
    mockApi.updateBookingStatus.mockResolvedValue({id: 'b1', status: expectedStatus});

    renderPage();

    const select = await screen.findByLabelText(/Change status/i);
    // Only operational statuses are offered; payment statuses stay webhook-owned.
    const options = [...select.querySelectorAll('option')].map(o => o.value).filter(Boolean);
    expect(options).toEqual(['CONFIRMED', 'CANCELLED']); // current PENDING excluded
    await user.selectOptions(select, expectedStatus);

    await waitFor(() => expect(mockApi.updateBookingStatus).toHaveBeenCalledWith('b1', expectedStatus));
    expect(await screen.findByText(expectedStatus)).toBeInTheDocument();
    // Local merge, not a page-flashing refetch: getBookingById ran only for the initial load.
    expect(mockApi.getBookingById).toHaveBeenCalledTimes(1);
});

test('a booking in a payment status offers only CANCELLED', async () => {
    mockApi.getBookingById.mockResolvedValue({
        id: 'b1', status: 'DEPOSIT_PAID', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 12, items: [], paymentLinks: [],
    });

    renderPage();

    const select = await screen.findByLabelText(/Change status/i);
    // Money is in — PENDING/CONFIRMED would hide it; cancellation is the only manual exit.
    const options = [...select.querySelectorAll('option')].map(o => o.value).filter(Boolean);
    expect(options).toEqual(['CANCELLED']);
});

test('status dropdown is hidden for MANAGER', async () => {
    mockRoles = ['MANAGER'];
    mockApi.getBookingById.mockResolvedValue({
        id: 'b1', status: 'PENDING', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 0, items: [], paymentLinks: [],
    });

    renderPage();

    expect(await screen.findByText('PENDING')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Change status/i)).not.toBeInTheDocument();
});

test('shows an error when the status update fails', async () => {
    const user = userEvent.setup();
    mockApi.getBookingById.mockResolvedValue({
        id: 'b1', status: 'PENDING', userEmail: 'x@y.z',
        totalAmount: 40, amountPaid: 0, items: [], paymentLinks: [],
    });
    mockApi.updateBookingStatus.mockRejectedValue(new Error('Status REFUNDED is managed by the Stripe webhook'));

    renderPage();

    await user.selectOptions(await screen.findByLabelText(/Change status/i), 'CONFIRMED');

    expect(await screen.findByText(/managed by the Stripe webhook/i)).toBeInTheDocument();
});
