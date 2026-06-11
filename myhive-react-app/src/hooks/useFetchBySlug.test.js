import {renderHook, waitFor} from '@testing-library/react';
import {useFetchBySlug} from './useFetchBySlug';

test('exposes data after a successful fetch', async () => {
    const fetchFn = jest.fn().mockResolvedValue({name: 'Prague'});
    const {result} = renderHook(() => useFetchBySlug(fetchFn, 'prague'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({name: 'Prague'});
    expect(result.current.error).toBe(false);
});

test('resets error when slug changes after a failure', async () => {
    const fetchFn = jest.fn()
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce({name: 'Prague'});
    const {result, rerender} = renderHook(({slug}) => useFetchBySlug(fetchFn, slug), {
        initialProps: {slug: 'bad'},
    });
    await waitFor(() => expect(result.current.error).toBe(true));

    rerender({slug: 'prague'});
    await waitFor(() => expect(result.current.data).toEqual({name: 'Prague'}));
    expect(result.current.error).toBe(false);
});

test('ignores a stale response that resolves after the slug changed', async () => {
    let resolveFirst;
    const fetchFn = jest.fn()
        .mockImplementationOnce(() => new Promise(res => {
            resolveFirst = res;
        }))
        .mockResolvedValueOnce({name: 'new'});
    const {result, rerender} = renderHook(({slug}) => useFetchBySlug(fetchFn, slug), {
        initialProps: {slug: 'old'},
    });

    rerender({slug: 'new'});
    await waitFor(() => expect(result.current.data).toEqual({name: 'new'}));

    resolveFirst({name: 'old'});
    // Give the stale promise a chance to (incorrectly) apply state
    await new Promise(res => setTimeout(res, 0));
    expect(result.current.data).toEqual({name: 'new'});
});
