import { pushEvent } from './analytics';

beforeEach(() => { window.dataLayer = []; });

test('pushes event with generated event_id and explicit context', () => {
  pushEvent('vote_opened', { trip_id: 'st-123', user_role: 'participant' });
  const e = window.dataLayer[0];
  expect(e.event).toBe('vote_opened');
  expect(e.trip_id).toBe('st-123');
  expect(e.user_role).toBe('participant');
  expect(e.event_id).toMatch(/^[0-9a-f-]{36}$/);
});

test('keeps false/0, drops undefined/null/empty', () => {
  pushEvent('tb_group_submitted', { has_budget: false, value: 0, ref: undefined, missing: null, blank: '' });
  const e = window.dataLayer[0];
  expect(e.has_budget).toBe(false);
  expect(e.value).toBe(0);
  expect('ref' in e).toBe(false);
  expect('missing' in e).toBe(false);
  expect('blank' in e).toBe(false);
});
