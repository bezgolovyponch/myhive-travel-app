import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminCategories from './AdminCategories';

const mockApi = {
    getCategoriesPaged: jest.fn(),
    createCategory: jest.fn(),
    updateCategory: jest.fn(),
    deleteCategory: jest.fn(),
    getCategoryUsage: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
// Must return a stable reference — it is a dependency of the hook's internal
// useCallback; a fresh identity per render causes an endless refetch loop.
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

beforeEach(() => {
    // CRA's jest preset resets mocks before each test, so set implementations here.
    mockApi.getCategoriesPaged.mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    mockApi.createCategory.mockResolvedValue({});
});

async function openCreateModal(user) {
    render(<AdminCategories/>);
    await screen.findByText('Categories');
    await user.click(screen.getByRole('button', {name: '+ Add Category'}));
}

test('clicking Create with an empty name shows an inline error and does not call the API', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText('This field is required.')).toBeInTheDocument();
    expect(mockApi.createCategory).not.toHaveBeenCalled();
});

test('typing into the name field clears its error', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);
    await user.click(screen.getByRole('button', {name: 'Create'}));
    expect(await screen.findByText('This field is required.')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Nightlife/), 'Nightlife');

    expect(screen.queryByText('This field is required.')).not.toBeInTheDocument();
});

test('an invalid slug shows a format error and blocks create', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);
    await user.type(screen.getByPlaceholderText(/Nightlife/), 'Nightlife');
    await user.type(screen.getByPlaceholderText(/auto-generate/), 'Bad Slug');

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText(/lowercase letters, numbers and hyphens/)).toBeInTheDocument();
    expect(mockApi.createCategory).not.toHaveBeenCalled();
});

test('submitting a valid name (blank slug) creates the category and closes the modal', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.type(screen.getByPlaceholderText(/Nightlife/), 'Bali');
    await user.click(screen.getByRole('button', {name: 'Create'}));

    await waitFor(() => expect(mockApi.createCategory).toHaveBeenCalledTimes(1));
    expect(mockApi.createCategory).toHaveBeenCalledWith({name: 'Bali', slug: ''});
    await waitFor(() => expect(screen.queryByRole('button', {name: 'Create'})).not.toBeInTheDocument());
});

test('shows both name and slug errors when both are invalid on the same submit', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.type(screen.getByPlaceholderText(/auto-generate/), 'Bad Slug');
    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText('This field is required.')).toBeInTheDocument();
    expect(screen.getByText(/lowercase letters, numbers and hyphens/)).toBeInTheDocument();
    expect(mockApi.createCategory).not.toHaveBeenCalled();
});

test('editing an existing category submits via updateCategory', async () => {
    const user = userEvent.setup();
    mockApi.getCategoriesPaged.mockResolvedValue({
        content: [{id: 'c1', name: 'Nightlife', slug: 'nightlife'}],
        totalPages: 1,
        totalElements: 1,
    });
    mockApi.updateCategory.mockResolvedValue({});
    render(<AdminCategories/>);
    await screen.findByText('Nightlife');

    await user.click(screen.getByRole('button', {name: 'Edit'}));
    const nameInput = await screen.findByDisplayValue('Nightlife');
    await user.clear(nameInput);
    await user.type(nameInput, 'Nightlife Updated');
    await user.click(screen.getByRole('button', {name: 'Save Changes'}));

    await waitFor(() => expect(mockApi.updateCategory).toHaveBeenCalledTimes(1));
    expect(mockApi.updateCategory).toHaveBeenCalledWith('c1', {name: 'Nightlife Updated', slug: 'nightlife'});
});
