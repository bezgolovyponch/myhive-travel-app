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

## Fixes after the first live run (2026-09-29)

The branch was run against the model (OpenRouter, `qwen/qwen3.7-plus`) and the production catalog was checked:

- **The chat asked while the build had already started: 4 runs out of 4.** With presets set, the first taste message
  completes the brief and Java starts the generation, but the reply still asked a question and offered chips (three
  times about the arrival and departure the presets had set). `ChatTurnNode` now drops the chips on the turn that
  starts a generation and replaces a reply that asks something with a stock "building your three options" line.
- **The pairing follow-up never got its turn**, for the same reason. The first build is now held back once for a
  question about catalog variants, recognised by its chips: at least two of them name catalog activities. The next
  message starts the build whatever it says (`PlannerState.PAIRING_ASKED`).
- **Opening chips were empty on dev and would be on production.** They read the categories assigned to the
  destination, which Prague has none of in production, and the hooks used seed-data slugs (`shooting`, `dining`,
  `prank`, `driving`) where production has `guns-and-bullets`, `food-and-drink`, `stag-hot-babies-and-pranks`.
  `OpeningReplies` moved to `ai.catalog` and now reads the catalog snapshot: whole words of activity names and
  category slugs. The seed turn writes the chips next to its snapshot.
- **Guards:** the diagnostics write in `PersistResultNode` and the seed-time catalog snapshot can no longer throw
  out of a node; `includes` is cut to 200 characters like the one-liner (production texts run to 454).
- **Verified live:** strict `json_schema` is accepted through Spring AI (compose 7.6 s, 375 tokens); keyword copy
  cut the texts phase from 12-14 s to about 8 s.

## Drafts corrected in Java instead of thrown away (2026-09-29)

Every live generation had ended `degraded`. The model put more into a day than its tier allows (`DAY_OVER_MINUTES`,
most often PREMIUM day 2), the repair call returned a day just as long, and the whole plan was replaced by the
deterministic fallback - which knows the catalog but not the conversation. The explicit slot windows did not touch it.

- **`PlanTrimmer`** (`ai/plan`) corrects what is arithmetic rather than judgement: a day over its minutes or its
  item count, an activity listed twice, two activities in one slot, a slot outside the arrival/departure window, an
  id the catalog does not know. It restates no rule: `PlanValidator` says what is wrong and `PlanAssembler` whether
  the tiers still rise in price; the trimmer corrects one thing on the list and asks again.
- **`ValidateNode`** gives a failing draft to the trimmer before giving up on it. If the corrected draft passes the
  same two checks, it is the result: no repair call, no fallback, `degraded: false`. If it does not, the draft goes
  to the repair as the model wrote it, with every violation, exactly as before. The trimmer runs on the repair's
  draft as well. A correction that throws leaves the draft rejected - a node must never throw.
- **Which activity goes from a day that is too long**, in this order: the one that answers the organizer's request
  least; a daytime activity rather than the night out; one whose removal alone settles the day; one another tier
  offers too; the cheapest line. A removal is never made if it would leave a tier with nothing the others lack,
  empty a middle day, or put the tiers out of price order.
- **The organizer's request** is the categories they picked plus the words of their own description, read from
  `vibe` and from `notes` (the chat files it under either), without their endings ("clubbing" finds the Nightclub)
  and matched against activity names and category slugs; a word in the name counts double. Production has no
  categories assigned to Prague, so there the words are all there is.
- **Bottom up:** BASIC is corrected before MEDIUM before PREMIUM, so each tier is priced against what is left of
  the one below it.
- **A draft the model has to repair anyway** (a missing tier, a wrong day count, an empty middle day) is not
  trimmed first.
- **A slot conflict is a move, not a loss:** the activity goes to the nearest free slot of the day's window and is
  dropped only when there is none.
- **Diagnostics:** `AttemptDiagnostic.fixes` - one line per correction. A draft with fixes was kept, one without
  was rejected; `violations` still lists what was wrong with it as written. The admin bench prints
  `compose: kept, Java corrected 2 slip(s) — dropped … from PREMIUM day 2 (840 min in 4 activities; …)`. The
  backend logs the same at INFO (`planner draft corrected`), and the two ways a correction can fail:
  `planner draft not trimmed: nothing to take for …` and `planner draft corrected but still failing …`.
  No migration: `diagnostics` is a JSON column.

### Live runs

Same model (OpenRouter, `qwen/qwen3.7-plus`) and dev catalog as before; presets plus one taste message.

| | before | with the trimmer |
|---|---|---|
| generations | 5 | 37 |
| first draft over a cap | 5 | 37 |
| served `degraded` (fallback) | 5 | 1 |
| corrected without a repair call | - | 32 |
| corrected after one repair call | - | 4 |

READY after 13-26 s when the draft was corrected at once, 27-37 s when it took a repair call first.

The 37 are six batches run while the ranking was being settled; the last eight, on the code as committed, were
6 corrected at once, 1 after a repair, 1 fallback. What the batches changed:

- **The club night went first.** It is the longest activity of any day, so "one removal is enough" always picked
  it, for the very groups that had asked for a club. The request now outranks everything, and the night out
  outranks "one removal is enough". In the last batch the trimmer took the Nightclub out of no package (the
  second copy of one listed twice aside).
- **The chat does not always file the request under `vibe`**: one brief had `vibe: "wild"` and
  `notes: "strip club and a boat party"`, another `notes: "Wants karting (not available) and clubbing"`.
- **PREMIUM was priced against a MEDIUM that was still a day too full**, could not give up its castle tour, and
  gave up the club instead. Hence bottom up.

The one fallback, and what the trimmer cannot do: nothing could leave MEDIUM's overfull day without breaking a
tier rule, and the repaired draft, once its days were trimmed, had MEDIUM priced no higher than BASIC
(`TIER_ORDER`). Dropping activities cannot raise a price.

Tests: backend `./gradlew test` 1172, 0 failed, 6 skipped (25 new); CRA Jest 625 passed (1 new).

### Still open

- **The model does not add up durations.** 37 first drafts out of 37 had a day over its cap, by 30 to 360
  minutes: it fills every slot it is given. Java takes one or two activities back out of most MEDIUM and PREMIUM
  days, so the packages are valid and the model's own but thinner than they could be. The fix belongs in the
  planner prompt (or in giving the model the minutes left per day), not here.
- **A package title can name an activity the package does not contain** ("Karting · …" with no karting in the
  catalog): the copy call takes it from the brief.

## The chat promised a build it had not started (2026-09-29)

One live session in 38 could not be got out of. The entry screen had set days, group size, arrival and departure;
the organizer wrote "We like beer, karting and a big night out"; the model filed it as `notes: "likes beer,
karting"`, left `vibe` empty and answered "Building three options right now." Java looks for taste in
`categorySlugs` and `vibe`, found none, and started nothing. "Just build it" got the same sentence again.

The prompt never said what `notes` is for. Three changes, the first for the cause and two for when it happens anyway:

- **Prompt** (`chat-system.st`): what the group is into always goes into `vibe`, never into `notes`; `notes` is for
  a fact that fits no other field. "Anything", "surprise us", "just build it" is an answer too and sets `vibe` to
  "open to anything".
- **Notes read as taste** (`Brief.withNotesAsTaste`, called by `ChatTurnNode`): when taste is the only gap, there
  are notes, and the reply asks nothing, the notes become the vibe and the build starts. Not while the chat is still
  asking: "my brother's stag" is a note, and the answer about taste is on its way.
- **No reply without a move** (`MissingFieldQuestion`, the mirror image of `BuildingReply`): a reply that asks
  nothing while the brief still has a gap gets the question for that gap, EN/DE. When the model reported nothing
  missing it was announcing a build, so the question replaces the reply; when it knew of the gap, the question
  follows what it said. The taste question comes with the opening chips of the catalog.

Both Java rules log one INFO line when they act (`planner chat read the notes as taste`,
`planner chat asked nothing with the brief incomplete gap=[…] question=replaced the reply`) - field names only.

**Live:** four scripted sessions, seven turns, among them the message that had got stuck and "just build it" as the
very first message: no turn without a move, every build that was announced was running. In all four the prompt
change was enough - the taste landed in `vibe`, "just build it" became `vibe: "open to anything"` - so the two Java
rules did not have to act and are covered by unit tests only.

Tests: backend `./gradlew test` 1185, 0 failed, 6 skipped (13 new). Two existing tests had a fake reply that asked
nothing on an empty brief; they ask now.

## Not in this PR

- The package-structure suggestions from the concept doc (one skeleton per tier, variant ladders, accommodation/transfers).
  They are listed there and not applied.
- The generation-performance suggestions (P1–P6 in the concept doc). Among them P2: the job's worst case (160 s) is
  longer than the 90 s the API doc tells the frontend to poll, and this PR does not change that.
- Packages are not yet named after their hero activity (concept item 3); the copy prompt only asks for keyword titles.
- The concept doc's "earlier audit, D1/D2" is on the unmerged branch `audit/ai-package-generator-vs-competitors`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
