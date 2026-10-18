import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  activityUpdate,
  BEGIN_MARKER,
  blogUpdates,
  devBlock,
  END_MARKER,
  parseMap,
  prodSql,
  rekey,
  renameMap,
  replaceBlock,
  validateRows,
  withEol,
} from './seo-map-to-sql.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const csvPath = path.resolve(here, '..', '..', 'myhive-next', 'seo', 'redirect-map.csv');
const realRows = () => parseMap(readFileSync(csvPath, 'utf8'));

test('parseMap_readsCrlfAndRejectsQuotes', () => {
  const header = 'type,old_url_en,new_url_en,old_url_de,new_url_de,category_en,category_de,note';
  const row = 'activity,/a/old,/b/new,/de/a/old,/de/b/neu,shooting,schiessen,301';
  const rows = parseMap(`${header}\r\n${row}\r\n`);
  assert.equal(rows.length, 1);
  const expected = {
    type: 'activity',
    oldEn: '/a/old',
    newEn: '/b/new',
    oldDe: '/de/a/old',
    newDe: '/de/b/neu',
    categoryEn: 'shooting',
    categoryDe: 'schiessen',
    note: '301',
  };
  assert.deepEqual(rows[0], expected);
  assert.throws(() => parseMap(`${header}\n"${row}"\n`), /quote/i);
  assert.throws(() => parseMap(`${header}\nactivity,/a,/b\n`), /8/);
});

test('validateRows_acceptsTheCommittedMap', () => {
  const rows = realRows();
  assert.equal(rows.length, 81);
  assert.doesNotThrow(() => validateRows(rows));
});

test('validateRows_rejectsDuplicateNewSlug', () => {
  const rows = realRows();
  const activities = rows.filter((r) => r.type === 'activity');
  activities[1].newEn = activities[0].newEn;
  assert.throws(() => validateRows(rows), /duplicate/i);
});

test('validateRows_rejectsOldSlugEqualToANewSlug', () => {
  const rows = realRows();
  const activities = rows.filter((r) => r.type === 'activity');
  activities[0].oldEn = '/destination/prague/activity/zzz-unique-old-slug';
  assert.doesNotThrow(() => validateRows(rows));
  const victim = activities[1].newEn.split('/').pop();
  activities[0].oldEn = `/destination/prague/activity/${victim}`;
  assert.throws(() => validateRows(rows), /live page|old/i);
});

test('activityUpdate_isIdempotentOnOldOrNewSlug', () => {
  const row = realRows().find((r) => r.newEn.endsWith('/ak47-shooting'));
  const sql = activityUpdate(row);
  assert.match(sql, /slug IN \('ak-47-glock-17-shooting', 'ak47-shooting'\)/);
  assert.match(sql, /slug_de = 'kalaschnikow-schiessen'/);
  assert.match(sql, /WHERE slug = 'shooting'/);
  assert.match(sql, /updated_at = CURRENT_TIMESTAMP/);
});

test('prodSql_wrapsInTransactionAndDeletesRetiredPosts', () => {
  const sql = prodSql(realRows());
  assert.ok(sql.startsWith('-- ') || sql.includes('BEGIN;'));
  assert.match(sql, /^BEGIN;$/m);
  assert.match(sql.trimEnd(), /COMMIT;$/);
  assert.equal(sql.match(/^UPDATE activities /gm).length, 72);
  assert.match(sql, /UPDATE blog_posts SET slug = 'how-to-plan-a-stag-do', slug_de = 'jga-planen'/);
  assert.match(sql, /DELETE FROM blog_posts WHERE slug IN \(.*'top-5-group-travel-destinations-for-2026'.*'weekend-getaway-ideas-for-large-groups'\)/);
});

test('rekey_renamesOnlyMappedKeys', () => {
  const input = {
    activities: { old: { name: 'a' }, keep: { name: 'b' } },
    blog: { oldPost: { title: 'c' }, other: { title: 'd' } },
    categories: { old: { name: 'untouched' } },
  };
  const renames = new Map([
    ['old', 'new'],
    ['oldPost', 'newPost'],
  ]);
  const out = rekey(input, renames);
  assert.deepEqual(Object.keys(out.activities), ['new', 'keep']);
  assert.deepEqual(Object.keys(out.blog), ['newPost', 'other']);
  assert.deepEqual(Object.keys(out.categories), ['old']);
  assert.deepEqual(Object.keys(input.activities), ['old', 'keep']);
});

const activitiesOf = (rows) => rows.filter((r) => r.type === 'activity');

test('validateRows_rejectsWrongActivityCount', () => {
  const rows = realRows();
  rows.splice(rows.findIndex((r) => r.type === 'activity'), 1);
  assert.throws(() => validateRows(rows), /72 activity rows/);
});

test('validateRows_rejectsDuplicateNewDeSlug', () => {
  const rows = realRows();
  const activities = activitiesOf(rows);
  activities[1].newDe = activities[0].newDe;
  assert.throws(() => validateRows(rows), /duplicate new DE slug/);
});

test('validateRows_rejectsDeSlugEqualToADifferentRowsEnSlug', () => {
  const rows = realRows();
  const activities = activitiesOf(rows);
  activities[1].newDe = activities[0].newEn;
  assert.throws(() => validateRows(rows), /different row's new EN slug/);
});

test('validateRows_rejectsUnknownCategory', () => {
  const rows = realRows();
  activitiesOf(rows)[0].categoryEn = 'museums';
  assert.throws(() => validateRows(rows), /unknown category_en "museums"/);
});

test('validateRows_rejectsWrongCategoryCount', () => {
  const rows = realRows();
  const activities = activitiesOf(rows);
  const from = activities.find((r) => r.categoryEn === 'shooting');
  from.categoryEn = 'driving';
  assert.throws(() => validateRows(rows), /category (shooting|driving): expected \d+ activities/);
});

test('devBlock_hasMarkersAndDestinationLineWithoutTransaction', () => {
  const block = devBlock(realRows());
  assert.ok(block.startsWith(`${BEGIN_MARKER}\n`));
  assert.ok(block.endsWith(END_MARKER));
  assert.ok(block.includes("UPDATE destinations SET seo_indexable = TRUE WHERE slug = 'prague';"));
  assert.doesNotMatch(block, /^(BEGIN|COMMIT);$/m);
  assert.equal(block.match(/^UPDATE activities /gm).length, 72);
});

test('replaceBlock_replacesInPlaceAndIsIdempotent', () => {
  const original = 'INSERT 1;\r\nINSERT 2;\r\n';
  const appended = replaceBlock(original, `${BEGIN_MARKER}\nold\n${END_MARKER}`);
  assert.ok(appended.startsWith(original.trimEnd()));
  const replaced = replaceBlock(`${appended}-- tail\r\n`, `${BEGIN_MARKER}\nnew\n${END_MARKER}`);
  assert.ok(replaced.includes('\r\nnew\r\n'));
  assert.ok(!replaced.includes('old'));
  assert.ok(replaced.endsWith('-- tail\r\n'));
  assert.equal(replaced.split(BEGIN_MARKER).length, 2);
  assert.equal(replaceBlock(replaced, `${BEGIN_MARKER}\nnew\n${END_MARKER}`), replaced);
});

test('replaceBlock_appendsWhenAbsentAndKeepsEol', () => {
  const block = devBlock(realRows());
  const crlf = replaceBlock('a;\r\n', block);
  assert.ok(crlf.includes(BEGIN_MARKER));
  assert.equal(crlf.replace(/\r\n/g, '').includes('\n'), false);
  const lf = replaceBlock('a;\n', block);
  assert.equal(lf.includes('\r'), false);
  assert.equal(replaceBlock(crlf, block), crlf);
});

test('withEol_defaultsToCrlfAndFollowsExistingFile', () => {
  assert.equal(withEol('a\nb\n', null), 'a\r\nb\r\n');
  assert.equal(withEol('a\nb\n', 'x\ny\n'), 'a\nb\n');
  assert.equal(withEol('a\nb\n', 'x\r\ny\r\n'), 'a\r\nb\r\n');
});

test('renameMap_mapsActivitiesAndTheKeptPost', () => {
  const renames = renameMap(realRows());
  assert.equal(renames.get('ak-47-glock-17-shooting'), 'ak47-shooting');
  assert.equal(renames.get('how-to-plan-a-stress-free-group-trip'), 'how-to-plan-a-stag-do');
});

test('blogUpdates_renamesKeptPostAndDeletesRetired', () => {
  const sql = blogUpdates();
  assert.match(sql, /slug = 'how-to-plan-a-stag-do', slug_de = 'jga-planen', updated_at = CURRENT_TIMESTAMP/);
  assert.match(sql, /WHERE slug IN \('how-to-plan-a-stress-free-group-trip', 'how-to-plan-a-stag-do'\)/);
  assert.equal(sql.match(/DELETE FROM blog_posts WHERE slug IN \(([^)]*)\)/)[1].split(', ').length, 5);
});
