# AI planner — package concept, chat hooks and generation audit

Date: 2026-09-29 · Branch: `feat/ai-planner-diagnostics-and-structure`

Results only. Numbers come from the competitor pages listed under **Sources** and from this repository.
Each suggestion says what its evidence is. **Not measured** means no one has run it yet.

Assumption from the owner: group size and dates are picked in the UI before the chat starts.

---

## 1. What ready-made stag packages are made of

Sample: **33 ready-made packages** from 4 operators (Pissup 18 across Budapest/Prague/Krakow/Berlin,
The Stag Company 8, The Stag & Hen Experience 2, Freedom 5 — the last three Prague).
Chilli Sauce's package contents are loaded client-side and could not be read; its Prague product catalogue
(names only) was used for the variant ladders below.

| Component | Packages |
|---|---|
| Night out (guided bar crawl / club entry) | 29 / 33 |
| Accommodation | 19 / 33 |
| Shooting | 13 / 33 |
| Airport transfer (named explicitly) | ≥ 12 / 33 |
| Dinner with a strip show ("Steak & Strip", "Burger & Strip", "Steak & Tits") | 11 / 33 |
| Prank on the groom (dwarf, stag arrest, stripper prank, sexy wake-up) | 10 / 33 |
| Boat (river cruise, beer boat, booze cruise, party boat) | 8 / 33 |
| Karting | 7 / 33 |
| Paintball | 6 / 33 |

### The pattern behind them

Every package fits one shape:

```
package = night-out spine            (every night; 29/33)
        + one daytime "hero" activity (shooting, karting, paintball, rafting, …; names the package)
        + one centrepiece × modifier  (dinner × show | boat × unlimited drinks | crawl × club entry)
        + logistics                   (nights, airport transfer; 19/33 and ≥12/33)
```

- **The hero activity names the package.** "Pole Position" is karting, "Paints and Sinners" is paintball,
  "Bubble Trouble" is bubble football, "On The Splash" is rafting. Pissup uses tier words instead
  (Budget / Standard / Epic / Full On).
- **The same product is sold on a modifier ladder.** From Chilli Sauce's Prague catalogue:
  - Bar crawl: 5 beers → 10 beers → unlimited beer → with nightclub and lap-dance club entry.
  - Paintball: 200 balls → unlimited balls → unlimited balls and beer.
  - Shooting: pistol → AK-47 → AK-47 and 3 guns → military package.
  - Dinner: 2 courses → medieval banquet with 3 or 5 courses → folklore dinner with unlimited drinks → steak dinner with strip show.
  - Boat: 1-hour cruise → unlimited drinks → beer boat → boat party.
  - Transfer: minibus → limo → strip hummer / strip party bus.
- **Tiers go up by moving up the ladders and adding items.** Pissup's Budget/Basic packages list 2–4 items. Its Epic/Full On
  packages list 9–13, except Prague Epic with 4. At Pissup, accommodation appears in 4 of 18 packages (the priciest Budapest one,
  Prague Big groups, Krakow Full On and Selection w/ acc). The other three operators include it in every package.
- **In bundles, the strip show is attached to a carrier.** It rides on dinner (11/33 packages) or a boat cruise, and in Chilli Sauce's
  catalogue also on a spa, a poker night and airport transfers. Standalone products exist too (Pissup "Strippers", "Strip Club Entry").

## 2. Package concept for the planner (recommendation, not applied)

1. **One skeleton per tier: spine + hero + centrepiece.** Every night gets a night-out item. Each tier has one hero
   daytime activity the others don't. The current `TIER_NOT_DISTINCT` rule already requires one exclusive activity per tier,
   and the hero gives that rule a meaning. Each tier has one centrepiece.
2. **Tiers climb the ladder instead of only adding items.** Where the catalog has variants of a product, BASIC takes the
   lowest variant, MEDIUM the middle one, PREMIUM the top one. This needs a `variantGroup` + `level` on activities.
   That is a catalog decision; names alone are not reliable enough to group on.
3. **Name packages after their hero activity** (the market convention), in keyword form ("Karting · Steak & Strip · Club").
   The copy prompt in this PR already asks for keyword titles.
4. **Accommodation and airport transfers** are in 19/33 and ≥12/33 packages but are not in the Trivlu catalog. This is still
   a product decision (see the earlier audit, D1/D2); the concept works without them.

## 3. Hook and prefilled messages (implemented)

With dates and group size already set in the UI, the first question is what the weekend is built around.
The opening chips are the most repeated bundles above, in order of frequency. Each chip appears only when
the destination has the categories to deliver it:

| Chip (en) | Frequency | Shown when the destination has |
|---|---|---|
| Bar crawl + club night | 29/33 | `nightlife` |
| Shooting range + night out | 13/33 | `shooting` |
| Steak dinner with a show | 11/33 | `dining` and `show` or `adult` |
| A prank on the groom | 10/33 | `prank` |
| Karting by day, club by night (backfill) | 7/33 | `driving` |

After every turn the model returns 2–4 `suggestedReplies` that answer its own question. The chips are
catalog names or concrete choices, never open text.

**What this is and isn't based on:** the order follows how often the market repeats each bundle. No conversion
data exists for any of it; the competitor pages don't publish any. The claim is "the bundles operators
sell most". It is **not** "the chips that convert best".

To find out, measure (**not measured**):
- chip tap rate per position;
- turns until the brief is ready;
- generation → package selection rate.

Then A/B the chip order against a random order.

## 4. Interaction structure (implemented)

- **Taste first.** Fields the pickers already set are never asked again. The greeting is "Hey! 3 days, 8 people -
  noted. What should the weekend be built around?".
- **Centrepiece → one either/or question built from real variants.** When the organizer picks a centrepiece and the
  catalog holds two or more variants of it, the chat asks once, in plain words. For example: "Dinner - just steak and
  beers, or with a show?". The chips then name the catalog variants. Only catalog names are allowed, which is why the chat now
  sees the catalog from the first turn (snapshotted when the session is seeded).
- **At most one question per reply.** Nothing is asked once the brief is complete; the build starts immediately (unchanged).

## 5. Itinerary output format (implemented)

Copy and chat replies are keyword lines, not paragraphs:

```
title        Karting · Steak & Strip · Club night
tagline      Two activities a day, unlimited beer on the boat
description  • Karting - 2 × 10 min, transfers
             • Steak & Strip Dinner - 3 beers, 15 min show
day summary  Karting → Steak dinner → Club
why          Asked for: karting, big night out
```

Hype words, emoji and markdown are banned. Inclusions may only be stated when the catalog's `includes` says so.

**Evidence:** Nielsen Norman Group's web-writing study (1997) measured usability **+58 %** for concise text,
**+47 %** for a scannable layout (bulleted lists, highlighted keywords), **+27 %** for objective language (no "marketese"),
and **+124 %** with all three combined. It was not measured on this product.

## 6. Generation strategy audit

Measured values come from `docs/superpowers/specs/2026-09-28-ai-planner-skeleton-first-design.md`
(live runs through OpenRouter). Limits come from `application.properties` and the code.

**Measured**

- Old single-call design: **40 s** of planner time, **80 s** with one repair.
- About **95 completion tokens/s**; the first live skeleton was **1,224 tokens**.
- The screenshot run: **25 s** to a degraded result.

**Critical path, worst case**

A generation runs these steps in sequence:
1. chat turn: ≤ 20 s (qwen3.7-plus);
2. compose: ≤ 60 s (qwen3.8-max);
3. repair: ≤ 60 s (qwen3.8-max);
4. copy: ≤ 40 s (three calls in parallel).

The generation job alone can take **160 s**. `docs/api/ai-planner-api.md` tells the frontend to
**give up polling after 90 s**. A run that needs a repair after a slow compose can therefore still be working
when the UI has already shown an error.

### Core issues and suggestions

| # | Issue (verified in code) | Suggestion | Evidence |
|---|---|---|---|
| P1 | A failed call (timeout / unavailable / bad JSON) leaves an empty draft that goes to **repair**: the same model, the same 60 s budget, a longer prompt (compose prompt + repair message). | After a failed call, go straight to the deterministic fallback, or retry compose once with a shorter budget. Keep repair for real rule violations. | Code path `DraftAttempt` → `ValidateNode` → `RepairNode`. The diagnostics added in this PR will show how often it happens. |
| P2 | The polling budget (90 s) is shorter than the job's worst case (160 s). | Align them: planner timeout ≤ 30 s (a 1,224-token skeleton at ~95 tok/s decodes in ≈ 13 s), or raise the poll limit. | Measured throughput above; the rest is arithmetic. |
| P3 | `plannerUser` puts the per-session BRIEF and chat history **before** the catalog listing. Repair re-sends the whole compose prompt. | Put stable content first (system prompt, catalog), variable content last, and mark the compose prompt for **explicit context cache**. Repair then reads it at a cache hit. | Alibaba Model Studio docs: explicit cache supported on `qwen3.8-max` and `qwen3.7-plus`, minimum 1,024 tokens, hit billed at ~10 % of input price "with improved response speed", 5-min validity; guidance "place duplicate content at the beginning". Latency gain not measured here. |
| P4 | `TIER_ORDER` and `DAY_OVER_MINUTES` ask the model to do arithmetic (group-minimum pricing, minute sums with 30-min buffers). A miss costs a full model repair. | Repair deterministically first: re-key tiers by price when every per-tier cap still holds; drop the lowest-ranked item from an over-long day. Call the model only if violations remain. | Rules and data are all in `PlanValidator` / `PlanAssembler`; the fix needs no model. Violation rates not measured; use the new diagnostics. |
| P5 | The model schedules slots, although slots, caps and windows are fully determined by Java (`allowedSlots`, `Tier` caps). | Split the work: the model picks **which** activities per tier (taste); Java places them into slots (rules). `FallbackPlanComposer` already schedules deterministically. | Every scheduling violation code is a deterministic check. The quality effect is **not measured** (compare on a fixed brief set). |
| P6 | The chat turn now carries up to 80 catalog names (added in this PR for the pairing follow-up). | Watch chat latency and tokens after rollout; if needed, send only the names in the categories the brief touches. | `LlmUsage` per call is already logged (`llm call model=… latencyMs=… promptTokens=…`). |

**How to verify all of the above:**
1. Build a fixed set of briefs, including the screenshot case (3 days, 8 people, EVENING → MORNING).
2. Run each 20× before and after each change.
3. Compare p50/p95 time-to-skeleton, the repair rate and the fallback rate, using the new `diagnostics`.

---

### Sources

- Pissup: [Budapest](https://www.pissup.com/budapest-stag-do/), [Prague](https://www.pissup.com/prague-stag-do/),
  [Krakow](https://www.pissup.com/krakow-stag-do/), [Berlin](https://www.pissup.com/berlin-stag-do/),
  [activities](https://www.pissup.com/stag-activities/)
- [The Stag Company — Prague](https://www.thestagcompany.com/prague-stag-weekends)
- [The Stag & Hen Experience — Prague](https://thestagandhenexperience.com/location/stag/prague)
- [Freedom — Prague](https://www.freedomltd.com/prague-stag-weekends/)
- [Chilli Sauce — Prague catalogue](https://chillisauce.com/stag/in-prague), [Prague packages](https://chillisauce.com/stag/in-prague/packages)
- [Nielsen Norman Group — Concise, SCANNABLE, and Objective](https://www.nngroup.com/articles/concise-scannable-and-objective-how-to-write-for-the-web/)
- [Alibaba Model Studio — context cache](https://www.alibabacloud.com/help/en/model-studio/context-cache)
