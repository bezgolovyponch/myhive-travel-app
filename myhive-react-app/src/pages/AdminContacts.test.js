import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminContacts from './AdminContacts';

const mockApi = {
    getContactsPaged: jest.fn(),
    exportContactsCsv: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
// Must return a stable reference — it is a dependency of the page's useCallback;
// a fresh identity per render causes an endless refetch loop.
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

const contact = {
    id: '1',
    email: 'anna@example.com',
    name: 'Anna Example',
    locale: 'de',
    firstSource: 'VOTE',
    lastSource: 'BOOKING',
    firstSeenAt: '2026-09-01T10:00:00',
    lastSeenAt: '2026-09-12T08:30:00',
    touchCount: 3,
    unsubscribed: true,
};

beforeEach(() => {
    // CRA's jest preset resets mocks before each test, so set implementations here.
    mockApi.getContactsPaged.mockResolvedValue({content: [contact], totalPages: 1, totalElements: 1});
    mockApi.exportContactsCsv.mockResolvedValue();
});

test('renders contacts with source, touches and the unsubscribed badge', async () => {
    render(<AdminContacts/>);

    expect(await screen.findByText('anna@example.com')).toBeInTheDocument();
    expect(screen.getByText('Anna Example')).toBeInTheDocument();
    expect(screen.getByText('VOTE → BOOKING')).toBeInTheDocument();
    expect(screen.getByText('Unsubscribed')).toBeInTheDocument();
    expect(mockApi.getContactsPaged).toHaveBeenCalledWith(0, 20, '');
});

test('typing a search term refetches with the query', async () => {
    const user = userEvent.setup();
    render(<AdminContacts/>);
    await screen.findByText('anna@example.com');

    await user.type(screen.getByPlaceholderText('Search email or name'), 'anna');

    await waitFor(() => expect(mockApi.getContactsPaged).toHaveBeenLastCalledWith(0, 20, 'anna'));
});

test('Export CSV calls the export API', async () => {
    const user = userEvent.setup();
    render(<AdminContacts/>);
    await screen.findByText('anna@example.com');

    await user.click(screen.getByRole('button', {name: 'Export CSV'}));

    expect(mockApi.exportContactsCsv).toHaveBeenCalledTimes(1);
});

test('shows the empty state when there are no contacts', async () => {
    mockApi.getContactsPaged.mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    render(<AdminContacts/>);

    expect(await screen.findByText('No contacts yet.')).toBeInTheDocument();
});
