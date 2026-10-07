# 2026-10-07 — AI planner: the recommendations above the composer are a sheet with three states

## Why

On `/plan` the recommendations above the chat input showed the top match as a card and the rest as a
row of "+ name" chips with a hidden scrollbar. On a desktop nothing said there was more than one, nor
that the row scrolled; there was no way to put the block away; and on a phone it took the room the chat
needs. The owner asked for: a visible "there is more" with scrolling on desktop, a Hide, and a drag up /
down on mobile.

## Where the recommendations come from (unchanged)

Three sources, in this order of precedence (`AiPlannerPage`, `offered`):

1. **The answer to a "+ Add shooting" tag** — `DraftGaps` (Java, no model): categories the trim lacks;
   the tapped answer becomes the card, the rest of its kind the others.
2. **The chat's recommendations** (`planner.recommendations`) — after a message under the draft: a typed
   "add X" is not carried out; `ChatTurnNode` resolves X, the model's alternatives for a missing X and
   the model's own suggestions against the catalog snapshot, and `AiDtoMapper.recommendations` fills up
   to four with activities of the top match's category.
3. **The default suggestions** (`planner.suggestions[trim]`) — `DraftSuggestions` (Java, no model):
   activities of the ready-made preset packages the trim does not hold yet, own tier first, each dry-run
   through the editor so it fits a free slot.

All three are capped at four.

## What changed

`AiRecommendations` is a sheet with three states, owned by the component:

- **compact** (default): a header "Recommendations (4)" with a grip, the top match as a card with Add,
  and a "3 more" button instead of the chip row.
- **expanded**: every recommendation as a card with Add, in a list capped at `min(240px, 40dvh)` with a
  **visible** thin scrollbar — the composer and a few lines of chat stay on screen.
- **hidden**: the header line alone, with "Show".

The header is the handle: a tap toggles compact ↔ expanded, "Hide"/"Show" fold to the line and back,
and a swipe up or down on it (pointer events, 24 px threshold, no library) steps through
hidden → compact → expanded. The header captures the pointer **only once it has moved 6 px** (a drag),
so a mouse drag that leaves it still counts — captured on `pointerdown`, as the first cut did, Chrome
retargets the click to the capturing element and every tap on Hide/Show landed on the header div and did
nothing (seen live after the first deploy, confirmed on a minimal page). The click a browser fires right
after a swipe (within 400 ms, whichever header button it lands on) is the swipe's tail and is ignored,
while a later activation — a keyboard Enter after a touch swipe, which has no tail click — is not.
`touch-action: none` on the header only, so the page does not scroll under the gesture. The expanded list
ends with "Show less", the explicit way back to the top match (the header toggle does the same).

A **new offer** opens compact again, hidden or not: the chat's "the top match is above" has to point at
something. What counts as a new offer is the page's call (`resetKey`: a message sent under the draft, a
tag answer, another trim), never the list itself — an Add takes the added activity out of the default
suggestions, and that must not fold the open list under the organiser's finger. The reset is derived
while rendering, so a new offer never paints a frame in the old state. Every card is still a toggle
(Add / "Added ✓" to take it out), with no chat turn in between.

i18n (`aiPlanner.result`): `suggestionsCount`, `moreCount`, `hide`, `show` in EN and DE.

## Tests

`AiRecommendations.test.js` (new): compact / expanded / hidden, the reset on a new set, "Added ✓" in both
views, the swipe steps and the swallowed click, nothing rendered for an empty set. `AiPlannerPage.test.js`:
the two flows that reached the second recommendation now expand the sheet first.
