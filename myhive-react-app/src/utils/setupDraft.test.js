import {readSetupDraft, writeSetupDraft, clearSetupDraft} from './setupDraft';

afterEach(() => localStorage.clear());

test('round-trips a draft', () => {
    writeSetupDraft({travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06'});
    expect(readSetupDraft()).toEqual({travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06'});
});

test('returns null when empty or malformed', () => {
    expect(readSetupDraft()).toBeNull();
    localStorage.setItem('myhive-setup-draft', '{not json');
    expect(readSetupDraft()).toBeNull();
});

test('clearSetupDraft removes the draft', () => {
    writeSetupDraft({travelers: 2, startDate: '', endDate: ''});
    clearSetupDraft();
    expect(readSetupDraft()).toBeNull();
});
