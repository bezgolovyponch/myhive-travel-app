import {createAdminApi} from './adminApi';

function stubResponse({ok = true, status = 200, contentType = 'application/json', body = {}}) {
    return {
        ok,
        status,
        headers: {get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null)},
        json: jest.fn().mockResolvedValue(body),
    };
}

describe('createAdminApi', () => {
    // Set inside beforeEach, not at declaration: CRA's jest preset enables
    // resetMocks, which would otherwise wipe a declaration-time implementation.
    const getAccessToken = jest.fn();

    beforeEach(() => {
        getAccessToken.mockResolvedValue('test-token');
    });

    afterEach(() => {
        delete global.fetch;
    });

    it('resolves the JSON body and sends the bearer token on success', async () => {
        const expectedActivities = [{id: 'a1', name: 'Surfing'}];
        global.fetch = jest.fn().mockResolvedValue(stubResponse({body: expectedActivities}));
        const api = createAdminApi(getAccessToken);

        const result = await api.getActivities();

        expect(result).toEqual(expectedActivities);
        const [, options] = global.fetch.mock.calls[0];
        expect(options.headers.Authorization).toBe('Bearer test-token');
    });

    it('rejects with err.status 401 on unauthorized', async () => {
        global.fetch = jest.fn().mockResolvedValue(stubResponse({ok: false, status: 401}));
        const api = createAdminApi(getAccessToken);

        await expect(api.getActivities()).rejects.toMatchObject({status: 401});
    });

    it('rejects with the backend message and parsed body on 409', async () => {
        const errorBody = {message: 'Slug already exists', field: 'slug'};
        global.fetch = jest.fn().mockResolvedValue(
            stubResponse({ok: false, status: 409, body: errorBody})
        );
        const api = createAdminApi(getAccessToken);

        await expect(api.createActivity({name: 'x'})).rejects.toMatchObject({
            message: 'Slug already exists',
            status: 409,
            body: errorBody,
        });
    });

    it('createBookingPaymentLink POSTs amountCents and returns the link', async () => {
        const expectedLink = {url: 'https://pay/plink_1', amount: 28, shareId: 's1'};
        global.fetch = jest.fn().mockResolvedValue(stubResponse({body: expectedLink}));
        const api = createAdminApi(getAccessToken);

        const result = await api.createBookingPaymentLink('b1', 2800);

        expect(result).toEqual(expectedLink);
        const [url, opts] = global.fetch.mock.calls[0];
        expect(url).toContain('/admin/bookings/b1/payment-link');
        expect(opts.method).toBe('POST');
        expect(JSON.parse(opts.body)).toEqual({amountCents: 2800});
    });
});
