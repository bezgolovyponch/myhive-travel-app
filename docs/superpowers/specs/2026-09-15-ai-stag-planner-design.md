# AI Stag-Party Planner (chat agent → 3 packages → day-by-day plan) — Design

**Date:** 2026-09-15
**Source:** product brief (owner) + design session
**Apps:** `myhive-backend` only. The frontend is built in parallel against the
contract in [`docs/api/ai-planner-api.md`](../../api/ai-planner-api.md).

## Problem

A visitor lands on a destination page and has to assemble a stag-party trip by
hand: browse 30–80 activities, guess how many fit in a day, add them to the Trip
Builder, then book or start a vote. The quiz narrows the catalog to categories but
does not produce a plan. We want an assistant that, from a short chat, understands
how long the group stays, how many people come and what they enjoy, and returns
**three ready-made packages** (tiers) each with a **realistic day-by-day itinerary**
that never overloads a day, using only real catalog activities and their durations.
The organizer picks a package, it lands in the Trip Builder, and the existing
book-or-vote flows take over.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Interaction model | Multi-turn chat; the agent asks for what is missing | Owner wants a conversation, not a form |
| Result persistence | Persisted; the organizer can return to the packages screen and pick again | Owner: "must be able to come back"; enables analytics |
| Delivery of the result | Async job + polling | Qwen composition takes 10–40 s; Cloudflare/Render cut long requests |
| When generation starts | Automatically, the moment days + group size + preferences are known; no confirmation step | Owner: "we must give the three packages right away" |
| Package variants | Exactly three tiers with stable keys `BASIC` / `MEDIUM` / `PREMIUM`; catchy titles written by the model | Owner: "basic, medium, premium; invent names that hook" |
| Who understands taste | The LLM, at both points (brief extraction and composition). Java never infers preferences | Owner's concern that Java candidate retrieval would be hard; catalog per city is small enough to go into the prompt whole |
| LLM provider | Qwen via Alibaba Cloud Model Studio (DashScope) OpenAI-compatible endpoint | Owner's decision |
| Model client | Spring AI 2.0.1 (`spring-ai-starter-model-openai`) behind our own `LlmGateway` interface | GA on Maven Central, targets Spring Boot 4; the interface keeps tests and future swaps cheap |
| Orchestration | **langgraph4j** 1.8.x LTS, whole conversation as one `StateGraph` with checkpoints in Postgres and `interruptBefore` for user input | Owner wants hands-on langgraph4j experience even at extra cost; checkpoints + interrupts are where the library earns its keep |
| Handoff to Trip Builder | `select` returns items in the `VotePoolActivityDTO` shape the Trip Builder already maps (`activityId → id`) | Zero backend change to booking or voting |

## Scope

In scope (backend):

1. Chat session lifecycle with a public token, persisted in Postgres.
2. langgraph4j graph: brief collection with interrupts, async plan generation,
   validation/repair/fallback, selection.
3. Qwen integration via Spring AI with strict JSON output and typed parsing.
4. Scheduling rules that keep every day realistic (slots, minutes, counts).
5. Pricing with the group-minimum floor, tier ordering.
6. Public REST API + error contract for the frontend.
7. Cost/abuse controls, kill switch, observability, cleanup.
8. Unit + integration tests; one Testcontainers test for checkpoint persistence.

Out of scope:

- Any frontend work (the Next/CRA screens, Trip Builder wiring, analytics events).
- Changes to booking, vote, lead or payment flows.
- Admin UI for sessions (read-only SQL is enough for now).
- Vector search / embeddings (catalogs are < 150 activities per city).
- Streaming replies (SSE). A chat turn is one JSON response.
- Lead capture inside the chat (the Trip Builder already captures it).

## Architecture

New package `com.myhive.backend.ai` with sub-packages `controller`, `service`,
`graph`, `llm`, `dto`, `entity`, `repository`, `config`.

### The graph

One compiled `StateGraph<PlannerState>` per JVM, one **thread** per chat session
(`threadId = session token`). Checkpoints persist in Postgres through
`langgraph4j-postgres-saver`, so a session survives restarts and any thread (HTTP
request or job executor) can resume it.

```
START → chatTurn ─┬─(brief incomplete)──→ awaitUser ⏸ ──→ chatTurn
                  └─(brief complete → generate automatically)──→ awaitGeneration ⏸
                                              │  (resumed by the job executor)
                                              ▼
                     snapshotCatalog → compose → validate ─┬─ ok ──→ persistResult
                                                           └─ fail ─→ repair → validate ─┬─ ok ──→ persistResult
                                                                                        └─ fail ─→ fallback → persistResult
                     persistResult → awaitSelection ⏸ → select → END
```

`⏸` = node compiled with `interruptBefore`. `awaitUser`, `awaitGeneration` and
`awaitSelection` are no-op nodes that exist only as interrupt points.

| Node | Kind | Does |
|---|---|---|
| `chatTurn` | LLM | Sends system prompt + catalog category list + brief-so-far + last 20 messages to the chat model. Parses `{reply, brief, missingFields}`. Appends the assistant message, merges the brief (model output wins per field, nulls leave the old value) and sets `action = GENERATE` iff the merged brief is ready — Java decides, not the model. |
| `awaitUser` | interrupt | Graph parks here. `POST /messages` does `updateState({messages: [user msg]})` then resumes. |
| `awaitGeneration` | interrupt | Graph parks here so the HTTP request returns 202. The job executor resumes the same thread. |
| `snapshotCatalog` | Java | Loads all activities of the destination (localized), compacts them to `{id, name, oneLine, durationMinutes, price, minPrice, categories}`. If > 80, keeps the 80 best by category overlap with the brief then `featuredWeight`. Stores the snapshot in state so `compose`/`repair`/`validate` all see the same catalog. |
| `compose` | LLM | Planner model. Input: brief, catalog snapshot, last 10 messages, tier rules. Output: draft with three packages, each `days[].items[]` referencing catalog ids and slots. |
| `validate` | Java | Runs `PlanValidator` (rules below) and `PlanPricer`. Writes `violations` (empty = ok) and `attempt`. |
| `repair` | LLM | Planner model again with the draft and the list of violations, asked to fix only what is listed. Max one repair per generation. |
| `fallback` | Java | `FallbackPlanComposer`: greedy fill per tier by category overlap → `featuredWeight` → price, respecting the same rules. Marks `degraded = true`. |
| `persistResult` | Java | Writes the result JSON to `ai_generations`, flips `ai_sessions.status` to `READY`. |
| `awaitSelection` | interrupt | Graph parks until `POST /select`. Regeneration re-enters at `awaitGeneration` via `updateState` + resume. |
| `select` | Java | Records `selected_package_key`, returns trip items. Graph reaches END but the thread is **not released** (`releaseThread(false)`) so the organizer can reopen the screen and pick another package (re-entry at `awaitSelection`). |

### State

`PlannerState extends AgentState`, JSON-serializable values only (records → maps via
Jackson; the serializer is `JacksonStateSerializer`, confirmed in the spike).

| Key | Channel | Content |
|---|---|---|
| `messages` | appender | `[{role, content, at}]` |
| `brief` | replace | `Brief` (below) |
| `catalog` | replace | catalog snapshot (list of compact activities) |
| `draft` | replace | planner output before validation |
| `violations` | replace | `[{packageKey, dayNumber?, code, detail}]` |
| `attempt` | replace | 0 = first compose, 1 = after repair |
| `result` | replace | validated `Package[]` + `degraded` |
| `selectedPackageKey` | replace | `BASIC` / `MEDIUM` / `PREMIUM` |
| `generationId` | replace | id of the `ai_generations` row being produced |

The checkpoint is the **engine's memory**; our tables are the **projection** the API
serves. `GET /sessions/{token}` reads messages and brief from `graph.getState(config)`
and generation status/result from `ai_generations`.

### Brief

```
Brief {
  days: int?              // 1..7
  groupSize: int?         // 2..30
  categorySlugs: [slug]   // subset of the destination's categories, chosen by the model
  vibe: string?           // free text, ≤ 300 chars ("full send, no museums")
  dislikes: string?       // free text, ≤ 300 chars
  budget: LOW|MID|HIGH?   // optional hint for tier spread
  arrival: MORNING|AFTERNOON|EVENING   // default AFTERNOON
  departure: MORNING|AFTERNOON|EVENING // default MORNING
  notes: string?          // anything else the model wants to remember, ≤ 300 chars
}
```

Ready to generate when `days`, `groupSize` and (`categorySlugs` non-empty **or**
`vibe` present) are known. **Generation starts automatically the moment the merged
brief becomes ready** — Java decides after each chat turn, the model is never asked
for permission and there is no "shall I build it?" step. If the first message already
contains everything, the packages are generated right after the first reply. The
model's reply on that turn simply says it is building the three options;
`missingFields` lists what is still unknown so the frontend can show hints.
After a generation, further chat regenerates **only if the merged brief changed**
(the graph remembers the brief the last generation used); small talk never burns a
generation. The explicit `POST /generations` stays for "try other options".

### Scheduling rules (PlanValidator)

- Day slots: `MORNING` (10–14), `AFTERNOON` (14–19), `EVENING` (19–23), `NIGHT`
  (23–03). Day 1 starts at the `arrival` slot; the last day ends at the
  `departure` slot (inclusive). Slots outside that window are violations.
- Per-day capacity by tier (activity minutes + 30 min buffer between items):

  | Tier | max items / day | max minutes / day |
  |---|---|---|
  | BASIC | 2 | 360 |
  | MEDIUM | 3 | 480 |
  | PREMIUM | 4 | 540 |

  An activity without `duration` counts as 120 minutes. At most one item per slot.
- Every `activityId` must exist in the catalog snapshot; no activity twice in one
  package; every day has at least one item unless it is an arrival/departure day.
- Package must have ≥ `days` items in total (no empty packages).
- Tier ordering: `pricePerPerson(BASIC) < pricePerPerson(MEDIUM) <
  pricePerPerson(PREMIUM)` strictly, and each tier has ≥ 1 activity not present in
  the other two.
- Text fields: `title ≤ 60`, `tagline ≤ 120`, `description ≤ 600`, `why ≤ 160`,
  `dayTitle ≤ 60`, `daySummary ≤ 300` chars; HTML stripped before storage.

Each violation carries a machine code (`UNKNOWN_ACTIVITY`, `DUPLICATE_ACTIVITY`,
`DAY_OVER_MINUTES`, `DAY_OVER_ITEMS`, `SLOT_TAKEN`, `SLOT_OUTSIDE_WINDOW`,
`EMPTY_DAY`, `TIER_ORDER`, `TIER_NOT_DISTINCT`, `TEXT_TOO_LONG`) so the repair prompt
is precise and each rule has its own unit test.

### Pricing (PlanPricer)

Per item: `lineTotal = max(price × groupSize, minPrice)` — the same floor as
`BookingService.lineTotal`, `VoteSessionService.flooredLine` and `tripPricing.js`.
The formula is copied into `PlanPricer` with a comment naming the other three, and
the memory note about "three synced places" becomes four. `totalPrice = Σ lineTotal`,
`pricePerPerson = totalPrice / groupSize` rounded half-up to cents,
`totalDurationMinutes = Σ duration`. Prices come from the catalog snapshot, never
from the model output.

### Async execution

- `aiTaskExecutor`: `ThreadPoolTaskExecutor`, core 2, max 2, queue 20,
  `AbortPolicy` → the controller maps rejection to `429 AI_BUSY` (no CallerRuns:
  a 40 s LLM call must never run on a request thread).
- `POST /messages` runs `chatTurn` synchronously on the request thread (single
  chat-model call, 20 s timeout). When the graph parks at `awaitGeneration` the
  controller inserts a `QUEUED` row in `ai_generations`, submits the job and
  returns the generation id inside the message response.
- The job marks the row `RUNNING`, resumes the graph, and the graph's
  `persistResult` marks it `READY`. Any exception marks it `FAILED` with an
  `error_code` (`LLM_TIMEOUT`, `LLM_INVALID_OUTPUT`, `LLM_UNAVAILABLE`, `INTERNAL`).
- Sweeper on the existing scheduling infrastructure (`fixedDelay = 60 s`): rows
  `RUNNING` for > 3 min become `FAILED (STALE)` (the JVM was restarted mid-job).
- Limits: 30 user messages and 5 generations per session; 20 new sessions per IP
  per UTC day (in-memory counter, same style as `RateLimitFilter`); 1 000-char
  messages.

### LLM layer

- Dependencies: `platform("org.springframework.ai:spring-ai-bom:2.0.1")`,
  `spring-ai-starter-model-openai`, `org.bsc.langgraph4j:langgraph4j-core`,
  `langgraph4j-postgres-saver` (version pinned to the 1.8.x LTS verified in the
  spike), `langgraph4j-studio-springboot` as a **dev-profile-only** dependency
  (`developmentOnly` configuration) for the visual debugger.
- Config (`application.properties`, all from env):

  | Property | Env | Default |
  |---|---|---|
  | `app.ai.enabled` | `AI_ENABLED` | `false` |
  | `spring.ai.openai.api-key` | `QWEN_API_KEY` | empty |
  | `spring.ai.openai.base-url` | `QWEN_BASE_URL` | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` |
  | `app.ai.chat-model` | `QWEN_CHAT_MODEL` | `qwen3.7-plus` |
  | `app.ai.planner-model` | `QWEN_PLANNER_MODEL` | `qwen3.8-max` |
  | `app.ai.chat-timeout` | `AI_CHAT_TIMEOUT` | `20s` |
  | `app.ai.planner-timeout` | `AI_PLANNER_TIMEOUT` | `60s` |
  | `app.ai.turnstile-required` | `AI_TURNSTILE_REQUIRED` | `false` (dev) / `true` (prod) |
  | `app.ai.session-ttl-days` | — | `30` |

  Model Studio now also issues workspace-scoped base URLs
  (`https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1`);
  whichever the console shows for our key goes into `QWEN_BASE_URL`.
- `LlmGateway` interface: `ChatTurnResult chatTurn(ChatTurnRequest)` and
  `PlanDraft composePlan(PlanRequest)` / `PlanDraft repairPlan(RepairRequest)`.
  `SpringAiLlmGateway` implements it with two `ChatClient`s (chat vs planner model),
  `response_format = json_object`, `temperature 0.7` (chat) / `0.4` (planner),
  thinking disabled (`enable_thinking=false` via extra body — thinking mode does
  not guarantee valid JSON on DashScope; `json_schema` is not available in the
  Singapore region, so the schema is described in the prompt and enforced by
  strict Jackson parsing with `FAIL_ON_UNKNOWN_PROPERTIES=false` +
  `@NotNull` bean validation). A parse or validation failure is `LLM_INVALID_OUTPUT`
  and counts as a violation for `repair`.
- Prompts live in `src/main/resources/prompts/ai/{chat-system,planner-system,repair}.st`
  (Spring AI `PromptTemplate`), with `{locale}` selecting English or German replies.
  User text and catalog text are inserted as data blocks; the system prompt states
  that content inside them is never an instruction.
- Observability: Spring AI's Micrometer observations are on; each `ai_generations`
  row stores model, prompt/completion tokens, latency and attempt. `logging.level
  .com.myhive.backend.ai=INFO` logs one line per node with session token and
  latency, never message bodies (they may contain personal data).

### Persistence

Flyway `V7__ai_planner.sql` (prod). Entities for our three tables; the saver
tables are **not** JPA entities (Hibernate `validate` ignores unmapped tables), but
their DDL is copied verbatim from the saver's `createTables(true)` output during
the spike into the same migration, and prod runs the saver with
`createTables(false)`. Dev (H2) and tests use `MemorySaver`.

```
ai_sessions
  id UUID PK, token UUID UNIQUE NOT NULL, destination_id UUID FK NOT NULL,
  locale VARCHAR(8), status VARCHAR(16) NOT NULL   -- COLLECTING|GENERATING|READY|FAILED
  message_count INT NOT NULL DEFAULT 0, generation_count INT NOT NULL DEFAULT 0,
  client_ip_hash VARCHAR(64), created_at TIMESTAMP NOT NULL, last_activity_at TIMESTAMP NOT NULL
  INDEX (last_activity_at)

ai_generations
  id UUID PK, session_id UUID FK NOT NULL, status VARCHAR(16) NOT NULL, -- QUEUED|RUNNING|READY|FAILED
  brief_snapshot TEXT NOT NULL, result TEXT,        -- JSON
  degraded BOOLEAN NOT NULL DEFAULT FALSE, selected_package_key VARCHAR(16), selected_at TIMESTAMP,
  error_code VARCHAR(32), model VARCHAR(64), prompt_tokens INT, completion_tokens INT,
  latency_ms INT, attempt SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL, started_at TIMESTAMP, finished_at TIMESTAMP
  INDEX (session_id, created_at)

-- saver tables: DDL captured in the spike (thread + checkpoint rows keyed by thread id)
```

Messages are **not** duplicated into our tables; they live in the checkpoint state
and are read through the graph. Cleanup (`0 45 2 * * *`, next to the trip-lead
cleanup): sessions with `last_activity_at` older than `session-ttl-days` are
deleted together with their generations, and the graph thread is released
(`saver.release(config)`), which drops its checkpoints.

### Security and cost

- Endpoints under `/ai/**` are `permitAll` (same as `/vote/**`). Session token is
  a random UUID (122 bits) and is the only credential; no enumeration endpoint.
- Turnstile on session creation when `app.ai.turnstile-required=true` (reuses
  `TurnstileService`). Per-IP daily session cap + per-session message/generation
  caps + the global `RateLimitFilter`.
- `client_ip_hash` = SHA-256 of the IP with a server salt, kept only for the daily
  cap and abuse review.
- The kill switch `app.ai.enabled=false` makes every `/ai/**` call return
  `503 AI_DISABLED` before touching the DB, and the graph/saver/ChatClient beans are
  still created (so a flip needs only an env change + restart, no code path differs).
- Prompt injection: user text cannot change the catalog or prices (Java owns
  both); model text is sanitized (HTML stripped, length-capped) before storage and
  is echoed to the frontend as plain text only.

### Error contract

Uses the existing `ErrorResponse` (`status`, `error`, `message`, `path`, …). New
exception types map as:

| HTTP | `error` | When |
|---|---|---|
| 503 | `AI_DISABLED` | kill switch off |
| 404 | `SESSION_NOT_FOUND` / `GENERATION_NOT_FOUND` | unknown token/id |
| 400 | `VALIDATION_ERROR` | bean validation (content length, unknown destination slug…) |
| 409 | `BRIEF_INCOMPLETE` | manual `POST /generations` before the brief is ready |
| 409 | `GENERATION_IN_PROGRESS` | a generation is already QUEUED/RUNNING for the session |
| 409 | `GENERATION_NOT_READY` | `select` on a non-READY generation |
| 429 | `SESSION_TURN_LIMIT` / `GENERATION_LIMIT` / `SESSION_DAILY_LIMIT` / `AI_BUSY` | caps |
| 403 | `TURNSTILE_FAILED` | captcha rejected |
| 502 | `LLM_UNAVAILABLE` / `LLM_TIMEOUT` | synchronous chat turn could not get a reply (the user message is kept in state; a retry that re-sends the identical text as the last stored user message does not append it twice) |

Generation-level failures are not HTTP errors: `GET /generations/{id}` returns
`status: FAILED` with `error: {code, retryable}`.

### Testing

- Unit (no Spring): `PlanValidator` (one test per violation code, plus arrival/
  departure windows), `PlanPricer` (floor, rounding, tier order), `BriefMerger`
  (null keeps old, readiness predicate), `FallbackPlanComposer` (respects every rule
  on a fixture catalog, produces three distinct tiers), `CatalogSnapshotter`
  (80-cap ordering, localization), prompt rendering (golden files), `LlmOutputParser`
  against recorded Qwen JSON fixtures (valid, extra fields, malformed).
- Graph tests with `MemorySaver` and a `FakeLlmGateway` (scripted answers): happy
  path chat → generate → select; repair path; fallback path; regenerate re-entry;
  select twice; brief-incomplete refusal.
- Controller integration (`@SpringBootTest`, fake gateway bean): the full HTTP
  cycle, every error code above, kill switch, caps, Turnstile flag.
- Persistence: one Testcontainers Postgres test (`@Tag("docker")`, skipped when
  Docker is absent) proving a thread parked at `awaitUser` resumes after the saver is
  rebuilt on the same database, and that `V7` DDL matches what the saver expects.
- Live smoke (`@EnabledIfEnvironmentVariable(QWEN_API_KEY)`): one real chat turn and
  one composition against the dev catalog, asserting only structural validity.

### Rollout

1. Deploy with `AI_ENABLED=false`; V7 applies; nothing else changes.
2. Set `QWEN_API_KEY`, `QWEN_BASE_URL`, flip `AI_ENABLED=true` on the backend only;
   verify with the live smoke test and a manual curl session.
3. Frontend ships against the contract; `AI_TURNSTILE_REQUIRED=true` in prod from
   day one.
4. Watch `ai_generations` (failure rate, `degraded` share, latency, tokens) for a
   week before promoting the entry point on the destination page.

### Risks

| Risk | Mitigation |
|---|---|
| langgraph4j is maintained mostly by one person | Only `core` + `postgres-saver` are used; both are small; a fork is realistic. The `langgraph4j-spring-ai` module is **not** used (built against Spring AI 2.0.0-M4). |
| Saver DDL drifts with library upgrades | DDL is pinned in Flyway; upgrades add a new migration; the Testcontainers test catches mismatches. |
| Model returns invalid JSON or hallucinated ids | strict parse → violation → one repair → deterministic fallback; the user always gets three packages. |
| Cost spike from abuse | Turnstile, per-IP daily cap, per-session caps, bounded executor, kill switch. |
| Render restarts mid-generation | Stale sweeper marks the row FAILED (retryable); the thread checkpoint is intact, so regeneration resumes from `awaitGeneration`. |
| Spike finds the saver incompatible with Java 25 / Boot 4 | Fallback: `MemorySaver` + rehydrating the graph thread from our tables on each request (messages would then be stored in a new `ai_messages` table). Node and API design unchanged. |
