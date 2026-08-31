import { describe, it, expect } from 'vitest';
import { initialDeck, deckReducer, isDeckFinished } from '../deck';

const IDS = ['a', 'b', 'c'];

// Picks are the cart's job now (TripContext), so they are not asserted here —
// only the cursor the deck itself owns.
describe('deck reducer', () => {
  it('starts at the first card', () => {
    const s = initialDeck();
    expect(s.cursor).toBe(0);
    expect(isDeckFinished(s, IDS.length)).toBe(false);
  });

  it('advances on a swipe either way', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: true, id: 'a' });
    expect(s.cursor).toBe(1);
    s = deckReducer(s, { type: 'swipe', yes: false, id: 'b' });
    expect(s.cursor).toBe(2);
  });

  it('finishes after the last card', () => {
    let s = initialDeck();
    for (const id of IDS) s = deckReducer(s, { type: 'swipe', yes: true, id });
    expect(isDeckFinished(s, IDS.length)).toBe(true);
  });

  it('reset rewinds the deck', () => {
    let s = initialDeck();
    s = deckReducer(s, { type: 'swipe', yes: true, id: 'a' });
    s = deckReducer(s, { type: 'reset' });
    expect(s).toEqual(initialDeck());
  });
});
