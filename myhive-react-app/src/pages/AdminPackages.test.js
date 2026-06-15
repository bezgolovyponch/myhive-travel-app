import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminPackages from './AdminPackages';

const mockApi = {
    getPackagesPaged: jest.fn(),
    createPackage: jest.fn(),
    updatePackage: jest.fn(),
    deletePackage: jest.fn(),
    getDestinations: jest.fn(),
    getCategories: jest.fn(),
    getActivitiesPaged: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
jest.mock('../hooks/useAuthErrorHandler', () => {
    // Stable reference: a fresh function each call makes useAdminCrud's fetchData
    // useCallback dep change every render and loops forever.
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

beforeEach(() => {
    // CRA's jest preset resets mocks before each test, so set implementations here.
    mockApi.getPackagesPaged.mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    mockApi.getDestinations.mockResolvedValue([{id: 'd1', name: 'Bali'}]);
    mockApi.getCategories.mockResolvedValue([]);
    mockApi.getActivitiesPaged.mockResolvedValue({content: [], totalPages: 1, totalElements: 0});
    mockApi.createPackage.mockResolvedValue({});
});

async function openCreateModal(user) {
    render(<AdminPackages/>);
    await screen.findByText('Packages');
    await user.click(screen.getByRole('button', {name: '+ Add Package'}));
}

test('clicking Create with an empty form shows inline errors and does not call the API', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText('Select a destination.')).toBeInTheDocument();
    expect(screen.getByText('Discount is required.')).toBeInTheDocument();
    expect(screen.getByText('Add at least one activity.')).toBeInTheDocument();
    expect(mockApi.createPackage).not.toHaveBeenCalled();
});

test('an out-of-range discount shows the range error and blocks create', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.selectOptions(screen.getByRole('combobox'), 'd1');
    await user.type(screen.getByPlaceholderText('Package name'), 'Bali Weekender');
    await user.type(screen.getByPlaceholderText('0.00'), '150');

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText('Discount must be between 0 and 100.')).toBeInTheDocument();
    expect(mockApi.createPackage).not.toHaveBeenCalled();
});
