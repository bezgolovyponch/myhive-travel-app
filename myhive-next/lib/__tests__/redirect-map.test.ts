import { describe, expect, it } from 'vitest';
import { loadRedirectMap, parseRedirectMap } from '../redirect-map';

const HEADER = 'type,old_url_en,new_url_en,old_url_de,new_url_de,category_en,category_de,note';

describe('redirect map', () => {
  it('parseRedirectMap_acceptsCrlf', () => {
    const expectedRow = 'hub,/destination/prague,/prague-stag-do,/de/destination/prague,/de/jga-prag,,,301';
    const csv = [HEADER, expectedRow, 'landing,/landing/prague,/landing/prague,/de/landing/prague,/de/landing/prague,,,landing'].join('\r\n');

    const rows = parseRedirectMap(csv);

    expect(rows).toHaveLength(2);
    expect(rows[0]).toEqual({
      type: 'hub',
      oldEn: '/destination/prague',
      newEn: '/prague-stag-do',
      oldDe: '/de/destination/prague',
      newDe: '/de/jga-prag',
      categoryEn: '',
      categoryDe: '',
      note: '301',
    });
    expect(rows[1].note).toBe('landing');
  });

  it('parseRedirectMap_rejectsQuotedCell', () => {
    const csv = [HEADER, 'hub,"/destination/prague",/prague-stag-do,/de/destination/prague,/de/jga-prag,,,301'].join('\n');

    expect(() => parseRedirectMap(csv)).toThrow(/quoted/);
  });

  it('loadRedirectMap_readsCommittedFile', () => {
    const rows = loadRedirectMap();

    expect(rows).toHaveLength(81);
    expect(rows.filter((row) => row.type === 'activity')).toHaveLength(72);
  });
});
