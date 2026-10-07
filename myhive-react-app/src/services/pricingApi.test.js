import pricingApi from './pricingApi';

afterEach(() => {
  delete global.fetch;
});

test('quote POSTs the plan lines with their package and the head-count', async () => {
  global.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ fromPrice: 1492, fromPricePerPerson: 150 }) });
  const items = [{ activityId: 'a-1', packageId: 'pkg-1' }, { activityId: 'a-2', packageId: null }];

  const quote = await pricingApi.quote({ items, travelers: 10 });

  expect(global.fetch).toHaveBeenCalledWith(
    expect.stringContaining('/pricing/quote'),
    expect.objectContaining({ method: 'POST', body: JSON.stringify({ items, travelers: 10 }) }),
  );
  expect(quote).toEqual({ fromPrice: 1492, fromPricePerPerson: 150 });
});

test('quote throws when the server declines', async () => {
  global.fetch = jest.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({}) });

  await expect(pricingApi.quote({ items: [], travelers: 10 })).rejects.toThrow('Failed to price the plan');
});
