# AI Planner Package Edits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the organizer change the generated packages from the chat ("swap karting for the beer bike", "drop the strip club") without a planner-model regeneration: the chat model extracts edit operations, Java applies them deterministically, the chat model refreshes only the affected texts, and the result is a new READY generation returned inline.

**Architecture:** The chat JSON gains an `edits` array. `ChatTurnNode` routes to a new Java node `applyEdits` when edits arrive and packages exist (a changed brief still wins and regenerates). `applyEdits` runs `PackageEditor` (resolve names against the catalog snapshot, place/remove/replace items, re-validate scheduling, re-price via `PlanAssembler`), then one best-effort `LlmGateway.refreshTexts` call, then persists a new `ai_generations` row (`kind = EDITED`, `parent_id`) through a sink and parks at `awaitSelection`. The service returns the edit report and the new generation in the same `POST /messages` response.

**Tech Stack:** Spring Boot 4.0 / Java 25 / Gradle, Spring AI 2.0.1 (Qwen via DashScope), langgraph4j 1.8.13, Flyway, JUnit 5 + AssertJ + Mockito, Testcontainers (docker-tagged).

**Spec:** `docs/superpowers/specs/2026-09-17-ai-planner-edits-design.md` (builds on `docs/superpowers/specs/2026-09-15-ai-stag-planner-design.md`). Frontend contract: `docs/api/ai-planner-api.md`.

## Global Constraints

- Branch `feat/ai-planner-edits` (stacked on `feat/ai-stag-planner`). Backend only.
- Java style per `CLAUDE.md`: no wildcard imports, `@Override` always, braces always, one variable per declaration, constants `UPPER_SNAKE_CASE`, K&R braces, never ignore a caught exception without a comment, DRY.
- Test style (binding): values that appear in both arrange and assert are introduced once as `expected`-prefixed locals.
- Every task ships unit tests; `./gradlew test` (from `myhive-backend/`) green before each commit. Repo files are CRLF.
- Prod schema is Flyway-owned (`ddl-auto=validate`): new columns only via `V8__ai_planner_edits.sql`.
- Additive contract only: no existing JSON field is renamed or removed; existing constructors keep working (add overloads instead of changing call sites where a record gains a component).
- Edit ops: `ADD`, `REMOVE`, `REPLACE`. Rejection reasons exactly: `UNKNOWN_ACTIVITY`, `AMBIGUOUS_ACTIVITY`, `NOT_IN_PACKAGE`, `ALREADY_IN_PACKAGE`, `WOULD_EMPTY_PACKAGE`, `NO_FREE_SLOT`, `WOULD_BREAK_SCHEDULE`, `NO_PACKAGES_YET`, `EDIT_LIMIT`, `INTERNAL`.
- `MAX_EDITS_PER_SESSION = 20` (counted per edit turn that created a row). Edits never count against the 5 generations.
- Scheduling rules stay hard for edited plans; `TIER_ORDER` and `TIER_NOT_DISTINCT` are ignored for edited plans.
- Category-aware slot order for `ADD` without a slot: categories containing any of `nightlife`, `adult`, `dining`, `food` → `EVENING, NIGHT, AFTERNOON, MORNING`; otherwise `MORNING, AFTERNOON, EVENING, NIGHT`.
- Text refresh: chat model (`AiProperties.getChatModel()`, `qwen3.7-plus`), JSON mode, thinking off, temperature 0.7, `AiProperties.getChatTimeout()`; best-effort, never blocks the edit; only whitelisted fields (`description`, `why` by activity id, `summary` by day) are read; every written text passes `PlanAssembler.clean` and the existing length caps.
- Never log activity names, prompts or message bodies at INFO.
- Commit messages end with `Co-Authored-By:` + the attribution line your environment prescribes.

## File Map

| Path (under `myhive-backend/src/main/java/com/myhive/backend/`) | Responsibility |
|---|---|
| `ai/edit/EditOp.java`, `EditRequest.java` | What the chat model asked for |
| `ai/edit/EditRejectionReason.java`, `AppliedEdit.java`, `RejectedEdit.java`, `EditReport.java`, `EditOutcome.java` | What happened |
| `ai/edit/ActivityNameResolver.java` | Catalog name → activity |
| `ai/edit/PackageEditor.java` | Apply ops to a `ComposedPlan` (Java only) |
| `ai/plan/PlanDrafts.java` | `ComposedPlan` → `PlanDraft` |
| `ai/plan/PlanValidator.java` (modify) | public `validatePackage` (scheduling-only) |
| `ai/llm/ChatTurnResult.java`, `ChatTurnRequest.java` (modify) | `edits`; packages view + catalog names |
| `ai/llm/TextRefreshRequest.java`, `TextRefreshResult.java`, `PackageTexts.java` | Text refresh contract |
| `ai/llm/LlmGateway.java`, `SpringAiLlmGateway.java`, `LlmOutputParser.java`, `PromptRenderer.java` (modify) | Parse `edits`, render/parse refresh |
| `ai/edit/TextRefresher.java` | Apply refreshed texts to a plan |
| `resources/prompts/ai/chat-system.st` (modify), `text-refresh-user.st` (new) | Prompts |
| `ai/graph/PlannerState.java`, `PlannerGraph.java`, `PlannerGraphConfig.java`, `nodes/ChatTurnNode.java` (modify) | State keys, routing |
| `ai/graph/nodes/ApplyEditsNode.java` | The new node + `GenerationEditSink` |
| `entity/AiGeneration.java`, `entity/AiGenerationKind.java`, `entity/AiSession.java` (modify) | Persistence |
| `resources/db/migration/V8__ai_planner_edits.sql` | Schema |
| `ai/service/PlanGenerationService.java`, `AiSessionService.java` (modify) | Sink, limits, turn outcome |
| `ai/dto/*` (modify: `TurnResponseDTO`, `GenerationDTO`, `SessionStateDTO`, `AiDtoMapper`; new `EditDTO`) | HTTP |
| `docs/api/ai-planner-api.md`, `README.md`, `CLAUDE.md` (on disk only, gitignored) | Docs |

Tests mirror the packages under `src/test/java/com/myhive/backend/`. Test doubles: `ai/llm/FakeLlmGateway` (extend), `ai/AiTestConfig`, `ai/graph/TestPlannerGraphs` (extend).

---

### Task 1: Edit request model and parsing from the chat turn

**Files:**
- Create: `ai/edit/EditOp.java`, `ai/edit/EditRequest.java`
- Modify: `ai/llm/ChatTurnResult.java`, `ai/llm/LlmOutputParser.java`
- Test: `src/test/java/com/myhive/backend/ai/llm/LlmOutputParserTest.java` (extend), fixture `src/test/resources/ai/fixtures/chat-turn-with-edits.json`

**Interfaces:**
- Produces: `enum EditOp { ADD, REMOVE, REPLACE }`; `record EditRequest(EditOp op, String activity, String replacement, Tier packageKey, Integer dayNumber, Slot slot)`; `ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits, LlmUsage usage)` with the compact constructor normalising `edits` null → `List.of()` and **a 4-argument overload** `ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, LlmUsage usage)` delegating with `List.of()` so every existing call site (tests, `FakeLlmGateway`, gateway) compiles unchanged; `withUsage` keeps the edits.

- [ ] **Step 1: Write the failing parser tests**

Fixture `chat-turn-with-edits.json`:

```json
{"reply": "Swapping it now.",
 "brief": {"days": null, "groupSize": null, "categorySlugs": [], "vibe": null, "dislikes": null, "budget": null,
           "arrival": null, "departure": null, "notes": null},
 "missingFields": [],
 "edits": [
   {"op": "REPLACE", "activity": "Karting", "replacement": "Beer Bike", "packageKey": null, "dayNumber": null, "slot": null},
   {"op": "ADD", "activity": "Shooting Range", "replacement": null, "packageKey": "PREMIUM", "dayNumber": 2, "slot": "AFTERNOON"},
   {"op": "TELEPORT", "activity": "Karting"},
   {"op": "REMOVE", "activity": "   "}
 ]}
```

Tests in `LlmOutputParserTest`:
- `parseChatTurn_readsEditOperations_andDropsInvalidOnes`: the fixture yields exactly two edits; the first is `REPLACE Karting → Beer Bike` with null scope; the second is `ADD Shooting Range` scoped to `PREMIUM`, day 2, `AFTERNOON`. The unknown op and the blank activity are dropped; the turn parses (`reply` is `"Swapping it now."`). Use `expected`-prefixed locals for the names and scope values.
- `parseChatTurn_withoutEditsField_hasEmptyEdits`: the existing `chat-turn-valid.json` fixture → `edits()` is empty.
- `parseChatTurn_editsNotAnArray_isTreatedAsEmpty`: `"edits": "karting"` → empty, reply still parsed.
- `parseChatTurn_replaceWithoutReplacement_isDropped`: a `REPLACE` with null/blank `replacement` is dropped.

- [ ] **Step 2: Run to verify failure** — `./gradlew test --tests '*LlmOutputParserTest'` → compilation failure.

- [ ] **Step 3: Implement**

`EditRequest`: record as above; `activity`/`replacement` stripped in the compact constructor (null stays null).

`ChatTurnResult`:

```java
public record ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, List<EditRequest> edits,
                             LlmUsage usage) {

    public ChatTurnResult {
        edits = edits == null ? List.of() : List.copyOf(edits);
    }

    /** The shape every caller used before edits existed; a turn without edits. */
    public ChatTurnResult(String reply, Brief briefUpdate, List<String> missingFields, LlmUsage usage) {
        this(reply, briefUpdate, missingFields, List.of(), usage);
    }

    public ChatTurnResult withUsage(LlmUsage newUsage) {
        return new ChatTurnResult(reply, briefUpdate, missingFields, edits, newUsage);
    }
}
```

`LlmOutputParser.parseChatTurn`: after `missing`, read `root.path("edits")`; when it is an array, convert each element on its own with a try/catch around `mapper.treeToValue(node, EditRequest.class)` (the mapper already reads unknown enum values as null) and keep it only when `op != null`, `activity` non-blank, and for `REPLACE` a non-blank `replacement`. A malformed element is skipped with a comment explaining why (the brief and the reply are still useful). Extract the element filter into a private method `editOrNull(JsonNode)`.

- [ ] **Step 4: Run the tests** — `./gradlew test --tests '*LlmOutputParserTest'` → PASS; then the full suite (all existing `new ChatTurnResult(reply, brief, missing, usage)` call sites must still compile).

- [ ] **Step 5: Commit** — `feat(ai): parse edit operations from the chat turn`

---

### Task 2: Scheduling-only package validation and ComposedPlan → PlanDraft

**Files:**
- Create: `ai/plan/PlanDrafts.java`
- Modify: `ai/plan/PlanValidator.java`
- Test: `src/test/java/com/myhive/backend/ai/plan/PlanDraftsTest.java`, `PlanValidatorTest.java` (extend)

**Interfaces:**
- Produces: `PlanDrafts.fromComposed(ComposedPlan plan)` → `PlanDraft` (package order, titles, taglines, descriptions, day titles/summaries, item slot/startHint/activityId/why preserved exactly); `PlanDrafts.packageOf(PlanDraft draft, Tier key)` → `Optional<PlanDraft.PackageDraft>`; `PlanDrafts.replacePackage(PlanDraft draft, PlanDraft.PackageDraft replacement)` → new `PlanDraft` with the package of the same key swapped.
- Produces: `public List<Violation> PlanValidator.validatePackage(PlanDraft.PackageDraft pkg, Brief brief, Map<UUID, CatalogActivity> catalog)` — the existing per-package checks (texts, day count, days, slots, caps, duplicates, empty day, empty package) **without** `MISSING_TIER` and `checkDistinct`. `validate(...)` is refactored to call it for each package (behaviour unchanged; existing tests must stay green). `allowedSlots` stays package-visible and is also used by the editor (same package? no — the editor lives in `ai.edit`): make `allowedSlots` **public static** and document that the editor relies on it.

- [ ] **Step 1: Failing tests**
  - `PlanDraftsTest.fromComposed_roundTripsThroughTheAssembler`: build a draft with three 2-day packages (use a small fixture catalog as in `PlanAssemblerTest`), assemble it, convert back with `fromComposed`, assemble again → the two `ComposedPlan`s are equal (records compare by value).
  - `PlanDraftsTest.replacePackage_swapsOnlyThatTier`.
  - `PlanValidatorTest.validatePackage_ignoresTierDistinctness`: three identical packages are `TIER_NOT_DISTINCT` under `validate` but `validatePackage` returns no violations for any of them.
  - `PlanValidatorTest.validatePackage_reportsSchedulingViolations`: a day over the minute cap → `DAY_OVER_MINUTES` from `validatePackage`.

- [ ] **Step 2: Run to verify failure.**

- [ ] **Step 3: Implement.** `fromComposed` maps `PackageResult → PackageDraft(key, title, tagline, description, days)`, `DayResult → DayDraft(dayNumber, title, summary, items)`, `ItemResult → ItemDraft(slot, startHint, activityId, why)`. In `PlanValidator`, extract the body of the existing `validatePackage(p, brief, catalog, out)` into the new public overload returning a list; keep one private implementation (DRY).

- [ ] **Step 4: Run** `./gradlew test --tests '*PlanDraftsTest' --tests '*PlanValidatorTest' --tests '*PlanAssemblerTest' --tests '*FallbackPlanComposerTest'` → PASS.

- [ ] **Step 5: Commit** — `refactor(ai): scheduling-only package validation and ComposedPlan to draft conversion`

---

### Task 3: Activity name resolver and the package editor

**Files:**
- Create: `ai/edit/ActivityNameResolver.java`, `ai/edit/EditRejectionReason.java`, `ai/edit/AppliedEdit.java`, `ai/edit/RejectedEdit.java`, `ai/edit/EditOutcome.java`, `ai/edit/PackageEditor.java`
- Test: `src/test/java/com/myhive/backend/ai/edit/ActivityNameResolverTest.java`, `PackageEditorTest.java`

**Interfaces:**
- Consumes: `EditRequest`, `EditOp` (Task 1); `PlanDrafts`, `PlanValidator.validatePackage`, `PlanValidator.allowedSlots`, `PlanValidator.BUFFER_MINUTES` (Task 2); `PlanAssembler.assemble`; `ComposedPlan`; `CatalogActivity`; `Brief`; `Tier` (`maxItemsPerDay()`, `maxMinutesPerDay()`); `Slot`.
- Produces:
  - `sealed interface ActivityNameResolver.Resolution permits Found, NotFound, Ambiguous` with `record Found(CatalogActivity activity)`, `record NotFound()`, `record Ambiguous(List<String> candidates)`; `ActivityNameResolver.resolve(String name, List<CatalogActivity> catalog)`.
  - `enum EditRejectionReason { UNKNOWN_ACTIVITY, AMBIGUOUS_ACTIVITY, NOT_IN_PACKAGE, ALREADY_IN_PACKAGE, WOULD_EMPTY_PACKAGE, NO_FREE_SLOT, WOULD_BREAK_SCHEDULE, NO_PACKAGES_YET, EDIT_LIMIT, INTERNAL }`
  - `record AppliedEdit(EditOp op, String activityName, String replacementName, Tier packageKey, int dayNumber, Slot slot, UUID placedActivityId)` — `activityName`/`replacementName` are the **catalog** names after resolution (not the model's spelling); `replacementName` null except for `REPLACE`; `placedActivityId` is the id of the item that was added (ADD: the activity, REPLACE: the replacement, REMOVE: null); for `REMOVE`, `dayNumber`/`slot` are those of the removed item's first occurrence, otherwise the cell where the new item landed.
  - `record RejectedEdit(EditOp op, String activityName, Tier packageKey, EditRejectionReason reason, String detail)` (`packageKey` null when the rejection happened before targeting).
  - `record EditOutcome(ComposedPlan plan, List<AppliedEdit> applied, List<RejectedEdit> rejected)` with `boolean anyApplied()`.
  - `@Component PackageEditor` with `EditOutcome apply(ComposedPlan plan, Brief brief, List<CatalogActivity> catalog, List<EditRequest> edits)`; the constructor takes `PlanValidator` and `PlanAssembler`.

**Resolver rules:** normalise both sides (lower-case with `Locale.ROOT`, collapse whitespace, strip). Exact equal names → `Found` (if two catalog rows share the same normalised name, `Ambiguous`). Else collect catalog rows whose normalised name contains the normalised query **or** whose name is contained in the query (min query length 3); exactly one → `Found`, more → `Ambiguous` (candidate names, sorted), none → `NotFound`.

**Editor algorithm (binding):**

```
working = PlanDrafts.fromComposed(plan)
for each edit in order:
    resolve activity (and replacement for REPLACE)
        NotFound  -> reject(null package, UNKNOWN_ACTIVITY, detail = name)
        Ambiguous -> reject(null package, AMBIGUOUS_ACTIVITY, detail = candidates joined with ", ")
    targets = edit.packageKey != null ? [that package] : all packages
    for REMOVE / REPLACE with packageKey == null: targets = packages containing the activity;
        none contain it -> reject(null, NOT_IN_PACKAGE)
    for each target package (independently; each success replaces the package in `working`):
        candidate = applyOne(op, package)            // returns PackageDraft or a rejection
        violations = validator.validatePackage(candidate, brief, catalogById)
        if violations non-empty -> reject(target, WOULD_BREAK_SCHEDULE, detail = first violation's code + detail)
        else working = PlanDrafts.replacePackage(working, candidate); record AppliedEdit
assembled = assembler.assemble(working, brief, catalogById, plan.degraded())   // TIER_ORDER violations ignored
return EditOutcome(assembled.plan(), applied, rejected)
```

`applyOne`:
- **REMOVE**: activity absent → `NOT_IN_PACKAGE`; removing every occurrence leaves zero items in the package → `WOULD_EMPTY_PACKAGE`; else drop all occurrences.
- **ADD**: already present → `ALREADY_IN_PACKAGE`; candidate cells = `dayNumber` given ? [that day] : all days in order; slots = `slot` given ? [slot] : preference order (see Global Constraints) intersected with `PlanValidator.allowedSlots(day, brief)`, keeping the preference order; the first cell where the slot is free, `items + 1 <= tier.maxItemsPerDay()` and `minutes + duration + (items.isEmpty() ? 0 : BUFFER_MINUTES) <= tier.maxMinutesPerDay()` wins; the item is inserted keeping the day's items ordered by `Slot` ordinal, with `startHint = null`, `why = ""`; nothing fits → `NO_FREE_SLOT`.
- **REPLACE**: old absent → `NOT_IN_PACKAGE`; replacement present → `ALREADY_IN_PACKAGE`; remove the old item's first occurrence (day `d`, slot `s`), then try `ADD replacement` at exactly `(d, s)`; if that does not fit, try the unscoped `ADD` placement; if nothing fits → `NO_FREE_SLOT` and the package is unchanged. `REPLACE` removes only the first occurrence (a package never holds duplicates after validation).

Any `RuntimeException` inside one edit is caught, the edit is rejected with `INTERNAL` (detail = exception class simple name), `working` is left as before that edit, and the loop continues — the comment must say why (an editor bug must not lose the organizer's other edits).

- [ ] **Step 1: Failing tests** — `ActivityNameResolverTest`: exact (case/whitespace-insensitive), unique contains, reverse contains ("the karting track please" → "Karting"), ambiguous (two "Beer …" rows) lists both sorted, not found, query under 3 chars only matches exactly.

`PackageEditorTest` (fixture: 6 activities with distinct prices and durations, categories incl. one `nightlife`; a 2-day brief MORNING→EVENING for 4 people; three packages assembled via `PlanAssembler`; use `expected`-prefixed locals):
1. `remove_dropsTheActivityFromEveryPackageThatHasIt_andRepricesThem` — totals recomputed (assert `totalPrice` equals the sum of the remaining `lineTotal`s).
2. `remove_lastItemOfAPackage_isWouldEmptyPackage` (package-scoped).
3. `remove_absentActivity_isNotInPackage`.
4. `add_withoutScope_goesIntoEveryPackageAtTheFirstFittingCell_nightlifePrefersEvening`.
5. `add_daytimeActivity_prefersMorning`.
6. `add_respectsTierItemAndMinuteCapsIncludingBuffer` — BASIC day already at 2 items → next day; minutes at 330 with a 60-min activity → `NO_FREE_SLOT` when no other day fits.
7. `add_withExplicitDayAndSlot_usesExactlyThatCell_orRejectsNoFreeSlot`.
8. `add_alreadyPresent_isAlreadyInPackage`.
9. `replace_putsTheReplacementIntoTheFreedCell`.
10. `replace_whenTheFreedCellCannotHoldIt_fallsBackToPlacement`.
11. `replace_whenNothingFits_leavesThePackageUnchanged`.
12. `unknownAndAmbiguousNames_areRejectedBeforeTargeting` (`packageKey` null on the rejection).
13. `partialRejection_oneScopedPackageFails_othersStillApplied`.
14. `editsApplyInOrder_againstTheRunningResult` (ADD X then REMOVE X → X absent, both applied).
15. `groupMinimumFloor_isAppliedToAnAddedItem` (an activity with `minPrice` large enough → `groupMinApplied` true on the new item).
16. `tierOrderAndDistinctness_areNotEnforced` (edits that make BASIC pricier than PREMIUM are still applied).
17. `runtimeFailureInOneEdit_isInternal_andLaterEditsStillApply` (use a catalog row whose `durationMinutes` makes arithmetic throw? simpler: a spy `PlanValidator` that throws once).

- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement** — keep `PackageEditor` focused: private methods `applyRemove`, `applyAdd`, `applyReplace`, `placement(...)`, `slotPreference(CatalogActivity)`, `minutesOf(day, catalogById)`; no method over ~40 lines.
- [ ] **Step 4: Run** `./gradlew test --tests 'com.myhive.backend.ai.edit.*'` then the full suite.
- [ ] **Step 5: Commit** — `feat(ai): deterministic package editor for add, remove and replace`

---

### Task 4: Text refresh (gateway method, prompt, parser, applier)

**Files:**
- Create: `ai/llm/TextRefreshRequest.java`, `ai/llm/TextRefreshResult.java`, `ai/llm/PackageTexts.java`, `ai/edit/TextRefresher.java`, `resources/prompts/ai/text-refresh-system.st`, `resources/prompts/ai/text-refresh-user.st`
- Modify: `ai/llm/LlmGateway.java`, `ai/llm/SpringAiLlmGateway.java`, `ai/llm/LlmOutputParser.java`, `ai/llm/PromptRenderer.java`
- Test: `SpringAiLlmGatewayTest` (extend), `LlmOutputParserTest` (extend), `PromptRendererTest` (extend), new `ai/edit/TextRefresherTest.java`; extend `src/test/java/com/myhive/backend/ai/llm/FakeLlmGateway.java`

**Interfaces:**
- Consumes: `ComposedPlan`, `EditOutcome`/`AppliedEdit` (Task 3), `PlanAssembler.clean`, `PlanValidator` text caps.
- Produces:
  - `record TextRefreshRequest(String locale, String destinationName, List<ComposedPlan.PackageResult> packages, Map<Tier, Set<UUID>> newActivityIds, Map<Tier, Set<Integer>> touchedDays)` — `packages` holds only the packages with at least one applied edit.
  - `record PackageTexts(String description, Map<UUID, String> whyByActivityId, Map<Integer, String> summaryByDay)` (null maps normalised to empty).
  - `record TextRefreshResult(Map<Tier, PackageTexts> texts, LlmUsage usage)`.
  - `TextRefreshResult LlmGateway.refreshTexts(TextRefreshRequest request)`.
  - `Map<Tier, PackageTexts> LlmOutputParser.parseTextRefresh(String raw)`; `PromptRenderer.textRefreshSystem(TextRefreshRequest)` and `textRefreshUser(TextRefreshRequest)`.
  - `@Component TextRefresher` with `record Refreshed(ComposedPlan plan, boolean refreshed, LlmUsage usage)` and `Refreshed refresh(ComposedPlan plan, EditOutcome outcome, String locale, String destinationName)`.
  - `FakeLlmGateway`: `queueRefresh(TextRefreshResult...)`, `failNextRefresh(RuntimeException)`, public `refreshRequests` list; with nothing queued `refreshTexts` throws `IllegalStateException("FakeLlmGateway: no refresh answer queued")`; `reset()` clears the new state.

**Prompt (`text-refresh-system.st`):**

```
You write short, upbeat stag-party copy for {destinationName} in language "{locale}" (de = informal "du").
You are given packages that the organizer just edited. Rewrite ONLY the requested texts so they match the
activities that are now in each package. Never mention an activity that is not listed. No markdown.
Limits: description max 600 chars, why max 160 chars, day summary max 300 chars.
Answer ONLY with JSON: \{"packages": [\{"key": "BASIC"|"MEDIUM"|"PREMIUM", "description": string,
 "why": [\{"activityId": string, "text": string\}], "summaries": [\{"dayNumber": int, "text": string\}]\}]\}
```

**Prompt (`text-refresh-user.st`):** `{packages}` rendered by `PromptRenderer` as, per package: `PACKAGE <key> "<title>"`, the current description, then per day `DAY <n> (<summary>)` and per item `- <slot> <activityId> <name>`; followed by `REWRITE: description; why for <ids>; summary for days <numbers>`. Use the same template engine the existing prompts use (if `PromptTemplate` rejects the JSON braces, escape them as the existing `.st` files do).

**Gateway:** `SpringAiLlmGateway.refreshTexts` sends `[SystemMessage(textRefreshSystem), UserMessage(textRefreshUser)]` through the existing `call(...)` with `props.getChatModel()`, temperature `0.7`, `props.getChatTimeout()`; parses with `parseTextRefresh`; returns usage. Failures surface as the existing `LlmUnavailableException` / `LlmOutputException`.

**Parser:** tolerant per entry — unknown `key` or non-UUID `activityId` or non-int `dayNumber` entries are skipped; a missing `packages` array → `LlmOutputException`.

**TextRefresher.refresh:** when `!outcome.anyApplied()` → `Refreshed(plan, false, LlmUsage.none())` without calling the model. Otherwise build the request from `outcome.applied()` (group `placedActivityId` and `dayNumber` by `packageKey`), call the gateway inside a try/catch for `RuntimeException` (comment: the edit already succeeded; texts are cosmetic) → on failure `Refreshed(plan, false, LlmUsage.none())` + WARN with the exception class. On success rebuild only the touched packages: `description` when non-blank; `why` only for items whose activity id is in the package **and** in `newActivityIds`; `summary` only for days in `touchedDays`; every text passes `PlanAssembler.clean` and is truncated to the `PlanValidator` cap (`DESCRIPTION_MAX`, `WHY_MAX`, `DAY_SUMMARY_MAX`). Prices, ids, slots are copied from the input plan untouched.

- [ ] **Step 1: Failing tests**
  - `SpringAiLlmGatewayTest.refreshTexts_usesTheChatModelInJsonModeWithThinkingOff` (captured `Prompt` options: chat model id, temperature 0.7, JSON_OBJECT, `enable_thinking=false`).
  - `LlmOutputParserTest.parseTextRefresh_readsTextsAndSkipsInvalidEntries`; `parseTextRefresh_withoutPackages_isLlmOutputException`.
  - `PromptRendererTest.textRefreshUser_listsItemsAndWhatToRewrite`.
  - `TextRefresherTest`: `noAppliedEdits_doesNotCallTheModel`; `appliesOnlyRequestedFields_andCleansAndCaps` (HTML stripped, 200-char why truncated to 160, an extra why for an unchanged item ignored, a summary for an untouched day ignored, prices identical); `modelFailure_keepsTextsAndReportsNotRefreshed`; `untouchedPackages_areReturnedUnchanged`.
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test --tests '*SpringAiLlmGatewayTest' --tests '*LlmOutputParserTest' --tests '*PromptRendererTest' --tests '*TextRefresherTest'`, then the full suite.
- [ ] **Step 5: Commit** — `feat(ai): refresh package texts after an edit with the chat model`

---

### Task 5: Edit report, persistence (V8) and the edit sink

**Files:**
- Create: `ai/edit/EditReport.java`, `ai/edit/GenerationEditSink.java`, `entity/AiGenerationKind.java`, `resources/db/migration/V8__ai_planner_edits.sql`
- Modify: `entity/AiGeneration.java`, `entity/AiSession.java`, `ai/service/PlanGenerationService.java`
- Test: `repository/AiRepositoriesTest.java` (extend), `ai/service/PlanGenerationServiceTest.java` (extend), `ai/AiWiringTest.java` (extend)

**Interfaces:**
- Produces:
  - `record EditReport(List<AppliedEdit> applied, List<RejectedEdit> rejected, boolean tierRulesRelaxed, boolean textsRefreshed)` with `static EditReport of(EditOutcome outcome, boolean textsRefreshed)` (`tierRulesRelaxed = outcome.anyApplied()`), `static EditReport allRejected(List<EditRequest> edits, EditRejectionReason reason)` (one `RejectedEdit` per request, `packageKey` = the request's, `detail` null), `boolean anyApplied()`. Jackson-serialisable (stored as JSON).
  - `interface GenerationEditSink { UUID edited(UUID parentGenerationId, ComposedPlan plan, EditReport report, LlmUsage usage); }`
  - `enum AiGenerationKind { GENERATED, EDITED }`; `AiGeneration` gains `kind` (`@Enumerated(STRING)`, `nullable = false`, length 16, default `GENERATED`), `parentId` (`UUID`, column `parent_id`, plain column — no JPA relation), `editReport` (`TEXT`, column `edit_report`); `AiSession` gains `editCount` (`int`, column `edit_count`, not null, default 0).
  - `PlanGenerationService implements GenerationEditSink` (in addition to the two existing sinks). `edited(...)` is `@Transactional`: re-reads the parent by id, creates a new row with `session` = parent's session, `kind = EDITED`, `parentId`, `status = READY`, `briefSnapshot` and `degraded` copied from the parent, `result = JsonCodec.write(plan)`, `editReport = JsonCodec.write(report)`, `model`/tokens/latency from `usage` (null model when `LlmUsage.none()`), `attempt = 0`, `createdAt = startedAt = finishedAt = now(UTC)`; saves and returns the id. It does **not** touch the session row (Ruling: the request thread saves the session right after the graph run and would overwrite any counter written here — the lost-update pattern fixed in the previous feature; `AiSessionService` increments `editCount` itself, Task 8).

**Migration `V8__ai_planner_edits.sql`** (header comment in the style of V5–V7):

```sql
-- AI planner edits: an edit turn stores a new READY generation linked to its parent.
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'GENERATED';
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS parent_id UUID NULL
    REFERENCES ai_generations (id) ON DELETE SET NULL;
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS edit_report TEXT NULL;
ALTER TABLE ai_sessions ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;
```

- [ ] **Step 1: Failing tests**
  - `AiRepositoriesTest.editedGeneration_isTheNewestReady_andKeepsItsParentLink` (persist a GENERATED READY row, then an EDITED READY row with `parentId`; `findFirstBySessionIdAndStatusOrderByCreatedAtDesc` returns the edited one; `kind`, `parentId`, `editReport` round-trip).
  - `PlanGenerationServiceTest.edited_storesAReadyEditedRowWithTheParentsBriefAndDegradedFlag` (mocks as in the existing tests; assert the saved row via `ArgumentCaptor`; assert `sessionRepository.save` is **never** called by `edited`).
  - `PlanGenerationServiceTest.edited_withoutModelUsage_leavesModelAndTokensNull`.
  - `AiWiringTest.exactlyOneBeanImplementsTheEditSink`.
  - A docker-tagged extension of `PostgresCheckpointPersistenceTest` is **not** needed; instead add `V8` to the migration smoke in whichever test currently applies Flyway on Testcontainers (if none applies the full migration set, skip and note it in the report).
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.** Entity ↔ SQL agreement checked column by column in the report (prod `validate`).
- [ ] **Step 4: Run** the three test classes, then the full suite (all `@SpringBootTest` contexts must start with the new sink bean).
- [ ] **Step 5: Commit** — `feat(ai): persist edited generations with a parent link (Flyway V8)`

---

### Task 6: Chat prompt shows the packages; ChatTurnNode decides EDIT

**Files:**
- Create: `ai/edit/EditMessages.java`, `ai/edit/PackagesView.java`
- Modify: `ai/llm/ChatTurnRequest.java`, `ai/llm/PromptRenderer.java`, `resources/prompts/ai/chat-system.st`, `ai/graph/PlannerState.java`, `ai/graph/nodes/ChatTurnNode.java`
- Test: `PromptRendererTest` (extend), new `ai/edit/PackagesViewTest.java`, `ai/edit/EditMessagesTest.java`, `ai/graph/nodes/ChatTurnNodeTest.java` (new, unit — build `PlannerState` from a map)

**Interfaces:**
- Consumes: `EditRequest`, `ChatTurnResult.edits()` (Task 1); `EditReport.allRejected`, `EditRejectionReason` (Tasks 3, 5); `PlannerState.result()`, `catalog()`, `lastGeneratedBrief()`, `locale()`.
- Produces:
  - `ChatTurnRequest(String locale, String destinationName, List<String> categorySlugs, Brief brief, List<ChatMessage> history, String packagesView, List<String> catalogNames)` + a **5-argument overload** (the current shape) delegating with `null` / `List.of()`.
  - `PackagesView.render(ComposedPlan plan)` → e.g. `BASIC: day 1 [EVENING Beer Bike]; day 2 [MORNING Karting]` one line per package; `PackagesView.catalogNames(List<CatalogActivity> catalog)` → sorted distinct names.
  - `EditMessages.noPackagesYet(String locale)`, `EditMessages.rejectionSummary(String locale, List<RejectedEdit> rejected)` (EN + DE informal "du"; one short sentence per distinct reason, naming the activity; unknown locale → EN).
  - `PlannerState` constants: `EDITS` (JSON string of `List<EditRequest>`), `EDIT_REPORT` (JSON string of `EditReport`, `""` = none), `EDITS_LEFT` (int), `ACTION_EDIT = "EDIT"`; accessors `List<EditRequest> edits()`, `Optional<EditReport> editReport()` (blank → empty), `int editsLeft()` (absent → `Integer.MAX_VALUE`, so graph tests without the service are unlimited).

**Prompt change (`chat-system.st`)** — append before the JSON shape, rendered only when `packagesView` is non-null (use a `{packagesBlock}` placeholder that the renderer fills with the block or an empty string):

```
Current packages (the organizer can change them):
{packagesView}
Catalog activity names (use these exact names in edits): {catalogNames}
Edit rules:
- If the organizer asks to add, remove or swap a specific activity, put it in "edits" and do not change the brief for it.
- Use exact catalog names. packageKey null means every package; set it only if the organizer names a package.
- Reply with one short sentence saying you are doing it now; never claim it is done.
- Changes of trip length, group size, vibe or budget go into the brief as before, not into edits.
```

and extend the JSON shape line with `"edits": [\{"op": "ADD"|"REMOVE"|"REPLACE", "activity": string, "replacement": string|null, "packageKey": "BASIC"|"MEDIUM"|"PREMIUM"|null, "dayNumber": int|null, "slot": "MORNING"|"AFTERNOON"|"EVENING"|"NIGHT"|null\}]` (escaped like the rest of the file). When no packages exist the block is empty and the rules still say `"edits": []`.

**ChatTurnNode decision (binding):**

```
request includes packagesView/catalogNames when state.result() is present
merged/changed/ready computed as today
if merged.isReady() && changedSinceLastGeneration -> ACTION_GENERATE   (edits ignored: regeneration rebuilds from the brief)
else if !result.edits().isEmpty():
    if state.result().isEmpty() -> ACTION_NONE, EDIT_REPORT = allRejected(edits, NO_PACKAGES_YET),
                                   and a second ASSISTANT message EditMessages.noPackagesYet(locale)
    else                        -> ACTION_EDIT, EDITS = JSON(edits)
else -> ACTION_NONE
```

The node never clears `EDIT_REPORT` itself (the service does, per turn — Task 8). Both assistant messages go into the one `MESSAGES` list in reply order.

- [ ] **Step 1: Failing tests**
  - `PackagesViewTest.render_listsEachPackageDayAndSlotInOrder`; `catalogNames_areSortedAndDistinct`.
  - `EditMessagesTest`: EN and DE for `NO_FREE_SLOT` and `UNKNOWN_ACTIVITY`, unknown locale falls back to EN.
  - `PromptRendererTest.chatSystem_withPackages_includesViewNamesAndEditRules`; `chatSystem_withoutPackages_hasNoPackagesBlock`.
  - `ChatTurnNodeTest` (fake gateway): `editsWithPackages_routeToEdit_andKeepTheBrief`; `editsWithoutPackages_areRejectedNoPackagesYet_withANote`; `briefChangeAndEdits_regenerationWins`; `noEdits_behavesAsBefore`; `requestCarriesPackagesViewOnlyWhenAResultExists`.
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the new/extended tests, `PlannerGraphTest`, then the full suite.
- [ ] **Step 5: Commit** — `feat(ai): show current packages to the chat model and route edit turns`

---

### Task 7: ApplyEditsNode and graph wiring

**Files:**
- Create: `ai/graph/nodes/ApplyEditsNode.java`
- Modify: `ai/graph/PlannerGraph.java`, `ai/graph/PlannerGraphConfig.java`, `src/test/.../ai/graph/TestPlannerGraphs.java`
- Test: `ai/graph/PlannerGraphTest.java` (extend), new `ai/graph/nodes/ApplyEditsNodeTest.java`

**Interfaces:**
- Consumes: `PackageEditor` (Task 3), `TextRefresher` (Task 4), `EditReport`, `GenerationEditSink` (Task 5), `PlannerState.edits()/editsLeft()/result()/generationId()/catalog()/brief()/locale()/destinationName()`, `EditMessages` (Task 6).
- Produces:
  - `ApplyEditsNode implements NodeAction<PlannerState>`; constructors `(PackageEditor, TextRefresher, ObjectProvider<GenerationEditSink>)` and a test overload `(PackageEditor, TextRefresher, GenerationEditSink)` (constant supplier), mirroring `PersistResultNode`.
  - `PlannerGraph.APPLY_EDITS = "applyEdits"`; `PlannerGraph.Nodes` gains `ApplyEditsNode applyEdits` (last component); routing: `afterChatTurn` returns `ROUTE_EDIT` for `ACTION_EDIT` → `APPLY_EDITS`; `addEdge(APPLY_EDITS, AWAIT_SELECTION)`; class Javadoc diagram updated.
  - `TestPlannerGraphs.inMemory(...)` / `withSaver(...)` gain overloads taking a `GenerationEditSink`; the existing signatures delegate with a recording no-op sink so existing tests compile unchanged.

**Node behaviour (binding):**

```
edits = state.edits()
if state.editsLeft() <= 0:
    report = EditReport.allRejected(edits, EDIT_LIMIT)
    return { EDIT_REPORT: json(report), EDITS: "[]", ACTION: NONE, RESUME_REASON: "",
             MESSAGES: [assistant EditMessages.rejectionSummary(locale, report.rejected())] }
plan = state.result() (present by construction; if absent -> allRejected(NO_PACKAGES_YET))
parent = state.generationId() (if absent -> allRejected(INTERNAL) + WARN)
try:
    outcome = editor.apply(plan, state.brief(), state.catalog(), edits)
catch RuntimeException e:            // comment: an editor bug must not lose the previous generation
    log.error(token? not available -> "planner edit failed", e.getClass().getName())
    outcome = EditOutcome(plan, [], allRejected(edits, INTERNAL).rejected())
refreshed = outcome.anyApplied() ? refresher.refresh(outcome.plan(), outcome, locale, destinationName)
                                 : Refreshed(plan, false, LlmUsage.none())
report = EditReport.of(outcome, refreshed.refreshed())
update = { EDIT_REPORT: json(report), EDITS: "[]", ACTION: NONE, RESUME_REASON: "" }
if outcome.anyApplied():
    newId = sink().edited(parent, refreshed.plan(), report, refreshed.usage())
    update += { RESULT: json(refreshed.plan()), GENERATION_ID: newId.toString() }
if !report.rejected().isEmpty():
    update += { MESSAGES: [assistant EditMessages.rejectionSummary(locale, report.rejected())] }
return update
```

A missing sink bean resolves to a WARN-logging sink that returns the parent id (so the graph still parks cleanly); log one INFO line: applied count, rejected count, refreshed flag — no names.

- [ ] **Step 1: Failing tests**
  - `ApplyEditsNodeTest` (plain unit, fixture plan from `PackageEditorTest`-style catalog, fake gateway, recording sink): `appliedEdit_persistsANewGeneration_andWritesResultAndId`; `refreshFailure_keepsTextsAndFlagsNotRefreshed` (fake `failNextRefresh`); `rejectedOnly_createsNoRow_andAddsARejectionMessage`; `editLimitReached_rejectsAllWithoutCallingTheModelOrSink`; `editorThrows_isInternal_previousResultKept`.
  - `PlannerGraphTest`: `editTurn_afterGeneration_appliesEdits_andParksAtAwaitSelection` (generate as the existing happy path does, then a chat turn whose fake answer carries `REPLACE`; assert the sink was called, `GENERATION_ID` changed, `next()` is `awaitSelection`, `planRequests` size unchanged); `editTurn_thenSelect_selectsFromTheEditedPlan`; `editTurn_thenBriefChange_regenerates` (planRequests grows); `editsBeforeAnyGeneration_parkAtAwaitUser_withNoPackagesYetReport`.
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.** `PlannerGraphConfig` gets `@Bean ApplyEditsNode applyEditsNode(PackageEditor, TextRefresher, ObjectProvider<GenerationEditSink>)` and passes it into `Nodes`.
- [ ] **Step 4: Run** `./gradlew test --tests '*PlannerGraphTest' --tests '*ApplyEditsNodeTest' --tests '*AiWiringTest'` then the full suite (docker-tagged `PostgresCheckpointPersistenceTest` if Docker is up — the new state keys must serialise through the Jackson saver).
- [ ] **Step 5: Commit** — `feat(ai): apply edits in the planner graph without regenerating`

---

### Task 8: Service turn outcome, limits, DTOs and HTTP

**Files:**
- Create: `ai/dto/EditDTO.java`
- Modify: `ai/service/AiSessionService.java`, `ai/dto/TurnResponseDTO.java`, `ai/dto/GenerationDTO.java`, `ai/dto/SessionStateDTO.java`, `ai/dto/AiDtoMapper.java`
- Test: `ai/service/AiSessionServiceTest.java` (extend), `ai/controller/AiPlannerControllerIntegrationTest.java` (extend)

**Interfaces:**
- Consumes: everything above; `AiGenerationRepository.findWithSessionById`.
- Produces:
  - `AiSessionService.MAX_EDITS_PER_SESSION = 20`.
  - `TurnOutcome(SessionView view, Optional<AiGeneration> startedGeneration, Optional<EditReport> editReport, Optional<AiGeneration> editedGeneration)` + the existing 2-argument overload delegating with two `Optional.empty()`.
  - `EditDTO(UUID generationId, List<AppliedEditDTO> applied, List<RejectedEditDTO> rejected, boolean tierRulesRelaxed, boolean textsRefreshed)` with `AppliedEditDTO(String op, String activity, String replacement, String packageKey, int dayNumber, String slot)` and `RejectedEditDTO(String op, String activity, String packageKey, String reason, String detail)` (nested records).
  - `TurnResponseDTO` gains a last component `EditDTO edit`; `GenerationDTO` gains `String kind, UUID parentId, EditDTO editReport` (last three components; `editReport.generationId` = the row's own id); `SessionStateDTO.LimitsDTO` gains `int editsLeft`.

**Service changes in `turn(...)` (binding):**
- The pre-resume `update` also writes `EDIT_REPORT = ""` (clears last turn's report) and `EDITS_LEFT = MAX_EDITS_PER_SESSION - session.getEditCount()`.
- After `runUntilInterrupt`: `Optional<EditReport> report = snapshot.state().editReport()`; when `report` is present and `anyApplied()`, increment `session.editCount` **before** `touch(session)` (the single session save), and load the edited row with `generationRepository.findWithSessionById(snapshot.state().generationId().orElseThrow())`.
- Build the view as today; return `TurnOutcome(view, startedGeneration, report, editedGeneration)`. A turn that edits never starts a generation (the graph parks at `awaitSelection`), so both optionals are never present together.
- `messageCount` still increments once per turn.

**Mapper:** `turn(...)` sets `generation` to the started generation if present, else the edited generation, else null; `edit` from the report (with `generationId` = edited row id or null). `generation(...)` maps `kind`, `parentId`, and `editReport` (parse the JSON only for `EDITED` rows). Limits: `editsLeft = MAX_EDITS_PER_SESSION - session.getEditCount()`.

- [ ] **Step 1: Failing tests**
  - `AiSessionServiceTest`: `editTurn_incrementsEditCountBeforeTheSessionSave_andReturnsTheEditedGeneration`; `editTurn_withNothingApplied_doesNotCountAgainstTheLimit`; `turn_stampsEditsLeftAndClearsThePreviousReport`.
  - `AiPlannerControllerIntegrationTest`: `editTurn_afterGeneration_returnsTheEditReportAndTheEditedGenerationInline` (reuse the existing generate-and-poll helper; queue a chat answer with `REPLACE` of a package activity by a catalog activity that fits; queue a refresh answer; assert `$.edit.applied[0].op == "REPLACE"`, `$.generation.kind == "EDITED"`, `$.generation.parentId` equals the first generation id, the replaced activity is absent from `$.generation.packages[*].days[*].items[*].activityId` for the targeted package and the replacement present, `$.edit.textsRefreshed == true`; then `GET /ai/sessions/{token}` → `latestReadyGeneration.id` equals the edited id and `limits.editsLeft == 19`); `editTurn_beforeAnyGeneration_returnsNoPackagesYet` (`$.edit.rejected[0].reason == "NO_PACKAGES_YET"`, `$.generation` absent); `selectOnTheEditedGeneration_returnsTheEditedTripItems`.
- [ ] **Step 2: Run to verify failure.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the two classes, then the full suite.
- [ ] **Step 5: Commit** — `feat(ai): return edit reports and edited generations from the chat turn`

---

### Task 9: Contract, docs, live smoke and PR

**Files:**
- Modify: `docs/api/ai-planner-api.md`, `README.md`, `docs/superpowers/specs/2026-09-15-ai-stag-planner-design.md` (one cross-link line), `CLAUDE.md` (on disk only — gitignored), `myhive-backend/src/test/java/com/myhive/backend/ai/llm/QwenLiveSmokeTest.java`
- Memory (outside the repo): `C:\Users\dijtb\.claude\projects\C--Users-dijtb-IdeaProjects-myhive-travel-app\memory\project_ai_stag_planner.md` + its `MEMORY.md` index line

- [ ] **Step 1: Contract** — bump to v1.2 with a "Changes since v1.1" list: `edit` on the turn response (full shape + reason list + meaning of `tierRulesRelaxed` / `textsRefreshed`), `generation` returned inline on edit turns (no polling), `kind`/`parentId`/`editReport` on generations, `limits.editsLeft`, and a Flow paragraph: "after packages exist, ask for concrete changes in chat ('swap X for Y', 'drop Z'); the response carries the updated packages immediately; a change of days, group size, vibe or budget still regenerates". Every field name and reason string must match the DTOs.
- [ ] **Step 2: README** — one paragraph under the AI planner section (edits, cost, 20-edit cap); **CLAUDE.md** — extend the AI planner bullet with one sentence (edits path: `ApplyEditsNode` → `PackageEditor` → `TextRefresher` → `GenerationEditSink`, V8).
- [ ] **Step 3: Live smoke** — add a key-gated test calling `refreshTexts` on a tiny two-item package and asserting it parses (no content assertions).
- [ ] **Step 4: Memory** — add an "Edits (2026-09-17)" paragraph to `project_ai_stag_planner.md` (hybrid design, why, V8, stacked PR) and update the index line.
- [ ] **Step 5:** `graphify update .` from the repo root; `./gradlew test` from `myhive-backend/`.
- [ ] **Step 6: Commit and PR** — commit `docs(ai): document package edits (contract v1.2)`; `git push -u origin feat/ai-planner-edits`; `gh pr create --base feat/ai-stag-planner --title "feat(ai): edit planner packages from the chat without regenerating" --body-file <body>` (summary, how it works in 5 bullets, cost comparison, contract changes, test count, note "stacked on #27 — retarget to main after #27 merges", trailer `🤖 Generated with [Claude Code](https://claude.com/claude-code)`). Do not merge.

---

## Self-review notes

**Spec coverage:** chat extraction (T1, T6); resolver + editor + rejection reasons + relaxed tier rules (T2, T3); text refresh best-effort with whitelisted fields (T4); new rows, parent link, V8, edit_count (T5, T8); graph node + routing + brief-change precedence + no-packages-yet (T6, T7); limits (T7 node check, T8 counting); inline delivery + contract additions (T8, T9); logging hygiene (T4, T7); error handling INTERNAL (T3, T7); tests at every layer + integration (all); rollout/docs (T9).

**Deliberate deviation from the spec (Ruling):** the spec says the sink increments `edit_count`; the plan moves that to `AiSessionService.turn` before its single session save, because the request thread saves its detached session after the graph run and would overwrite a counter written by the sink (the lost-update pattern fixed in the previous feature). Task 9 updates the spec sentence.

**Type consistency:** `EditRequest` (T1) → `PackageEditor.apply` (T3) → `EditReport.allRejected` (T5) → `ChatTurnNode` (T6) → `ApplyEditsNode` (T7); `AppliedEdit` 7-component shape (T3) used by `TextRefresher` (T4) and `EditDTO` mapping (T8); `GenerationEditSink.edited(UUID, ComposedPlan, EditReport, LlmUsage) → UUID` (T5) used by T7; `PlannerState.EDITS / EDIT_REPORT / EDITS_LEFT / ACTION_EDIT` (T6) used by T7 and T8; `TurnOutcome` 4-component shape with 2-arg overload (T8).

**Known drift points to verify during implementation:** `PromptTemplate` handling of an optional block (fallback: render the block in Java and pass one `{packagesBlock}` string); `ChatTurnRequest` gaining components may affect `SpringAiLlmGateway`'s request construction (only through the overload); `TestPlannerGraphs` overloads must keep every existing test compiling.
