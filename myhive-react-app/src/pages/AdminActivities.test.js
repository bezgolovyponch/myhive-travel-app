import {fireEvent, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminActivities from './AdminActivities';

const mockApi = {
    getActivitiesPaged: jest.fn(),
    getDestinations: jest.fn(),
    getCategories: jest.fn(),
    getPrimaryCategories: jest.fn(),
    createActivity: jest.fn(),
    updateActivity: jest.fn(),
};
// Stable references: both are dependencies of the CRUD hook's useCallbacks.
jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

const karting = {
    id: 'a1', name: 'Karting', slug: 'karting', price: 40, destinationId: 'd1', destinationName: 'Prague',
    categories: [], primaryCategoryId: 'pc1', slugDe: 'kartfahren',
    extras: [{code: 'show', name: 'Private show', description: '', price: 250, pricing: 'PER_GROUP'}],
};

beforeEach(() => {
    mockApi.getActivitiesPaged.mockResolvedValue({content: [karting], totalPages: 1, totalElements: 1});
    mockApi.getDestinations.mockResolvedValue([{id: 'd1', name: 'Prague'}]);
    mockApi.getCategories.mockResolvedValue([]);
    mockApi.getPrimaryCategories.mockResolvedValue([{id: 'pc1', slug: 'stag-do', slugDe: 'jga', name: 'Stag do'}]);
    mockApi.createActivity.mockResolvedValue({});
    mockApi.updateActivity.mockResolvedValue({});
});

async function openEdit(user) {
    render(<AdminActivities/>);
    const row = await screen.findByRole('row', {name: /Karting/});
    await user.click(within(row).getByRole('button', {name: 'Edit'}));
    return screen.findByRole('dialog');
}

test('the table counts each activity’s extras', async () => {
    render(<AdminActivities/>);

    const row = await screen.findByRole('row', {name: /Karting/});
    expect(screen.getByRole('columnheader', {name: 'Extras'})).toBeInTheDocument();
    expect(within(row).getByText('1')).toBeInTheDocument();
});

test('saving after removing every extra sends an empty list, never null', async () => {
    const user = userEvent.setup();
    const dialog = await openEdit(user);

    await user.click(within(dialog).getByRole('button', {name: 'Remove extra 1'}));
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    await waitFor(() => expect(mockApi.updateActivity).toHaveBeenCalledWith('a1', expect.objectContaining({extras: []})));
});

test('a new extra is saved with a numeric price', async () => {
    const user = userEvent.setup();
    const expectedExtra = {name: 'Photos', price: 15, pricing: 'PER_PERSON', description: 'A photographer'};
    const dialog = await openEdit(user);

    await user.click(within(dialog).getByRole('button', {name: '+ Add extra'}));
    await user.type(within(dialog).getByLabelText('Extra 2 name'), expectedExtra.name);
    await user.type(within(dialog).getByLabelText('Extra 2 price'), '15');
    await user.selectOptions(within(dialog).getByLabelText('Extra 2 pricing'), 'PER_PERSON');
    await user.type(within(dialog).getByLabelText('Extra 2 description'), expectedExtra.description);
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    await waitFor(() => expect(mockApi.updateActivity).toHaveBeenCalledWith('a1', expect.objectContaining({
        extras: [karting.extras[0], expectedExtra],
    })));
});

test('an extra with a negative price is not saved', async () => {
    const user = userEvent.setup();
    const expectedMessage = 'Price must be 0 or more.';
    const dialog = await openEdit(user);

    fireEvent.change(within(dialog).getByLabelText('Extra 1 price'), {target: {value: '-5'}});
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    expect(await within(dialog).findByText(expectedMessage)).toBeInTheDocument();
    expect(mockApi.updateActivity).not.toHaveBeenCalled();
});

test('an extra without a name or a price is not saved', async () => {
    const user = userEvent.setup();
    const dialog = await openEdit(user);

    await user.click(within(dialog).getByRole('button', {name: '+ Add extra'}));
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    expect(await within(dialog).findByText('Each extra needs a name and a price.')).toBeInTheDocument();
    expect(mockApi.updateActivity).not.toHaveBeenCalled();
});

test('createRequiresPrimaryCategory', async () => {
    const user = userEvent.setup();
    render(<AdminActivities/>);
    await screen.findByRole('row', {name: /Karting/});
    await user.click(screen.getByRole('button', {name: '+ Add Activity'}));
    const dialog = await screen.findByRole('dialog');

    await user.selectOptions(within(dialog).getByRole('combobox', {name: 'Destination'}), 'd1');
    await user.type(within(dialog).getByPlaceholderText('Activity name'), 'Paintball');
    await user.type(within(dialog).getByPlaceholderText('0.00'), '30');
    await user.click(within(dialog).getByRole('button', {name: 'Create'}));

    expect(await within(dialog).findByText('Primary category is required')).toBeInTheDocument();
    expect(mockApi.createActivity).not.toHaveBeenCalled();
});

test('editSendsSlugDeAndPrimaryCategory', async () => {
    const user = userEvent.setup();
    const expectedSlugDe = 'gokart';
    const dialog = await openEdit(user);

    const slugDe = within(dialog).getByLabelText('German slug (optional)');
    await user.clear(slugDe);
    await user.type(slugDe, expectedSlugDe);
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    await waitFor(() => expect(mockApi.updateActivity).toHaveBeenCalledWith('a1', expect.objectContaining({
        slugDe: expectedSlugDe,
        primaryCategoryId: 'pc1',
    })));
});
