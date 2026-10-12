# Itinerary generation strategy: review, evidence, reverse test

**Date:** 2026-10-12
**Code reviewed:** `origin/main` @ `0cfd214` (`myhive-backend/src/main/java/com/myhive/backend/ai`)
**Status:** the one code change (section 4) and the fuzz test are in the repository since 2026-10-12; the test
runs as a gate, see `qa/PRE-MERGE-AUDIT.md`, audit 2.

## Verdict

Keep the strategy the planner already uses: **the model chooses, Java schedules, prices and
verifies, and a deterministic composer takes over when the model fails.** The published evidence
says this family is the only one that reaches high validity on travel planning, and the reverse
test below shows the Java half holds on the production catalogue.

The reverse test found one real hole in it. The fallback, the last stop, was the only stage whose
output nothing checked, and it shipped plans that break the tier rules. The patch closes it.

What this document does **not** prove is listed under "Gaps" and is not small: the free-text copy
and the live model's error rate were not measured.

## 1. The mechanic in use today

```
chat turn (model)  ->  brief {days, group, categories, vibe, wishes}
snapshotCatalog    ->  up to 80 activities + ready-made packages per tier   (Java)
compose (model)    ->  STRUCTURE ONLY: tier -> day -> slot -> activity code (A1..An)
validate           ->  PlanValidator + PlanAssembler                        (Java)
   failing draft   ->  PlanTrimmer corrects arithmetic slips                (Java)
   still failing   ->  one repair call (model), validated the same way
   still failing   ->  FallbackPlanComposer, greedy, degraded=true          (Java)
publishSkeleton    ->  packages visible
writeTexts (model) ->  titles, taglines, descriptions, "why"                (free text)
WishApplier        ->  activities asked for by name are forced in           (Java)
```

What already keeps the model honest:

| Risk | Guard | Where |
|---|---|---|
| Invented activity | strict JSON schema, `activityId` is an enum of the catalogue codes | `ResponseSchemas.plan` |
| Invented name or price | the model never writes either; both are read from the catalogue | `PlanAssembler` |
| Day too long, slot clash, repeat | validator, then a Java correction instead of a re-prompt | `PlanValidator`, `PlanTrimmer` |
| Model down or garbled | per-call timeout, one repair, deterministic fallback | `SpringAiLlmGateway`, `PlannerGraph` |
| Named wish ignored | applied in Java after generation | `WishApplier` |

The project's own history agrees with the literature. On 2026-09-29 every live generation ended
degraded because the model overfilled days (`DAY_OVER_MINUTES`) and the repair call returned a day
just as long; it was fixed by correcting in Java, not by a better prompt
(`docs/changes/2026-09-29-ai-planner-diagnostics-and-structure.md`).

## 2. What the published work says

| Source | Approach | Result as stated by the authors |
|---|---|---|
| Xie et al., TravelPlanner, ICML 2024 | model plans alone, with tools | "even GPT-4 only achieves a success rate of 0.6%" |
| Gundawar et al., LLM-Modulo, 2024 | model proposes, sound verifiers critique, re-prompt | 4.6x over baseline for GPT-4-Turbo; GPT-3.5-Turbo from 0% to 5% |
| Hao et al., NAACL 2025 | model translates the request, an SMT solver produces the plan | 93.9% on TravelPlanner; the best model alone (o1-preview) 10% |
| de la Rosa et al., TRIP-PAL, 2024 | model gathers and translates, an automated planner plans | constraint satisfaction guaranteed by the planner; no single headline number |
| Google Research, 2025 (in Search) | Gemini drafts, an optimiser repairs with substitutes | in production; the post gives no numbers |
| Tang et al., ItiNera, EMNLP 2024 industry | model selects, spatial optimisation orders | deployed; numbers not checked here |
| Tam et al., "Let Me Speak Freely?", 2024 | format restrictions on model output | "a significant decline in LLMs reasoning abilities under format restrictions" |

Reading:

1. A model alone cannot be trusted to satisfy constraints. This is the best-replicated result.
2. Re-prompting with verifier feedback helps, but modestly. The project saw the same: its repair
   call rarely fixed an overfilled day.
3. The jump to high validity comes when deterministic code produces or repairs the schedule and
   the model only handles the qualitative part.
4. Strict JSON output can reduce reasoning quality. That is an argument for the current split, in
   which the constrained call is asked for choices only and the arithmetic is done in Java.

### Why not a full solver (SMT or CP-SAT)

The constraint set here is small: four slots a day, a per-tier item and minute cap, a closed
catalogue of at most 80 activities, and three tiers that must rise in price and differ. A greedy
composer plus an independent check was sound on every Prague brief shape tested (see below). A
solver would add a dependency to a JVM that is already being OOM-killed at 512 MiB. It becomes
worth it only for the residual case in section 4 (very small catalogues).

## 3. Reverse test

**Method.** The model was replaced by an adversary, and the plan that would ship was judged by an
oracle written for this test that shares no code with `PlanValidator`: three packages, right day
count, every activity in the catalogue with the catalogue's name and price, line and package
prices recomputed, no repeats, slots inside the arrival/departure window, one activity per slot,
item and minute caps, no empty middle day, prices strictly rising, each tier with an activity of
its own.

**Data.** The production catalogue as served by the public API on 2026-10-12: Prague, 82
activities and 9 tiered ready-made packages; plus the other 24 destinations.

**Harness.** `PlannerInvariantFuzzTest`. Run with
`PLANNER_FUZZ_DIR=<dir with acts.json and pk.json> ./gradlew test --tests '*PlannerInvariantFuzzTest'`.
Raw output: `fuzz-report-prague.json`, `fuzz-report-other-destinations.json`.

### Results, Prague

| Experiment | Runs | Plans breaking a rule, before | After the patch |
|---|---|---|---|
| Fallback alone, every brief shape (days 1-7, group 2-30, 5x5 arrival/departure, 4 category sets, with and without ready-made packages) | 40,600 | 1,394 (3.4%) | 0 |
| Model writes anything the schema allows, repair assumed to fail | 20,000 | 1,205 (6.0%) | 0 |
| Model overfills days the way the September runs did, repair assumed to fail | 20,000 | 1,263 (6.3%) | 0 |

- **Validator soundness:** of the 40,000 adversarial drafts, the validator and assembler accepted
  12,847 as written or after trimming. The independent oracle rejected none of them.
- **Every broken plan came from the fallback.** Rules broken: tier price order (all cases) and
  tiers not distinct (about a third). 1,094 of the 1,394 were one-day trips: a ready-made package
  laid over fewer days than it was made for loses activities, and the tiers collapse onto each
  other (example: 37 / 55 / 55 EUR per person). The rest were groups of two, where group minimums
  put MEDIUM above PREMIUM (101 / 302 / 220).
- **Without ready-made packages the fallback broke nothing** (0 of 20,300), which is what makes
  it usable as the final link.

### Results, other destinations (patched fallback)

121,800 runs over 24 destinations: 23 destinations clean, **Baku 296 broken** (17 activities,
one-day trips, tier price order). The chain has no sound plan to fall back to there.

### What the two "model" experiments are not

They are synthetic. They show what ships when the model misbehaves; they do not say how often the
real model misbehaves. The split between accepted, corrected and fallback in those rows is a
property of the generator, not a forecast.

## 4. The change

`FallbackNode` now checks its own output and tries three forms, taking the first that the
validator and assembler both accept:

1. composed from the ready-made packages (as before);
2. the same after `PlanTrimmer`;
3. composed from the catalogue alone.

If none passes it ships the first form as before and logs a warning with the brief shape.

Verified: the real patched node over all 40,600 Prague brief shapes, 0 broken; the `ai` package
test suite, 555 tests, 0 failures, 6 skipped. The full backend suite was not run.

Applied on 2026-10-12 (`FallbackNode`, `PlannerInvariantFuzzTest`).

## 5. Why this is the most rugged option for this project

- The failure classes that code can decide are decided by code: invented activity, invented
  price, schedule arithmetic, tier order. For those, a wrong plan is now unreachable on the Prague
  catalogue in every shape tested, whatever the model writes.
- The model's part is the part the literature says models are good at: reading taste and picking.
- Volume is low, so one constrained call plus Java is enough; no agent loop, no solver, no extra
  memory.
- It degrades instead of failing: a dead model still yields a valid plan.

"Bulletproof" holds for structure and price. It does not yet hold for the text.

## 6. Gaps and uncertainties

Not measured or not closed. None of these is assumed to be fine.

1. **Free text is unverified.** Titles, taglines, descriptions, "why" lines and chat replies are
   model prose at temperature 0.7, guarded by the prompt only. A description can claim an
   inclusion the catalogue does not list. No measurement of how often. The rugged fix is to build
   the factual lines from catalogue fields in Java and let the model write only the tagline.
2. **Live model error rate is unknown.** No live calls were made for this review. Needed: a fixed
   set of at least 50 briefs run against the production model and catalogue, counting accepted,
   corrected, repaired and degraded from `ai_generations.diagnostics`.
3. **Fit to the brief is not checked by code.** Dislikes, budget and vibe relevance are prompt
   instructions. A structurally valid plan can still contain something the group excluded.
4. **Brief extraction is not checked.** The chat model reads days, group size and categories from
   prose. UI presets reduce this; it was not measured.
5. **Baku, one-day trips:** 296 of 5,075 shapes have no sound fallback. Needs either a
   search-based composer or a rule that small catalogues may offer fewer than three tiers.
6. **Other destinations have no durations.** In 19 of 24 every activity is missing one and is
   planned as 120 minutes, so the minute caps there check an assumption. They also have no tiered
   ready-made packages.
7. **Infrastructure is the larger reliability risk today.** The 2026-10-12 failure was the JVM
   dying mid-generation on the 512 MiB instance; no generation strategy survives that.
8. **Research coverage.** Papers and the Google post were read from primary pages. GitHub and
   forum material was only searched, not studied; the benchmark repositories found (TripTailor,
   TripCraft, TREK) were not evaluated. The OpenAI "100% schema adherence" figure was seen only in
   secondary sources and is not relied on here.
9. **Optional next step, tested only synthetically:** re-scheduling the model's picks in Java
   before giving up on a draft kept 790 more of 20,000 overfilled drafts (4%) and 4,435 of 20,000
   random ones (22%) out of the fallback. Worth doing only if gap 2 shows the fallback rate is
   high.

## Sources

- TravelPlanner: https://arxiv.org/abs/2402.01622
- LLM-Modulo case study: https://arxiv.org/abs/2405.20625
- Formal verification tools (Hao et al.): https://arxiv.org/abs/2404.11891
- TRIP-PAL: https://arxiv.org/abs/2406.10196
- Google Research, optimizing LLM-based trip planning: https://research.google/blog/optimizing-llm-based-trip-planning/
- ItiNera: https://arxiv.org/abs/2402.07204
- Let Me Speak Freely?: https://arxiv.org/abs/2408.02442
