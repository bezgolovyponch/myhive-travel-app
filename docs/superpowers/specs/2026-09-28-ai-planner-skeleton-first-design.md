# AI Planner — skeleton first, texts second — Design

**Date:** 2026-09-28
**Source:** owner, after testing the planner live through OpenRouter: "Почему так долго
отвечает модель?" — a generation took 40 s of planner time (80 s with a repair) before a
single package was visible. Owner picked this as step 2, after step 1 (arrival/departure
asked before the first generation, `a4f58ba`).
**Apps:** `myhive-backend` only. Extends [`docs/api/ai-planner-api.md`](../../api/ai-planner-api.md)
additively (v1.4).
**Builds on:** [`2026-09-15-ai-stag-planner-design.md`](2026-09-15-ai-stag-planner-design.md)
and [`2026-09-17-ai-planner-edits-design.md`](2026-09-17-ai-planner-edits-design.md).

## Problem

The planner model writes the whole plan in one answer: three packages, every day, every
item with its `why`, plus titles, taglines, descriptions and day summaries — about 4 000
completion tokens at ~95 tokens/s. The structure the validator, the pricer and the
organizer actually wait for is a few hundred of those tokens; the rest is copy. A repair
re-writes all of it again. Nothing is visible until the last token has arrived.

## Decisions

| Question | Decision | Why |
|---|---|---|
| What the planner model returns | The **skeleton** only: `packages[].days[].items[]` with `slot`, `startHint`, `activityId`. No titles, no texts. | A few hundred completion tokens instead of ~4 000; a repair re-writes the same few hundred |
| How activities are named in prompts | By **code**, `A1`, `A2`, ... by position in the catalog snapshot (`ActivityAliases`), in the catalog listing, the repair draft and the texts listing; the parser resolves codes back to ids before anything else sees them, an unknown code becomes the nil id (validator: `UNKNOWN_ACTIVITY`) | The first live skeleton was 1 224 tokens, twice the estimate: a UUID is ~25 tokens and a plan writes twenty of them. Codes cost two |
| Who writes the copy | The **chat model** (`qwen3.7-plus`, temperature 0.7, own budget `app.ai.texts-timeout`, default 40 s), **one call per package, the three in parallel** on `planTextsExecutor` (6 threads, CallerRuns): package title / tagline / description, day title / summary, item `why` | Three packages of copy in one answer (~2 400 tokens) ran past the budget; one package is a third of that, and a package that fails keeps only its own placeholders |
| When the packages become visible | As soon as the skeleton is validated, repaired if needed, and priced — **before** the texts call — the job writes the packages onto the still-`RUNNING` row (`publishSkeleton`). `GET /ai/generations/{id}` then serves `packages` with `textsPending: true` | That is the ~10 s the owner asked for; the poller can render cards and fill the copy in when `READY` arrives |
| Status semantics | Unchanged: `RUNNING` until the texts are in, then `READY`; a `RUNNING` row with packages is the new intermediate. Edits and selection stay refused (`GENERATION_IN_PROGRESS`) until `READY` | The graph run stays atomic, so the "a READY row means the thread is parked" invariant that `ensureParked` relies on is untouched |
| Placeholder copy | Before the texts call, and wherever the model's answer falls short: package title = the tier's stock name (`Warm-up` / `Main Event` / `Full Send`, German `Warm-up` / `Hauptprogramm` / `Volle Kanne`), day title = `Day N` / `Tag N`, everything else `null` | The same names the deterministic fallback already used; one `PlaceholderTexts` owner for both |
| Texts call fails | The generation still becomes `READY`, with the placeholders; a WARN line names the generation. No `degraded` flag: the packages are the model's, only the copy is stock | Copy is cosmetic; a valid, priced plan must never be lost over it |
| Fallback plans | Go through the same texts call | A degraded plan with real copy reads better than one with none; the flag still says it was auto-composed |
| Accounting | `usage` on the row is the planner call plus the texts call (`LlmUsage.plus`): tokens and latency summed, the planner's model name | One generation, one cost |
| Parser and merge | One whitelisted reader, `parsePackageTexts`, for both the first write and the post-edit refresh; the refresh keeps writing only description / why / summaries. Model copy is cleaned and cut to the validator's caps, never trusted to move an id, a slot or a price | Same seam as the edits' `TextRefresher` |

## Graph

```
snapshotCatalog → compose(skeleton) → validate ─┬─ ok ──────→ publishSkeleton → writeTexts → persistResult → awaitSelection ⏸
                                                ├─ 1st fail → repair → validate
                                                └─ 2nd fail → fallback ─┘
```

`publishSkeleton` and `writeTexts` follow the "a node must never throw" rule: a sink or
model failure is logged and the run continues with what it has.

## Contract (v1.4, additive)

- `GenerationDTO.packages` may be present while `status` is `RUNNING`. `textsPending`
  is `true` exactly then: structure and prices are final, titles are placeholders and
  taglines / descriptions / summaries / whys are `null`. Keep polling until `READY`.
- Nothing else changes: `READY`, `FAILED`, `degraded`, `kind`, edits.

## Out of scope

Streaming, a separate texts status, retrying the texts call, per-package texts calls.
