import {paymentApi} from './paymentApi';

function stubResponse({ok = true, status = 200, contentType = 'application/json', body = {}}) {
    return {
        ok,
        status,
        headers: {get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null)},
        json: jest.fn().mockResolvedValue(body),
    };
}

describe('paymentApi', () => {
    afterEach(() => {
        delete global.fetch;
    });

    it('createDepositSession posts trip data with vote + manager tokens and returns the url', async () => {
        const expected = {bookingId: 'b1', checkoutUrl: 'https://checkout/cs_1'};
        global.fetch = jest.fn().mockResolvedValue(stubResponse({status: 201, body: expected}));

        const result = await paymentApi.createDepositSession('share-1', 'mgr-1', {userEmail: 'a@b.com'});

        expect(result).toEqual(expected);
        const [url, options] = global.fetch.mock.calls[0];
        expect(url).toContain('/payments/deposit-session');
        // Tokens travel in headers, never the URL (M5).
        expect(url).not.toContain('share-1');
        expect(url).not.toContain('mgr-1');
        expect(options.headers['X-Vote-Share-Token']).toBe('share-1');
        expect(options.headers['X-Manager-Token']).toBe('mgr-1');
        expect(options.method).toBe('POST');
        expect(JSON.parse(options.body)).toEqual({userEmail: 'a@b.com'});
    });

    it('createConsultationLead posts trip data with vote + manager tokens', async () => {
        global.fetch = jest.fn().mockResolvedValue(stubResponse({status: 201, body: {bookingId: 'b1', message: 'ok'}}));

        const result = await paymentApi.createConsultationLead('share-1', 'mgr-1', {userEmail: 'a@b.com'});

        expect(result.message).toBe('ok');
        const [url, options] = global.fetch.mock.calls[0];
        expect(url).toContain('/payments/consultation-lead');
        expect(url).not.toContain('share-1');
        expect(url).not.toContain('mgr-1');
        expect(options.headers['X-Vote-Share-Token']).toBe('share-1');
        expect(options.headers['X-Manager-Token']).toBe('mgr-1');
        expect(options.method).toBe('POST');
        expect(JSON.parse(options.body)).toEqual({userEmail: 'a@b.com'});
    });

    it('rejects with parsed error on failure', async () => {
        global.fetch = jest.fn().mockResolvedValue(stubResponse({ok: false, status: 400, body: {message: 'nope'}}));

        await expect(paymentApi.createDepositSession('share-1', 'mgr-1', {userEmail: 'a@b.com'})).rejects.toMatchObject({status: 400});
    });
});
