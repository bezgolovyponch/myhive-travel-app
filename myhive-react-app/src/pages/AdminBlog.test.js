import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminBlog from './AdminBlog';

const mockApi = {
    getBlogPostsPaged: jest.fn(),
    createBlogPost: jest.fn(),
    updateBlogPost: jest.fn(),
};
// Stable references: both are dependencies of the CRUD hook's useCallbacks.
jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

const post = {id: 'b1', title: 'Prague guide', slug: 'prague-guide', slugDe: '', content: 'Body', date: '2026-10-01'};

beforeEach(() => {
    mockApi.getBlogPostsPaged.mockResolvedValue({content: [post], totalPages: 1, totalElements: 1});
    mockApi.updateBlogPost.mockResolvedValue({});
});

test('savesGermanSlug', async () => {
    const user = userEvent.setup();
    const expectedSlugDe = 'prag-ratgeber';
    render(<AdminBlog/>);
    const row = await screen.findByRole('row', {name: /Prague guide/});
    await user.click(within(row).getByRole('button', {name: 'Edit'}));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('German slug (optional)'), expectedSlugDe);
    await user.click(within(dialog).getByRole('button', {name: 'Save Changes'}));

    await waitFor(() => expect(mockApi.updateBlogPost).toHaveBeenCalledWith('b1', expect.objectContaining({
        slugDe: expectedSlugDe,
    })));
});
