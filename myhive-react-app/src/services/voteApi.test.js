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
});

describe('getTally', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('passes the managerToken as a query param', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ participantCount: 3, rows: [] }),
    });

    await voteApi.getTally('t-1', { managerToken: 'm-1' });

    const url = global.fetch.mock.calls[0][0];
    expect(url).toContain('/vote/sessions/t-1/tally?');
    expect(url).toContain('managerToken=m-1');
    expect(url).not.toContain('voterToken');
  });

  it('throws on 403: only the organiser sees the tally', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 403 });

    await expect(voteApi.getTally('t-1', {}))
      .rejects.toThrow('Only the organiser sees the live tally');
  });
});

describe('organiser edits on a running vote', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('drops, restores and adds activities with the encoded manager token', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: true });

    await voteApi.excludeActivity('t-1', 'm&1', 'a-1');
    await voteApi.restoreActivity('t-1', 'm&1', 'a-1');
    await voteApi.addActivity('t-1', 'm&1', 'a-2');

    const calls = global.fetch.mock.calls;
    expect(calls[0][0]).toMatch(/\/vote\/sessions\/t-1\/activities\/a-1\/exclude\?managerToken=m%261$/);
    expect(calls[1][0]).toMatch(/\/activities\/a-1\/restore\?managerToken=m%261$/);
    expect(calls[2][0]).toMatch(/\/vote\/sessions\/t-1\/activities\?managerToken=m%261$/);
    expect(JSON.parse(calls[2][1].body)).toEqual({ activityId: 'a-2' });
  });

  it('adds the organiser\'s other contact with PATCH', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: true });

    await voteApi.updateContact('t-1', 'm-1', { initiatorEmail: 'max@example.com' });

    expect(global.fetch.mock.calls[0][1].method).toBe('PATCH');
    expect(global.fetch.mock.calls[0][0]).toContain('/vote/sessions/t-1/contact?managerToken=m-1');
  });
});

describe('castVotes', () => {
  afterEach(() => {
    delete global.fetch;
  });

  it('sends the votes and the recommendations together', async () => {
    global.fetch = jest.fn().mockResolvedValue({ ok: true });

    await voteApi.castVotes('t-1', {
      voterToken: 'v-1', votes: [{ activityId: 'a-1', liked: true }], recommendedActivityIds: ['a-9'],
    });

    expect(JSON.parse(global.fetch.mock.calls[0][1].body)).toEqual({
      voterToken: 'v-1', votes: [{ activityId: 'a-1', liked: true }], recommendedActivityIds: ['a-9'],
    });
  });

  it('tells a second ballot apart from a full vote', async () => {
    global.fetch = jest.fn()
        .mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({ message: 'You have already voted' }) })
        .mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({ message: 'Session has reached the maximum' }) });

    await expect(voteApi.castVotes('t-1', { voterToken: 'v', votes: [] })).rejects.toThrow('Already voted');
    await expect(voteApi.castVotes('t-1', { voterToken: 'v', votes: [] })).rejects.toThrow('Session is full');
  });
});
