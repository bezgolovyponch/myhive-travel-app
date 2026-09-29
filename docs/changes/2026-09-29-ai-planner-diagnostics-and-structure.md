# AI planner: diagnostics, strict schema, UI presets, suggested replies, keyword copy

Branch: `feat/ai-planner-diagnostics-and-structure` → `main` · 2 code commits (51 files, +1222 / −93) plus this summary

Code commits:
- `9239671` feat(ai-planner): rejection diagnostics, strict JSON schema, includes, nights, explicit slot windows
- `8412161` feat(ai-planner): UI presets, suggested replies, catalog pairing follow-ups, keyword copy

The research results behind the second commit are in
[`docs/concepts/2026-09-29-ai-planner-package-concept.md`](../concepts/2026-09-29-ai-planner-package-concept.md).

## Why

- A degraded run in the admin bench said "the model broke a scheduling rule twice". The same text was also shown when
  the model call had timed out, been unreachable or returned unparseable JSON, and the real reason was only in the
  server log.
- Group size and dates will be chosen in the UI, so the chat should start on taste instead of asking for them.
- In a sample of 33 ready-made packages from 4 operators, most follow one pattern: a night out (29/33), one daytime
  activity that names the package, and a centrepiece with an extra (dinner with a show, boat with unlimited drinks).
  The chat now asks about those variants with one plain question and offers chips.
- Package copy was prose; it is now scannable keyword lines.

## What changed

### 1. Rejection diagnostics (staff only)
- Every rejected planner draft is kept in an attempt log: the attempt number, the model call's error code
  (`LLM_TIMEOUT`, `LLM_UNAVAILABLE`, `LLM_INVALID_OUTPUT`, `INTERNAL`) and the validator's violations.
- It is stored in the new column `ai_generations.diagnostics` and served as `GenerationDTO.diagnostics` to staff tokens only; everyone else gets `null`.
- The admin bench prints one line per rejected draft instead of the fixed sentence.

### 2. Strict JSON schema for planner and copy calls
- The planner calls (compose and repair) and the package-copy call send `response_format: json_schema` with `strict: true`. In the planner schema,
  `activityId` can only be one of the catalog codes (`A1…An`), and tiers and slots are limited to their allowed values.
- The chat turn and text refresh keep `json_object`.
- Switch: `app.ai.strict-schema` (`AI_STRICT_SCHEMA`, default `true`). Set it to `false` to go back to `json_object`.

### 3. Package output
- `Package.nights` = days − 1. It is `null` on packages stored before this change.
- `Item.includes`: the activity's catalog "what is included" text, in the plan's language. It is also passed to the planner and copy prompts.

### 4. Planner prompt
- Lists the allowed slots for every day (e.g. `day 1: EVENING, NIGHT; day 2: …; day 3: MORNING`), taken from
  `PlanValidator.allowedSlots` so the prompt and the validator can't disagree.

### 5. UI presets on session start
- `POST /ai/sessions` accepts the optional fields `days` (1–7), `groupSize` (2–30), `arrival` and `departure`.
- The values land in the brief and the chat never asks for them again.
- With `days` and `groupSize` set, the greeting is "Hey! 3 days, 8 people - noted. What should the weekend be built around?".

### 6. Suggested replies
- `SessionState.suggestedReplies` and `TurnResponse.suggestedReplies`: 0–4 tap-to-send chips, each ≤ 60 characters, never `null`.
- Opening chips are the most repeated competitor bundles, in this order: night out 29/33 packages, shooting 13/33,
  dinner with a show 11/33, prank 10/33. A chip is only shown if the destination has the categories to deliver it
  (dinner with a show needs `dining` plus `show` or `adult`). At most 4 are shown; karting (7/33) only fills in when an
  earlier one is unavailable.
- After that, the model writes the chips, and they answer the question it just asked.

### 7. Pairing follow-up
- The chat sees the catalog names from the first turn; the catalog is snapshotted when the session is created.
- When the organizer picks a centrepiece that the catalog sells in variants, the chat asks one either/or question
  naming those catalog variants ("Dinner - just steak and beers, or with a show?").

### 8. Keyword copy
- Package copy, copy after an edit, and chat replies use keyword lines, e.g. `• Karting - 2 × 10 min`, `Karting → Steak dinner → Club`.
  Hype words, emoji and markdown are banned. Inclusions may only be stated when the catalog's `includes` says so.
- `PlanAssembler.clean` now keeps single line breaks and still strips tags and extra whitespace.

### 9. Admin bench (`myhive-react-app`)
- Pickers for days, group size, arrival and departure next to "New chat".
- The server's suggested replies are shown as chips that send themselves; the old test prompts stay as a separate row.
- Descriptions render line breaks; cards show nights and each item's includes.

## API changes (all additive)

| Where | Field |
|---|---|
| `POST /ai/sessions` body | `days`, `groupSize`, `arrival`, `departure` (optional) |
| `SessionState`, `TurnResponse` | `suggestedReplies: string[]` |
| `Generation` | `diagnostics` (staff only, otherwise `null`) |
| `Package` | `nights` |
| `Package.days[].items[]` | `includes` |

`docs/api/ai-planner-api.md` is updated. The "`suggestedReplies` will arrive in a later version" note is removed.

## Database

- `V9__ai_generation_diagnostics.sql`: `ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS diagnostics TEXT;`

## Configuration

- New: `app.ai.strict-schema=${AI_STRICT_SCHEMA:true}`.

## Tests

- Backend: `./gradlew test`: 1137 tests, 0 failed, 2 skipped (19 new).
- Admin bench: `npx react-scripts test src/pages/AdminAiPlanner.test.js`: 7 passed (2 new).

## Not verified

- **No live Qwen run.** No `QWEN_API_KEY` was available, so strict `json_schema` (including `enum` on
  `activityId`) has not been run against DashScope. If the endpoint rejects it, set `AI_STRICT_SCHEMA=false`.
- **Chat cost:** the chat turn now carries up to 80 catalog names; its latency and token cost haven't been measured.
- **Seed catalog:** the Prague seed catalog is sightseeing, not stag products, so pairing follow-ups only appear with a
  catalog that has variant products.
- **Chip order** follows how often competitors repeat each bundle, not conversion data. The concept doc lists what to measure.

## How to try it

1. In `myhive-backend/.env` set `AI_ENABLED=true` and `QWEN_API_KEY` (plus `QWEN_BASE_URL` if needed).
2. Start the backend (`sh ./gradlew bootRun`) and open the admin console → AI planner.
3. Set 3 days, 8 people, arrival evening, departure morning → **New chat**. Expected: the taste greeting and the opening chips.
4. Tap a chip; you should get one question and 2–4 new chips.
5. Once packages are built, check that the copy is keyword lines, that cards show nights and includes, and that a degraded run
   prints one line per rejected draft.

## Not in this PR

- The package-structure suggestions from the concept doc (one skeleton per tier, variant ladders, accommodation/transfers).
  They are listed there and not applied.
- The generation-performance suggestions (P1–P6 in the concept doc). Among them P2: the job's worst case (160 s) is
  longer than the 90 s the API doc tells the frontend to poll, and this PR does not change that.
- Packages are not yet named after their hero activity (concept item 3); the copy prompt only asks for keyword titles.
- The concept doc's "earlier audit, D1/D2" is on the unmerged branch `audit/ai-package-generator-vs-competitors`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
