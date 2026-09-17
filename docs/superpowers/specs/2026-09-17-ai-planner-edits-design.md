# AI Planner — package edits without regeneration (hybrid) — Design

**Date:** 2026-09-17
**Source:** owner feedback after the planner backend landed (PR #27): "regenerating all
three packages for one change is expensive"; owner chose the hybrid with a qwen-plus
text refresh.
**Apps:** `myhive-backend` only. Extends the contract in
[`docs/api/ai-planner-api.md`](../../api/ai-planner-api.md) additively.
**Builds on:** [`2026-09-15-ai-stag-planner-design.md`](2026-09-15-ai-stag-planner-design.md)
(the graph, brief, catalog snapshot, validator, assembler, fallback, sinks).

## Problem

Today the only way to change a package is a full regeneration: ~4 000 input + ~1 700
output tokens on the planner model, 20–40 s of polling, and the new plan is built from
scratch, so "swap karting for a bar" can silently change everything else. The owner's
target flow is: chat collects the brief → three packages → the organizer says what to
add or remove → the packages update → the organizer picks one → Trip Builder → group
vote. Step three must be cheap, fast and predictable.

## Decisions

| Question | Decision | Why |
|---|---|---|
| What is an "edit" | A structural operation on a package: `ADD`, `REMOVE`, `REPLACE` of a catalog activity, optionally scoped to one package / day / slot | Mechanical operations need no model; Java already owns slots, caps, prices |
| Who understands the request | The chat model extracts edit operations into a new `edits` array of its JSON answer, naming activities by their **catalog names** (it is shown the current packages and the catalog names) | Same seam as the brief; Java resolves names against the catalog snapshot |
| Who applies it | Java (`PackageEditor`) on the stored `ComposedPlan`, re-validated and re-priced with the existing `PlanValidator`/`PlanAssembler` | Zero tokens, instant, deterministic |
| Texts after an edit | One small call to the **chat model** (`qwen3.7-plus`) rewrites only the affected texts (`why` of new items, `summary` of touched days, package `description`); failure keeps the old texts and flags it | ≈ 700 in / 300 out tokens, 2–4 s, never blocks the edit |
| Brief changes | Unchanged: days / group size / vibe / budget / edges still trigger the existing full regeneration | A semantic change needs the planner |
| Scope of an op | `packageKey` null = apply to every package where it makes sense; a named key scopes it | Owner: edits usually mean "everywhere"; per-package edits are free to support |
| Persistence | Every edit turn that applies at least one op creates a **new** `ai_generations` row (`kind = EDITED`, `parent_id` → previous, `edit_report` JSON), READY immediately | History and "back to the previous version" for free; `latestReadyGeneration` semantics unchanged |
| Tier rules after edits | Scheduling rules stay hard; `TIER_ORDER` and `TIER_NOT_DISTINCT` are **relaxed** for edited generations (`tierRulesRelaxed` in the report) | The organizer's explicit choice beats the tier heuristic |
| Limits | 20 edit turns per session (`edit_count`), not counted against the 5 generations; over the cap every op is rejected with `EDIT_LIMIT` (no HTTP error mid-chat) | Bounds the refresh-call cost |
| Delivery | Synchronous inside `POST /messages` (Java + ≤ 20 s refresh call); the response carries the new READY generation inline | No polling for a 3-second operation |
| Branch | `feat/ai-planner-edits` from `feat/ai-stag-planner`; stacked PR with base `feat/ai-stag-planner` until #27 merges, then retarget to `main` | Owner has not merged #27 yet |

## Scope

In scope (backend):

1. Edit operations extracted by the chat model, resolved and applied by Java.
2. Text refresh through the chat model, optional and non-blocking.
3. New `ai_generations` rows for edits, `edit_count` on sessions (Flyway V8).
4. Graph: one new node `applyEdits` between `chatTurn` and `awaitSelection`.
5. Contract additions (all additive): `edit` object on the turn response, `kind` /
   `parentId` / `editReport` on generations, `limits.editsLeft`.
6. Tests at every layer; one integration test for the edit turn over HTTP.

Out of scope:

- Semantic edits ("make day 2 more relaxed"): these still change the brief and
  regenerate.
- Editing texts by hand, reordering days, moving existing items between slots (a
  `REPLACE` or `REMOVE`+`ADD` pair covers the useful cases).
- Undo endpoint (the history is there; the UI can `select` an older READY row later).
- Frontend.

## Architecture

### Chat turn output (model → Java)

The chat JSON gains one array; the brief shape is unchanged:

```
"edits": [
  {"op": "ADD"|"REMOVE"|"REPLACE", "activity": "<catalog name>",
   "replacement": "<catalog name>"|null, "packageKey": "BASIC"|"MEDIUM"|"PREMIUM"|null,
   "dayNumber": int|null, "slot": "MORNING"|"AFTERNOON"|"EVENING"|"NIGHT"|null}
]
```

The chat prompt receives, once packages exist, a compact view of the current packages
(`key → day → activity names`) and the list of catalog activity names (≈ 400 tokens),
and three rules: only extract explicit add/remove/replace requests into `edits`; use
catalog names verbatim; phrase the reply as "doing it now", never as a result (Java
decides). Before any generation exists the model must not emit edits; Java drops them
anyway. An op with an unknown `op` value or a blank `activity` is dropped by the
parser; the turn itself never fails because of a malformed edit.

### `PackageEditor` (Java, no model)

Input: the current `ComposedPlan`, the brief, the catalog snapshot (already in graph
state from the last generation), the ops. Ops are applied **one at a time** in order,
each against the running result; an op that cannot be applied is rejected with a
reason code and the plan is left exactly as it was before that op.

- **Resolution** (`ActivityNameResolver`): the op's `activity` / `replacement` names
  are matched against the catalog snapshot: exact (case-insensitive, whitespace
  collapsed) first, then a unique "contains" match; no match or an ambiguous match
  rejects the op (`UNKNOWN_ACTIVITY`, `AMBIGUOUS_ACTIVITY`). For `REMOVE`/`REPLACE` the
  activity must currently be in the targeted package(s) (`NOT_IN_PACKAGE`).
- **Targets**: `packageKey` null → every package where the op makes sense (`REMOVE`
  and `REPLACE` where the activity is present, `ADD` everywhere); a per-package
  rejection does not block the other packages. The report lists the outcome per
  package.
- **`REMOVE`**: drop the item from every day of the package; a package that would end
  up with zero items rejects (`WOULD_EMPTY_PACKAGE`).
- **`ADD`**: pick the day: the given `dayNumber` if any, else the first day (in order)
  with a free allowed slot where the tier's item and minute caps still hold (30-minute
  buffer as in the validator). Pick the slot: the given `slot` if free and allowed,
  else by preference: activities whose categories contain any of `nightlife`,
  `adult`, `dining`, `food` try `EVENING, NIGHT, AFTERNOON, MORNING`, all others
  `MORNING, AFTERNOON, EVENING, NIGHT`. No fit anywhere → `NO_FREE_SLOT`. Already in
  the package → `ALREADY_IN_PACKAGE`. The new item's `why` is empty until the text
  refresh fills it.
- **`REPLACE`**: remove the old item and put the replacement into the same day and
  slot if it fits the caps; otherwise fall back to the `ADD` placement rule; if that
  fails too the op is rejected as a whole (`NO_FREE_SLOT`) and the old item stays.
  Replacement already in the package → `ALREADY_IN_PACKAGE`.
- **Validation**: after each op the affected package is re-checked with
  `PlanValidator` (scheduling codes only) and re-priced with `PlanAssembler`; an op
  that leaves any scheduling violation is rolled back and rejected with
  `WOULD_BREAK_SCHEDULE` (detail = the violation). `TIER_ORDER` and
  `TIER_NOT_DISTINCT` are ignored for edited plans; the report carries
  `tierRulesRelaxed = true` once any op was applied. Prices, `lineTotal`,
  `groupMinApplied`, totals and `activityIds` are always recomputed by the assembler,
  never edited by hand.
- **Output**: `EditOutcome(ComposedPlan plan, List<AppliedEdit> applied,
  List<RejectedEdit> rejected)` with `AppliedEdit(EditOp op, String activityName,
  Tier packageKey, int dayNumber, Slot slot)` and `RejectedEdit(EditOp op, String
  activityName, Tier packageKey, EditRejectionReason reason, String detail)`
  (`packageKey` null on a rejection that happened before targeting, e.g. resolution).

The editor converts the plan to the `PlanDraft` form (`PlanDrafts.fromComposed(plan)`
keeps ids, slots, start hints and texts), edits the draft, and runs it back through
the existing validator/assembler pipeline; `degraded` is inherited from the parent
generation.

### Text refresh (chat model)

After at least one op was applied, `applyEdits` asks the chat model once:
`LlmGateway.refreshTexts(TextRefreshRequest(locale, destinationName, packages after
the edit in compact form, added activity ids per package, touched day numbers per
package))` → `TextRefreshResult(Map<Tier, PackageTexts>, LlmUsage)` with
`PackageTexts(String description, Map<UUID, String> whyByActivityId, Map<Integer,
String> summaryByDay)`. Prompt `text-refresh-user.st` with the planner's framing;
JSON mode; temperature 0.7; `qwen3.7-plus`; the existing chat timeout. Only the
returned fields are written, each through the existing length caps and
`PlanAssembler.clean`; anything missing keeps its previous text; any exception
(transport, parse) keeps all texts and sets `textsRefreshed = false`. The refresh
never changes ids, slots or prices: the parser reads only the whitelisted fields.

### Graph

```
chatTurn → awaitGeneration  (action = GENERATE)
         | applyEdits       (action = EDIT: at least one parsed op AND a RESULT exists)
         | awaitUser        (otherwise)
applyEdits → awaitSelection
```

`chatTurn` writes `EDITS` (JSON of the parsed ops) and `ACTION = EDIT` only when the
state holds a `RESULT` and the brief did not change; when the brief changed the
existing regeneration wins and the edits are dropped (a regeneration rebuilds from
the brief). Edits arriving before any generation are dropped and the assistant reply
is followed by a short template note ("Let us build the packages first").

`applyEdits` (new node, Java): checks the edit cap, runs `PackageEditor`, runs the
text refresh when something was applied, then calls
`GenerationEditSink.edited(UUID parentGenerationId, ComposedPlan plan, EditReport
report, LlmUsage usage)` which persists the new row and returns its id. The node
writes `RESULT`, `GENERATION_ID = <new id>`, `EDIT_REPORT`, clears `EDITS`, sets
`ACTION = NONE`, and appends a second ASSISTANT message from EN/DE templates when any
op was rejected ("I could not add X: no free slot"). If nothing was applied no row is
created, `GENERATION_ID` is unchanged, and only `EDIT_REPORT` is written. The node
parks the thread at `awaitSelection`, so `select`/chat/regenerate keep working exactly
as today.

A full regeneration after edits starts from the brief, so edits are lost by design;
the edited rows remain in the history.

### Persistence (Flyway V8)

```
ALTER TABLE ai_generations ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'GENERATED';
ALTER TABLE ai_generations ADD COLUMN parent_id UUID NULL
    REFERENCES ai_generations (id) ON DELETE SET NULL;
ALTER TABLE ai_generations ADD COLUMN edit_report TEXT NULL;
ALTER TABLE ai_sessions ADD COLUMN edit_count INTEGER NOT NULL DEFAULT 0;
```

`AiGenerationKind { GENERATED, EDITED }` (entity field defaults to `GENERATED`). An
edited row copies `brief_snapshot` and `degraded` from the parent; the parent's
`selected_package_key` is **not** copied (a fresh pick is required). `model`, tokens
and latency come from the refresh call (null when skipped). `edited(...)` runs in its
own transaction like `ready(...)` and touches only the new row; `edit_count` is
incremented by `AiSessionService` in its single post-turn session save (a counter
written by the sink would be overwritten by that save). Cleanup deletes edited rows with the session (`deleteBySessionId`
already covers them; the self-FK is `ON DELETE SET NULL` so deletion order does not
matter). Hibernate dev/test schemas get the same columns from the entity mapping.

### Contract additions (`docs/api/ai-planner-api.md`, additive)

- `POST /ai/sessions/{token}/messages` response gains `edit` (null unless the turn
  carried edits): `{ generationId, applied: [{op, activity, packageKey, dayNumber,
  slot}], rejected: [{op, activity, packageKey, reason, detail}], tierRulesRelaxed,
  textsRefreshed }`; when a row was created, `generation` is the new READY generation
  (full `GenerationDTO`, no polling).
- `GenerationDTO` gains `kind` (`GENERATED` | `EDITED`), `parentId` (nullable) and
  `editReport` (the object above, EDITED rows only).
- `SessionState.limits` gains `editsLeft`.
- Rejection reasons: `UNKNOWN_ACTIVITY`, `AMBIGUOUS_ACTIVITY`, `NOT_IN_PACKAGE`,
  `ALREADY_IN_PACKAGE`, `WOULD_EMPTY_PACKAGE`, `NO_FREE_SLOT`, `WOULD_BREAK_SCHEDULE`,
  `NO_PACKAGES_YET`, `EDIT_LIMIT`, `INTERNAL`.

### Limits, cost, logging

- `MAX_EDITS_PER_SESSION = 20` (`AiSessionService`), counted per edit turn that
  created a row; over the cap every op is rejected with `EDIT_LIMIT` and no refresh
  call is made.
- Cost per edit turn: the chat turn (as today, plus about 400 input tokens for the
  package view and catalog names) and one refresh call (about 700 in / 300 out on the
  chat model). No planner-model call.
- Logs: one INFO line per edit turn with token, counts of applied/rejected and
  whether texts were refreshed; never activity names or message bodies at INFO.

### Error handling

An exception inside `PackageEditor` (a bug) is caught by the node, logged at ERROR
with the session token and exception class, and turned into a report with every op
rejected (`INTERNAL`); the chat turn still answers 200 and the previous generation
stays intact. The refresh call is best-effort as described. `POST /messages` error
codes are unchanged.

### Testing

- Unit: `ActivityNameResolver` (exact, contains, ambiguous, unknown); `PackageEditor`
  (each op × each rejection reason, placement order, category-aware slot order, caps
  with buffer, all-packages expansion with partial rejection, re-pricing incl. group
  minimum, tier rules relaxed); `PlanDrafts.fromComposed` round trip; parser for
  `edits` (valid, unknown op, blank activity → op dropped, turn not failed) and for
  the refresh result (partial, malformed → exception mapped to "keep texts"); prompt
  rendering (package view + catalog names present only when packages exist).
- Graph (`PlannerGraphTest`, fake gateway): edit turn → `applyEdits` → new generation
  id → parks at `awaitSelection`; edits before any generation are dropped with the
  note; brief change + edits → regeneration wins; refresh failure keeps texts and
  flags; rejected-only turn creates no row; subsequent select/regenerate/chat
  unaffected.
- Service/controller: `edited` sink persists the row and increments `edit_count`;
  `EDIT_LIMIT`; integration test over HTTP: generate → message "swap A for B" → 200
  with `edit.applied`, `generation.kind = EDITED`, packages reflect the swap, texts
  refreshed by the fake; `GET /sessions/{token}` shows the edited row as
  `latestReadyGeneration`.
- Live smoke extension (key-gated): one real refresh call parses.

### Rollout

Ships behind the existing `AI_ENABLED` switch; V8 applies on deploy. No new env
vars. Stacked PR on #27.

### Risks

| Risk | Mitigation |
|---|---|
| Model names an activity loosely ("the karting one") | resolver's contains-match + `AMBIGUOUS_ACTIVITY` rejection with a note; the chat prompt shows exact names |
| Refresh model rewrites more than asked | parser reads only the whitelisted fields; ids/slots/prices are never read from it |
| Edits make BASIC pricier than PREMIUM | allowed by design (`tierRulesRelaxed`), visible in the report |
| Long edit chains bloat `ai_generations` | 20-edit cap; rows are small; cleanup removes them with the session |
