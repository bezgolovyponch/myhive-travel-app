import {parseApiError} from './httpError';

function stubResponse({status = 500, contentType = 'application/json', body = null, jsonThrows = false} = {}) {
    return {
        status,
        headers: {get: (name) => (name.toLowerCase() === 'content-type' ? contentType : null)},
        json: () => (jsonThrows ? Promise.reject(new Error('bad json')) : Promise.resolve(body)),
    };
}

test('prefers the backend message from a JSON body', async () => {
    const expectedMessage = 'Slug already exists';
    const err = await parseApiError(stubResponse({status: 409, body: {message: expectedMessage}}), 'Failed to save');
    expect(err.message).toBe(expectedMessage);
    expect(err.status).toBe(409);
    expect(err.body).toEqual({message: expectedMessage});
});

test('keeps the fallback message for non-JSON responses', async () => {
    const err = await parseApiError(stubResponse({status: 502, contentType: 'text/html'}), 'Failed to fetch');
    expect(err.message).toBe('Failed to fetch');
    expect(err.status).toBe(502);
    expect(err.body).toBeNull();
});

test('keeps the fallback message when the JSON body fails to parse', async () => {
    const err = await parseApiError(stubResponse({status: 500, jsonThrows: true}), 'Failed to fetch');
    expect(err.message).toBe('Failed to fetch');
    expect(err.body).toBeNull();
});

test('keeps the fallback message when the JSON body has no message field', async () => {
    const err = await parseApiError(stubResponse({status: 400, body: {code: 'VALIDATION'}}), 'Failed to save');
    expect(err.message).toBe('Failed to save');
    expect(err.body).toEqual({code: 'VALIDATION'});
});
