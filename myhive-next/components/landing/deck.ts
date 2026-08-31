// Pure state for the hero swipe deck: which card is on top, nothing else.
// The picks themselves live in the real trip cart (TripContext) — the same list
// the header badge, the cart panel and the trip builder read — so the deck no
// longer keeps a private copy of them.

export interface DeckState {
  cursor: number;
}

export type DeckAction = { type: 'swipe'; yes: boolean; id: string } | { type: 'reset' };

export function initialDeck(): DeckState {
  return { cursor: 0 };
}

// `yes`/`id` stay on the swipe action: VoteLanding wraps this reducer to add the
// card to the cart on a right-swipe, and needs to know which card and which way.
export function deckReducer(state: DeckState, action: DeckAction): DeckState {
  switch (action.type) {
    case 'swipe':
      return { cursor: state.cursor + 1 };
    case 'reset':
      return initialDeck();
  }
}

export function isDeckFinished(state: DeckState, deckSize: number): boolean {
  return state.cursor >= deckSize;
}
