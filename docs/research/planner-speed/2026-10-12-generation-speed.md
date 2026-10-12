# Generation speed: where the time goes and what shortens it

**Date:** 2026-10-12 · **Measured on:** local backend, production-size Prague catalogue (72 activities, 9 ready-made
packages), OpenRouter -> Alibaba, `qwen/qwen3.7-max` (compose) and `qwen3.7-plus` (chat, copy).
**Applied so far:** one change (section 4). Everything else here is a measured recommendation, not shipped.

## 1. Where the time goes today

From the backend log of one day of runs:

| Step | Model | Calls | Median | Prompt tokens | Output tokens |
|---|---|---|---|---|---|
| Chat turn (reads the message, fills the brief) | plus | 38 | 3.8 s (2.4 - 5.6) | ~4,300 | ~146 |
| Compose (which activity, which day, which slot) | max | 6 | 5.6 s (4.8 - 6.3) | ~8,400 | ~294 |
| Copy (titles, taglines, descriptions), 3 calls in parallel | plus | 15 | 7.0 s (6.1 - 8.3) | ~870 | ~316 |
| Everything in Java (catalogue, validation, pricing, storing) | - | - | under 0.1 s | - | - |

The steps run one after another: about **10 s until the plan is on screen** (chat + compose; the page shows a
status meanwhile) and about **18 s until its copy is written**.

Two facts decide everything below:

- **Time follows the tokens the model writes, not the ones it reads.** The copy call reads 870 tokens and is
  the slowest; compose reads 8,400 and is faster. Each call costs about 1.3 - 1.5 s before the first token and
  then roughly 20 ms per output token.
- **The prompt is already cached by the provider:** 8,064 of 8,203 prompt tokens were served from cache on
  every benchmark call.

## 2. Benchmark: seven ways to run compose

`bench-compose.py`, five runs each, streaming, the real system prompt and catalogue (8,203 prompt tokens against
8,382 in production; baseline 5.9 s against 5.6 s in the log). Every answer was checked against the scheduling
rules. Raw numbers: `bench-compose-2026-10-12.json`.

| Variant | Total (median) | Output tokens | Answers with no rule broken |
|---|---|---|---|
| Today: max, full catalogue, object-per-item answer | 5.9 s | 284 | 3 of 5 |
| + provider sorted by throughput | 6.0 s | 297 | 1 of 5 |
| Catalogue without "about" and "includes" (3,496 prompt tokens) | 6.2 s | 298 | 3 of 5 |
| **max, compact answer** (`{"BASIC":[["E:A12"],["M:A3","N:A7"],[]], ...}`) | **2.6 s** | **64** | **4 of 5** |
| plus, today's answer shape | 7.3 s | 342 | 5 of 5 |
| plus, compact answer | 2.6 s | 66 | 1 of 5 |
| 3.8-flash, compact answer | 3.1 s | 74 | 1 of 5 |

Reading:

- **A compact answer more than halves compose (5.9 -> 2.6 s)** and it broke fewer rules with the same model: the
  day count is part of the shape, so a day cannot be left out. Its one bad answer was a slot used twice, which
  Java already corrects without a model call.
- **Shrinking the prompt does nothing** (6.2 s): it is cached, and reading is not what takes the time.
- **Provider routing does nothing:** one provider serves these models.
- **A cheaper model is not faster in today's shape** (plus: 7.3 s) and in the compact shape the cheaper models
  broke rules in four answers of five, including ones Java cannot correct (a fourth day).
- What this benchmark does **not** say: whether the compact answer picks activities as well as today's. It checks
  the rules, not the taste. That needs a side-by-side read of real plans before shipping.

Every "today's shape" failure was the same one: the model left out the last day because nothing happens on it.
In production that draft is rejected (`WRONG_DAY_COUNT`) and repaired with a **second compose call**, so about
two builds in five took roughly 6 s longer than the table in section 1.

## 3. The strategies people ask about, against this pipeline

| Strategy | Source and claim | Here |
|---|---|---|
| Write fewer tokens | OpenAI latency guide: cutting half the output tokens cuts about half the latency; Anthropic says the same | The biggest lever: compact compose answer (measured, -56%) |
| Make fewer requests | OpenAI latency guide: merge sequential steps into one call | Chat turn + compose are two round trips (3.8 + 5.6 s); the first message can skip or merge the first |
| Skeleton first, details in parallel | Skeleton-of-Thought (Ning et al., ICLR 2024): up to 2.39x faster | **Already how the planner works:** structure first, shown at once, then three copy calls in parallel |
| Parallel calls planned up front | LLMCompiler (Kim et al., ICML 2024): up to 3.7x against sequential tool calls | Applies only where steps do not depend on each other. Compose needs the brief; the three tiers must differ from each other. One call per tier would save under a second (each still pays 1.3 - 1.5 s to the first token) for three times the cost: not measured, not recommended |
| Do it in code | this repo's own history: day arithmetic moved to Java on 2026-09-29 | The copy is "activity names joined by ·", "A -> B -> C", "• name - what is included": all of it is in the catalogue. Writing it in Java removes the 7 s tail and the one place the model can still state a false inclusion |
| Prompt caching | OpenRouter: automatic with providers that support it | Already happening (8,064 cached tokens per call) |
| Faster provider | OpenRouter `provider.sort` / `:nitro` | No effect: single provider |
| Streaming | Anthropic: time to first token down by up to 70% | Helps a chat reply feel faster; the plan itself is JSON and is only useful whole |
| Cache the result | - | The start screen offers fixed messages. Same message + same catalogue = same brief: the plan could be served from a cache. Not measured |

## 4. Applied on 2026-10-12

**A day the model leaves out is added as an empty day in Java** (`PlanTrimmer.withMissingDays`) instead of being
sent back to the model. In the benchmark that was two answers in five; each used to cost a second compose call of
5 - 6 s. Covered by `PlanTrimmerTest`; the full backend suite passes (1,424 tests). Not yet measured over many
live builds: the claim is the removal of that second call, not a number.

## 5. Recommended order

| # | Change | Expected | Basis | Risk |
|---|---|---|---|---|
| 1 | Compact compose answer | compose 5.9 -> 2.6 s | measured, n=5 | plan quality unread; touches schema, prompt, parser, repair prompt |
| 2 | Copy written in Java from the catalogue | plan complete 7 s sooner; no model text about inclusions | arithmetic on measured parts | tagline and "why" become plainer |
| 3 | First message without its own chat call (fixed starters map to a brief in code; or one merged call) | 3.8 s off the first build | arithmetic on measured parts | typed first messages still need the model |
| 4 | Cache the plan for a fixed starter and catalogue version | first plan near-instant for those | not measured | everyone tapping a starter sees the same three options |

Projection, from the measured parts and not from an end-to-end run: today about 10 s to the plan and 18 s to the
finished copy. With 1 and 2: about 6.5 s, finished. With 3 as well: about 3 s.

## Sources

- OpenAI, Latency optimization: https://developers.openai.com/api/docs/guides/latency-optimization
- Anthropic, Reducing latency: https://docs.anthropic.com/en/docs/test-and-evaluate/strengthen-guardrails/reduce-latency
- Ning et al., Skeleton-of-Thought: https://arxiv.org/abs/2307.15337
- Kim et al., LLMCompiler: https://arxiv.org/abs/2312.04511
- OpenRouter, provider routing: https://openrouter.ai/docs/features/provider-routing
- OpenRouter, prompt caching: https://openrouter.ai/docs/features/prompt-caching
