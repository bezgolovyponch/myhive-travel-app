import fs from 'node:fs';
import path from 'node:path';

export interface MapRow {
  type: 'hub' | 'activity' | 'landing' | 'blog-index' | 'blog';
  oldEn: string;
  newEn: string;
  oldDe: string;
  newDe: string;
  categoryEn: string;
  categoryDe: string;
  note: '301' | '410' | 'landing';
}

const FIELD_COUNT = 8;
const TYPES: readonly string[] = ['hub', 'activity', 'landing', 'blog-index', 'blog'];
const NOTES: readonly string[] = ['301', '410', 'landing'];

function isType(value: string): value is MapRow['type'] {
  return TYPES.includes(value);
}

function isNote(value: string): value is MapRow['note'] {
  return NOTES.includes(value);
}

// Same rules as the backend parser: 8 comma-separated fields, no quoting
// (a `"` is rejected), CRLF tolerated, first line is the header.
export function parseRedirectMap(csv: string): MapRow[] {
  const rows: MapRow[] = [];
  const lines = csv.split('\n').map((line) => line.replace(/\r$/, ''));
  lines.slice(1).forEach((line, index) => {
    if (line.trim() === '') {
      return;
    }
    const where = `redirect-map.csv line ${index + 2}`;
    if (line.includes('"')) {
      throw new Error(`${where}: quoted cells are not supported`);
    }
    const fields = line.split(',');
    if (fields.length !== FIELD_COUNT) {
      throw new Error(`${where}: expected ${FIELD_COUNT} fields, got ${fields.length}`);
    }
    const [type, oldEn, newEn, oldDe, newDe, categoryEn, categoryDe, note] = fields;
    if (!isType(type)) {
      throw new Error(`${where}: unknown type '${type}'`);
    }
    if (!isNote(note)) {
      throw new Error(`${where}: unknown note '${note}'`);
    }
    rows.push({ type, oldEn, newEn, oldDe, newDe, categoryEn, categoryDe, note });
  });
  return rows;
}

// cwd is myhive-next for `next build`, `next dev` and vitest.
export function loadRedirectMap(): MapRow[] {
  return parseRedirectMap(fs.readFileSync(path.join(process.cwd(), 'seo', 'redirect-map.csv'), 'utf8'));
}

export function slugOf(url: string): string {
  return url.slice(url.lastIndexOf('/') + 1);
}
