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
