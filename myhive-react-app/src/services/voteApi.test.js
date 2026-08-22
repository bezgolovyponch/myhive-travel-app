import voteApi from './voteApi';

describe('voteApi.getActivities', () => {
  afterEach(() => {
    delete global.fetch;
  });

  test('throws "Vote session not found" on 404', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 404 });

    await expect(voteApi.getActivities('tok-1')).rejects.toThrow('Vote session not found');
  });

  test('throws the generic message on other errors', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 500 });

    await expect(voteApi.getActivities('tok-1')).rejects.toThrow('Failed to fetch vote activities');
  });
});

describe('createCartSession', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('POSTs the cart payload to /vote/sessions/cart', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1', voteMode: 'CART' }),
    });

    const payload = {
      destinationId: 'd-1',
      initiatorEmail: 'a@b.cz',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1', 'a-2'],
    };
    const session = await voteApi.createCartSession(payload);

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/vote/sessions/cart'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(payload) }),
    );
    expect(session.managerToken).toBe('m-1');
  });

  it('throws the backend message on failure', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'activityId x does not exist' }),
    });

    await expect(voteApi.createCartSession({ activityIds: [] }))
      .rejects.toThrow('activityId x does not exist');
  });

  it('forwards fbp/fbc/fbclid in the request body', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1' }),
    });

    await voteApi.createCartSession({
      destinationId: 'd-1',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1'],
      fbp: 'fb.1.1.2',
      fbc: 'fb.1.1.abc',
      fbclid: 'click-123',
    });

    const [, options] = global.fetch.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.fbp).toBe('fb.1.1.2');
    expect(body.fbc).toBe('fb.1.1.abc');
    expect(body.fbclid).toBe('click-123');
  });

  it('forwards groomName in the request body', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1' }),
    });

    await voteApi.createCartSession({
      destinationId: 'd-1',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1'],
      groomName: 'Tom',
    });

    const [, options] = global.fetch.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.groomName).toBe('Tom');
  });
});

describe('createSession forwards fbp/fbc/fbclid', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('includes fbp/fbc/fbclid in the request body', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1' }),
    });

    await voteApi.createSession({
      destinationId: 'd-1',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1'],
      fbp: 'fb.1.1.2',
      fbc: 'fb.1.1.abc',
      fbclid: 'click-123',
    });

    const [, options] = global.fetch.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.fbp).toBe('fb.1.1.2');
    expect(body.fbc).toBe('fb.1.1.abc');
    expect(body.fbclid).toBe('click-123');
  });

  it('forwards groomName in the request body', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 't-1', managerToken: 'm-1' }),
    });

    await voteApi.createSession({
      destinationId: 'd-1',
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1'],
      groomName: 'Tom',
    });

    const [, options] = global.fetch.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.groomName).toBe('Tom');
  });
});

describe('getTally', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('passes voterToken and managerToken as query params', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ participantCount: 3, rows: [] }),
    });

    await voteApi.getTally('t-1', { voterToken: 'v-1', managerToken: 'm-1' });

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('/vote/sessions/t-1/tally?');
    expect(url).toContain('voterToken=v-1');
    expect(url).toContain('managerToken=m-1');
  });

  it('throws a friendly error on 403', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 403 });

    await expect(voteApi.getTally('t-1', { voterToken: 'v-1' }))
      .rejects.toThrow('Vote first to see the live tally');
  });
});
