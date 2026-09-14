import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ImportActivitiesModal from './ImportActivitiesModal';

const adminApi = {
    previewActivityImport: jest.fn(),
    applyActivityImport: jest.fn(),
};

function emptyPreview(overrides) {
    return {
        token: 'tok',
        totalRows: 0,
        rowsToUpdate: 0,
        rowsToCreate: 0,
        rowsUnchanged: 0,
        rowsWithErrors: 0,
        rowsWithWarnings: 0,
        changes: [],
        creates: [],
        errors: [],
        warnings: [],
        ...overrides,
    };
}

async function uploadAndPreview(user, preview) {
    adminApi.previewActivityImport.mockResolvedValue(preview);
    render(<ImportActivitiesModal show onHide={() => {}} adminApi={adminApi}/>);
    const file = new File(['id,name\n'], 'activities.csv', {type: 'text/csv'});
    await user.upload(document.querySelector('input[type="file"]'), file);
    await user.click(screen.getByRole('button', {name: 'Preview'}));
}

test('review step lists new activities and enables Apply when only creates are present', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({
        totalRows: 1,
        rowsToCreate: 1,
        creates: [{
            csvRowNumber: 2,
            name: 'Sunrise hike',
            destinationSlug: 'bali',
            slug: null,
            price: 45,
            categorySlugs: ['beach'],
            imageUrl: 'https://example.com/hike.jpg',
        }],
    }));

    expect(await screen.findByText('1 to create')).toBeInTheDocument();
    expect(screen.getByText('New activities')).toBeInTheDocument();
    expect(screen.getByText('Sunrise hike')).toBeInTheDocument();
    expect(screen.getByText('bali')).toBeInTheDocument();
    expect(screen.getByText('auto')).toBeInTheDocument();
    expect(screen.getByText('beach')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply 1 new'})).toBeEnabled();
});

test('Apply label combines updates and creates', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({
        totalRows: 3,
        rowsToUpdate: 2,
        rowsToCreate: 1,
        changes: [{
            csvRowNumber: 2,
            activityId: 'a1',
            activityName: 'Surf',
            fieldChanges: {name: {oldValue: 'Surf', newValue: 'Surf lesson'}},
        }],
        creates: [{csvRowNumber: 4, name: 'New one', destinationSlug: 'bali', slug: 'new-one', price: 10, categorySlugs: []}],
    }));

    expect(await screen.findByRole('button', {name: 'Apply 2 updates, 1 new'})).toBeEnabled();
    expect(screen.getByText('new-one')).toBeInTheDocument();
});

test('Apply stays disabled when the preview has errors', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({
        token: null,
        totalRows: 1,
        rowsToCreate: 1,
        rowsWithErrors: 1,
        errors: [{csvRowNumber: 2, code: 'UNKNOWN_DESTINATION', message: 'Unknown destination slug: atlantis', field: 'destination_slug'}],
    }));

    expect(await screen.findByText(/Unknown destination slug: atlantis/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply 1 new'})).toBeDisabled();
});

test('result step reports updated and created counts', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({totalRows: 2, rowsToUpdate: 1, rowsToCreate: 1,
        creates: [{csvRowNumber: 3, name: 'X', destinationSlug: 'bali', slug: null, price: 1, categorySlugs: []}]}));
    adminApi.applyActivityImport.mockResolvedValue({rowsUpdated: 1, rowsCreated: 1, appliedAt: '2026-09-14T00:00:00Z'});

    await user.click(await screen.findByRole('button', {name: 'Apply 1 update, 1 new'}));

    expect(await screen.findByText('Updated 1 activity, created 1 activity.')).toBeInTheDocument();
    expect(adminApi.applyActivityImport).toHaveBeenCalledWith('tok');
});

test('image fetch failure keeps Apply enabled: the backend keeps the token for a retry', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({totalRows: 1, rowsToCreate: 1,
        creates: [{csvRowNumber: 2, name: 'X', destinationSlug: 'bali', slug: null, price: 1, categorySlugs: []}]}));
    const err = new Error('Could not fetch image for row 2 (https://example.com/x.jpg): HTTP 404');
    err.body = {error: 'IMAGE_FETCH_FAILED'};
    adminApi.applyActivityImport.mockRejectedValue(err);

    await user.click(await screen.findByRole('button', {name: 'Apply 1 new'}));

    expect(await screen.findByText(/Could not fetch image for row 2/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply 1 new'})).toBeEnabled();
});

test('token error disables Apply and tells the admin to go Back and preview again', async () => {
    const user = userEvent.setup();
    await uploadAndPreview(user, emptyPreview({totalRows: 1, rowsToCreate: 1,
        creates: [{csvRowNumber: 2, name: 'X', destinationSlug: 'bali', slug: null, price: 1, categorySlugs: []}]}));
    const err = new Error('Preview token has expired');
    err.body = {error: 'TOKEN_EXPIRED'};
    adminApi.applyActivityImport.mockRejectedValue(err);

    await user.click(await screen.findByRole('button', {name: 'Apply 1 new'}));

    expect(await screen.findByText(/Preview token has expired\. Go Back and preview the file again\./)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply 1 new'})).toBeDisabled();
});
