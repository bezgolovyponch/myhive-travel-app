import { describe, it, expect } from 'vitest';
import { initialDeck, deckReducer, isDeckFinished } from '../deck';

const IDS = ['a', 'b', 'c'];

describe('deck reducer', () => {
  it('starts at the first card with nothing picked', () => {
    const s = initialDeck();
    expect(s.cursor).toBe(0);
    expect(s.picked).toEqual([]);
    expect(isDeckFinished(s, IDS.length)).toBe(false);
  });

  it('swiping right picks the current card and advances', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: true, id: 'a' });
    expect(s.picked).toEqual(['a']);
    expect(s.cursor).toBe(1);
  });

  it('swiping left advances without picking', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: false, id: 'a' });
    expect(s.picked).toEqual([]);
    expect(s.cursor).toBe(1);
  });

  it('finishes after the last card', () => {
    let s = initialDeck();
    for (const id of IDS) s = deckReducer(s, { type: 'swipe', yes: true, id });
    expect(isDeckFinished(s, IDS.length)).toBe(true);
    expect(s.picked).toEqual(IDS);
  });

  it('toggle adds and removes ids without moving the cursor (catalogue cards)', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'toggle', id: 'x' });
    expect(s.picked).toEqual(['x']);
    s = deckReducer(s, { type: 'toggle', id: 'x' });
    expect(s.picked).toEqual([]);
    expect(s.cursor).toBe(0);
  });

  it('a picked id is not duplicated by toggle after swipe', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: true, id: 'a' });
    s = deckReducer(s, { type: 'toggle', id: 'a' });
    expect(s.picked).toEqual([]);
  });

  it('reset clears picks and rewinds the deck', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: true, id: 'a' });
    s = deckReducer(s, { type: 'reset' });
    expect(s).toEqual(initialDeck());
  });
});
