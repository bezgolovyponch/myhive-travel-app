# 2026-10-07 — AI planner: a tap on a trim switches, it no longer "picks"

## What was wrong

On `/plan`, once the three trims (Basic / Medium / Premium) were on screen, the first tap on any trim tab
switched to it **and took the other two tabs away**. The owner's report: "switching between packages does
not work — tapping any package selects it straight away". Nothing was booked (`POST …/select` runs only on
"Ask the group"), but with the tabs gone the organiser could not compare the trims any more, and the dock
line "Pick one to make it your trip plan" told them a tap had committed them.

The behaviour came from the trip-draft redesign (PR #36): `pickTier` set the `chatted` flag, which also
means "the organiser has started changing the plan", and `custom = chatted && !showAll` hides the trim
tabs. A tap was counted as a change. The tests of that PR pinned it ("the other trims leave the screen").

## What changed

- `AiPlannerPage.pickTier` only sets the active trim. The tabs stay until the organiser actually changes
  the plan — a message under the draft, an edit (×, Add, "+ Add …") — or the chat is asked for one trim
  ("show me Premium"), which is a message and so counts as before. "What were the other options?" still
  brings every trim back.
- The trim on screen still goes with every message (`packageKey`), so a message after tapping Premium
  edits Premium, as before.
- Dock copy, EN/DE: "Your three options are ready. Switch between them, or tell me what to change." /
  "Deine drei Optionen sind fertig. Schau sie dir an oder sag mir, was anders sein soll."

## Tests

`AiPlannerPage.test.js`: tapping Premium, then Basic, keeps the tablist, moves `aria-selected` and the
draft, and never calls `selectPackage`; the dock keeps the options line until something changes; the
"show me Premium" test now expects the tabs to survive a tap and to go only with the message.
