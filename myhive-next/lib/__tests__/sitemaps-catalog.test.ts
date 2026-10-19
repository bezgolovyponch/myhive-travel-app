import { describe, expect, it, vi } from 'vitest';

vi.mock('../api', () => ({
  api: {
    getDestinations: vi.fn().mockResolvedValue([{ id: 'd1', slug: 'prague', seoIndexable: true }]),
    getActivities: vi.fn().mockRejectedValue(new Error('Backend 503 on /activities')),
    getPackages: vi.fn().mockResolvedValue([]),
    getBlogPosts: vi.fn().mockResolvedValue([]),
  },
}));

import { loadCatalog } from '../sitemaps';

describe('loadCatalog', () => {
  it('loadCatalog_propagatesBackendFailure', async () => {
    const expectedMessage = 'Backend 503 on /activities';

    await expect(loadCatalog()).rejects.toThrow(expectedMessage);
  });
});
