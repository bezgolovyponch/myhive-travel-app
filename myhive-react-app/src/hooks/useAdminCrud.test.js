import {act, renderHook, waitFor} from '@testing-library/react';
import {useAdminCrud} from './useAdminCrud';

// Both mocks must return stable references — they are dependencies of the
// hook's internal useCallback, and fresh identities per render cause an
// endless refetch loop.
jest.mock('./useAdminApi', () => {
    const stableAdminApi = {};
    return {useAdminApi: () => stableAdminApi};
});
jest.mock('./useAuthErrorHandler', () => {
    const stableHandler = () => false;
    return {useAuthErrorHandler: () => stableHandler};
});

function renderCrud(overrides = {}) {
    // Built once and reused across re-renders: the hook requires a referentially
    // stable fetchFn (an inline fn per render causes an endless refetch loop).
    const props = {
        emptyForm: {name: ''},
        fetchFn: jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0}),
        createFn: jest.fn(),
        updateFn: jest.fn(),
        deleteFn: jest.fn(),
        mapItemToForm: (item) => item,
        ...overrides,
    };
    return renderHook(() => useAdminCrud(props));
}

test('failed save sets saveError and keeps the modal open', async () => {
    const expectedMessage = 'Slug already exists';
    const {result} = renderCrud({
        createFn: jest.fn().mockRejectedValue(new Error(expectedMessage)),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.saveError).toBe(expectedMessage);
    expect(result.current.showModal).toBe(true);
});

test('reopening the modal clears a previous saveError', async () => {
    const {result} = renderCrud({
        createFn: jest.fn().mockRejectedValue(new Error('boom')),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());
    expect(result.current.saveError).toBe('boom');

    act(() => result.current.openCreate());
    expect(result.current.saveError).toBe('');
});

test('successful save closes the modal and refetches', async () => {
    const fetchFn = jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    const createFn = jest.fn().mockResolvedValue({});
    const {result} = renderCrud({fetchFn, createFn});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.saveError).toBe('');
    expect(result.current.showModal).toBe(false);
    expect(fetchFn).toHaveBeenCalledTimes(2);
});

test('failed delete falls back to the error message without mapDeleteError', async () => {
    const {result} = renderCrud({
        deleteFn: jest.fn().mockRejectedValue(new Error('Failed to delete activity')),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.setDeleteId('id-1'));
    await act(() => result.current.handleDelete());

    expect(result.current.error).toBe('Failed to delete activity');
    expect(result.current.deleteId).toBeNull();
});

test('failed delete uses mapDeleteError to build the message', async () => {
    const err = Object.assign(new Error('Conflict'), {
        status: 409,
        body: {packageNames: ['Weekend Bali', 'Sunset Tour']},
    });
    const {result} = renderCrud({
        deleteFn: jest.fn().mockRejectedValue(err),
        mapDeleteError: (e) =>
            (e?.status === 409 && Array.isArray(e?.body?.packageNames))
                ? `Cannot delete: used in packages: ${e.body.packageNames.join(', ')}`
                : (e.message || 'Failed to delete.'),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.setDeleteId('id-1'));
    await act(() => result.current.handleDelete());

    expect(result.current.error).toBe('Cannot delete: used in packages: Weekend Bali, Sunset Tour');
});

test('handleSave blocks and sets fieldErrors when validate returns errors', async () => {
    const createFn = jest.fn();
    const validate = jest.fn().mockReturnValue({name: 'This field is required.'});
    const {result} = renderCrud({createFn, validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.fieldErrors).toEqual({name: 'This field is required.'});
    expect(createFn).not.toHaveBeenCalled();
    expect(result.current.showModal).toBe(true);
});

test('handleSave proceeds and clears fieldErrors when validate passes', async () => {
    const fetchFn = jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    const createFn = jest.fn().mockResolvedValue({});
    const validate = jest.fn().mockReturnValue({});
    const {result} = renderCrud({fetchFn, createFn, validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(createFn).toHaveBeenCalledTimes(1);
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.showModal).toBe(false);
});

test('updateField updates the form and clears that field error only', async () => {
    const validate = jest.fn().mockReturnValue({name: 'This field is required.', slug: 'Bad slug.'});
    const {result} = renderCrud({validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());
    expect(result.current.fieldErrors).toEqual({name: 'This field is required.', slug: 'Bad slug.'});

    act(() => result.current.updateField('name', 'Bali'));

    expect(result.current.form.name).toBe('Bali');
    expect(result.current.fieldErrors).toEqual({slug: 'Bad slug.'});
});

test('clearFieldError removes a single field error directly', async () => {
    const validate = jest.fn().mockReturnValue({name: 'This field is required.', slug: 'Bad slug.'});
    const {result} = renderCrud({validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());
    expect(result.current.fieldErrors).toEqual({name: 'This field is required.', slug: 'Bad slug.'});

    act(() => result.current.clearFieldError('slug'));

    expect(result.current.fieldErrors).toEqual({name: 'This field is required.'});
});
