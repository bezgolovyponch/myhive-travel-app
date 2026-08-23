// Pure state for the hero swipe deck and the "Add to trip" catalogue toggles.
// One picked list serves both: the header cart, step 1's done flag, the sticky
// bar and the final CTA all read from it.

export interface DeckState {
  cursor: number;
  picked: string[]; // activity slugs, in pick order
}

export type DeckAction =
  | { type: 'swipe'; yes: boolean; id: string }
  | { type: 'toggle'; id: string }
  | { type: 'reset' };

export function initialDeck(): DeckState {
  return { cursor: 0, picked: [] };
}

export function deckReducer(state: DeckState, action: DeckAction): DeckState {
  switch (action.type) {
    case 'swipe':
      return {
        cursor: state.cursor + 1,
        picked:
          action.yes && !state.picked.includes(action.id)
            ? [...state.picked, action.id]
            : state.picked,
      };
    case 'toggle':
      return {
        ...state,
        picked: state.picked.includes(action.id)
          ? state.picked.filter((p) => p !== action.id)
          : [...state.picked, action.id],
      };
    case 'reset':
      return initialDeck();
  }
}

export function isDeckFinished(state: DeckState, deckSize: number): boolean {
  return state.cursor >= deckSize;
}
