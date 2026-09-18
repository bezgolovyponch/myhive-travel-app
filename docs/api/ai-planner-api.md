# AI Planner API — frontend contract

Status: **v1.2, reconciled with the implementation on 2026-09-18**. Backend and
frontend are built in parallel against this document. Changes go through a PR that
edits this file first.

Design rationale: [`docs/superpowers/specs/2026-09-15-ai-stag-planner-design.md`](../superpowers/specs/2026-09-15-ai-stag-planner-design.md).

**Changes since v1.1** (additive only — nothing v1.1 documented was renamed or removed):
- Package edits: once packages exist, ask for a change in plain chat ("swap X for
  Y", "drop Z") on the same `POST /ai/sessions/{token}/messages` endpoint — no new
  endpoint. The result comes back **inline** in that response, already applied;
  there is nothing to poll for. Three ops: `ADD` / `REMOVE` / `REPLACE`, each
  optionally scoped to one package. See "Editing packages" under the messages
  endpoint below.
- `TurnResponseDTO.edit` (`EditDTO`): what the turn's edit batch did —
  `generationId`, `applied[]`, `rejected[]` (each rejection carries a `reason`),
  `tierRulesRelaxed`, `textsRefreshed`.
- `TurnResponseDTO.messages`: **every** assistant message the turn produced, in
  order. A turn that rejected something writes two (the agent's reply, then the
  template line explaining the rejection); `message` is the last of them, which
  is what it has always been. Render `messages`, keep reading `message` only if
  you were already.
- `GenerationDTO` gained `kind` (`GENERATED` | `EDITED`), `parentId` and
  `editReport` — every generation carries these now, not only edited ones.
- `SessionState.limits` gained `editsLeft`: **20 edit turns** per chat (a turn
  that applied at least one op), counted separately from the 5-generation cap
  and never surfaced as its own HTTP error.
- Ten edit rejection reasons, documented in full below.
- At most **10 edit ops** are taken from one message; anything past that is
  dropped silently (the reply still stands). An activity name longer than **120
  characters** is truncated at parse time.
- Corrected against the shipped behaviour (these were wrong or missing in the
  first v1.2 draft, which the frontend has not shipped against yet): the budget
  is edit *turns*, not applied ops; `edit` is `null` when the same message also
  changed the brief; `INTERNAL` comes in a per-op and a whole-batch shape;
  `EDIT_LIMIT`/`NO_PACKAGES_YET` still cost one chat-model call; `generation` can
  be `null` on a turn with a non-empty `applied[]`; `tierRulesRelaxed` is per
  batch, not a plan property; `parentId` can be `null` on an `EDITED` row;
  `WOULD_BREAK_SCHEDULE` fires only on violations the op introduced (and there is
  no "category rule"); `NOT_IN_PACKAGE`/`NO_FREE_SLOT` cover more cases than
  listed; selecting an older `READY` generation is the undo.

**Changes since v1** (reconciling the contract with what actually shipped):
- Bean-validation failures return `error: "Validation Failed"` (the app-wide
  convention), not `VALIDATION_ERROR`.
- Unknown `destinationSlug`, a malformed (non-UUID) `{token}`/`{id}`, and an
  invalid `packageKey` are all `400 "Bad Request"`, documented explicitly.
- `select`'s `GENERATION_IN_PROGRESS` also fires while any other generation of the
  session is in flight, not just the one being selected.
- Noted: `SessionState.messages[0]` is the seeded greeting, and how the client IP
  for the daily cap is resolved.

## Basics

- Base path: `/ai` (prod: `https://www.trivlu.com/api/ai/...` through the Next
  rewrite; dev: `http://localhost:8080/ai/...`).
- No auth. The **session token** is the credential; store it in
  `localStorage['myhive-ai-session']` and never put it in analytics payloads.
- Locale: pass `locale` on session creation (`en` | `de`); the agent replies and
  names activities in that language.
- All bodies are JSON. Money is EUR, numbers in euros with two decimals (not cents).
- The client IP used for the daily session cap is resolved the same way as the
  global rate limiter (`CF-Connecting-IP`, else the last `X-Forwarded-For` hop,
  else the socket address) — no client action needed.
- Errors use the shared shape:

```json
{ "timestamp": "2026-09-15T10:00:00", "status": 429, "error": "SESSION_TURN_LIMIT",
  "message": "This chat reached its 30-message limit", "path": "/ai/sessions/…/messages" }
```

| HTTP | `error` | Meaning / what the UI should do |
|---|---|---|
| 503 | `AI_DISABLED` | Feature is off. Hide the entry point. |
| 404 | `SESSION_NOT_FOUND`, `GENERATION_NOT_FOUND` | Token/id unknown or expired (30 days idle). Drop the stored token, offer a new chat. |
| 400 | `"Validation Failed"` | Bean-validation failure (e.g. message/`initialMessage` too long) — the app-wide convention (not an AI-specific code); `fieldErrors` map present. |
| 400 | `"Bad Request"` | Three cases, same shape: an unknown `destinationSlug` on `POST /ai/sessions` (`message` names the slug); a malformed non-UUID `{token}`/`{id}` path variable — treat exactly like `SESSION_NOT_FOUND`/`GENERATION_NOT_FOUND` and drop the stored token; or a `packageKey` outside `BASIC`\|`MEDIUM`\|`PREMIUM` on `select`. |
| 403 | `TURNSTILE_FAILED` | Captcha rejected. Re-render Turnstile and retry. |
| 409 | `BRIEF_INCOMPLETE` | Manual generate before the agent knows days/group/preferences. Show `missingFields`. |
| 409 | `GENERATION_IN_PROGRESS` | A generation is already QUEUED or RUNNING. On `select` this also fires while *any* generation of the session is in flight, not only the one being picked — an older package set cannot be selected mid-regeneration. Poll instead. |
| 409 | `GENERATION_NOT_READY` | `select` called on a generation that is not `READY`. |
| 409 | `SESSION_BUSY` | Another request for this chat is still in flight (double-click). Retry once the first call returns. |
| 429 | `SESSION_TURN_LIMIT`, `GENERATION_LIMIT` | 30 messages / 5 generations per session. Offer "start a new chat". |
| 429 | `SESSION_DAILY_LIMIT` | 20 new sessions per IP per day. |
| 429 | `AI_BUSY` | Queue full. Retry generation in ~10 s. |
| 502 | `LLM_UNAVAILABLE`, `LLM_TIMEOUT` | The chat reply failed. The user's message **was saved**; show "try again" which re-sends the same text (the backend de-dupes an identical consecutive user message). Never returned by `POST /ai/sessions` — a failed first turn comes back as `201` with `firstTurnError`. |

## Flow

```
POST /ai/sessions ─→ chat with POST /ai/sessions/{token}/messages until
  response.generation appears ─→ poll GET /ai/generations/{id} every 2 s until READY|FAILED
  ─→ render 3 packages ─→ POST /ai/generations/{id}/select ─→ dispatch tripItems into the Trip Builder
Returning later: GET /ai/sessions/{token} rebuilds the whole screen.
```

Restore from `latestReadyGeneration` when `latestGeneration` is `FAILED` — a failed
or `AI_BUSY` regeneration is the newest row but not the one with the packages on it.

After packages exist, ask for concrete changes in chat ("swap X for Y", "drop Z");
the response carries the updated packages immediately, no poll. A change to the
**brief** itself (days, group size, vibe, budget, …) still triggers a full
regeneration instead, and wins over any edit ops the same message also asked for —
the chat node never both regenerates and edits on one turn. On that turn `edit`
is `null`: the ops were never attempted, so they appear in no `rejected[]`
either. A second assistant message (in `messages[]`) says the packages are being
rebuilt with the new details and the changes should be asked for again
afterwards — show it, or the group is left thinking a swap they asked for
landed. A regeneration always starts from the brief, so it **discards** every
edit made so far; the edited generations stay in the history, but the new
packages are built from scratch.

## Endpoints

### `POST /ai/sessions` — start a chat

```json
{ "destinationSlug": "prague", "locale": "en",
  "turnstileToken": "…optional, required in prod…",
  "initialMessage": "optional first user message" }
```

`201` → `SessionState` (below). When `initialMessage` is given the response already
contains the agent's first reply; otherwise `messages` holds only the greeting.

If that inline first turn cannot reach the model, the call is **still `201`** — not
`502`. The session, its greeting and the user's message are all stored, and
`firstTurnError` carries `{"code": "LLM_UNAVAILABLE" | "LLM_TIMEOUT"}` while
`messages` ends on the `USER` entry with no assistant reply. Show "try again" and
re-send the same text to `POST /ai/sessions/{token}/messages`; the backend de-dupes
an identical consecutive user message, so nothing is stored twice. Answering `502`
here would throw away the very token the retry needs, along with the daily-cap slot
the call already spent.

### `GET /ai/sessions/{token}` — full state (restore the screen)

`200` → `SessionState`.

### `POST /ai/sessions/{token}/messages` — send a user message

```json
{ "content": "8 of us, Friday evening to Sunday noon, we like karting and beer" }
```

`content`: 1–1000 chars. Synchronous, typically 2–8 s for an ordinary turn. A turn
that lands an edit calls the model **twice** (extraction, then a best-effort text
refresh), each capped at the 20 s chat-model timeout — worst case ~40 s. Give the
request a client timeout of **at least 45 s** on this endpoint and show a pending
indicator for edit turns, not just the ordinary "thinking" state.

```json
{
  "message": { "role": "ASSISTANT", "content": "Nice — 8 lads, 3 days. What's the vibe: karting and beer, something wild, or a bit of everything?", "at": "2026-09-15T10:01:03Z" },
  "brief": { "...Brief..." },
  "missingFields": ["preferences"],
  "readyToGenerate": false,
  "generation": null,
  "edit": null,
  "messages": [ { "role": "ASSISTANT", "content": "Nice — 8 lads, 3 days. What's the vibe: karting and beer, something wild, or a bit of everything?", "at": "2026-09-15T10:01:03Z" } ]
}
```

`edit` is `null` on every turn that carried no edit ops — and also on the turn
that both changed the brief and asked for one, where the regeneration wins (see
"Flow" above). See "Editing packages" below for its shape once a turn does carry
ops.

`messages` is every assistant message **this turn** produced, in order, and is
never empty on a `200`. An ordinary turn holds one; a turn that rejected an edit,
or that asked for one before any packages exist, holds two. `message` is the
**last** entry — unchanged from v1.1, which is exactly why it is not enough on
its own: on a partially rejected edit it is the template line, and the agent's
own answer is the entry before it.

Generation starts **automatically** the moment the brief is complete (days, group
size and preferences known) — there is no confirmation step. On that turn
`readyToGenerate` is `true`, `generation` is set to `{ "id": "…", "status": "QUEUED" }`
and the message says something like "Building your three options…". Start polling.
If the very first message already contains everything, this happens right after the
first reply. After packages exist, a later message regenerates automatically **only
if it changed the brief** (e.g. "actually 6 of us"); small talk does not. A message
that arrives after a generation ended `FAILED` (including an `AI_BUSY` rejection) is
still answered in chat and, if the brief is complete, starts a fresh generation the
same way.

#### Editing packages

Once packages exist, the same endpoint doubles as the edit channel — there is no
`PATCH`/`PUT` and nothing to poll. The chat model reads the group's message against
the current packages and the catalog, extracts zero or more edit ops, and
`PackageEditor` applies them deterministically (no model involved in the actual
edit): the touched package is re-validated and the whole plan re-priced, so prices,
line totals and totals are never hand-edited. An edit turn is still one turn: it
counts against the chat's 30-message cap (`SESSION_TURN_LIMIT`) exactly like a
brief question or a regeneration turn does — there is no separate message
allowance for edits.

Three ops, each naming a catalog activity by its display name and optionally a
`packageKey` (`BASIC`\|`MEDIUM`\|`PREMIUM`) to scope it to one package:
- **ADD** — put an activity into a package. Left untargeted, it is attempted
  against **every** package in the plan (one applied/rejected entry per tier), not
  just one.
- **REMOVE** — drop an activity from a package. Left untargeted, it is attempted
  against every package that currently contains it.
- **REPLACE** — swap one activity for another in place (same cell where possible).
  Same untargeted behaviour as `REMOVE`: every package holding the original.

An `ADD` may additionally be scoped to a `dayNumber` and/or a `slot` ("put the
karting on Saturday morning"). A scoped op is not a hint: only that day, and only
that slot, is tried — if the cell is taken or the slot is outside that day's
arrival/departure window, the op comes back `NO_FREE_SLOT` rather than landing
somewhere else. Unscoped, the first day and slot that fit are used, with the
evening slots offered first to nightlife/dining activities.

Because one conversational request ("drop the club night") can fan out to more than
one package, `edit.applied[]`/`edit.rejected[]` may hold more entries than the
group asked for in words — group them by `packageKey` in the UI.

At most **10 ops** are taken from one message. A message that somehow produces
more has the tail dropped without a word — the reply and the first ten still
stand — so a UI should not promise a one-to-one mapping between what was asked
and what comes back. An `activity`/`replacement` name longer than **120
characters** is truncated at parse time, which only shows up in a rejection's
`activity`/`detail`.

`edit` (`EditDTO`) is non-null on every turn that carried at least one edit op,
applied or not:

```json
{
  "generationId": null,
  "applied": [
    { "op": "ADD" | "REMOVE" | "REPLACE", "activity": "Beer Bike",
      "replacement": null, "packageKey": "MEDIUM", "dayNumber": 2, "slot": "EVENING" }
  ],
  "rejected": [
    { "op": "ADD" | "REMOVE" | "REPLACE", "activity": "Karting",
      "packageKey": "PREMIUM", "reason": "NO_FREE_SLOT",
      "detail": "no free slot for Karting in PREMIUM" }
  ],
  "tierRulesRelaxed": true,
  "textsRefreshed": true
}
```

- `generationId`: the new `EDITED` generation's id — **null unless at least one op
  in the batch was applied**. A fully rejected batch creates no row.
- `applied[].activity`/`.replacement`: always the **catalog name**, never the
  model's spelling. `replacement` is non-null only for `REPLACE`.
- `applied[].dayNumber`/`.slot`: the cell the item landed in (`REMOVE`: the cell it
  was removed from). `slot` is `MORNING`\|`AFTERNOON`\|`EVENING`\|`NIGHT`.
- `rejected[].activity`: the catalog name once resolved, otherwise the model's raw
  spelling — see the rejection-reasons table for the one case where this is the
  *replacement's* spelling on a `REPLACE`.
- `rejected[].packageKey`: null when the rejection happened during activity-name
  resolution (`UNKNOWN_ACTIVITY`/`AMBIGUOUS_ACTIVITY`) or when an unscoped edit
  matched no package at all. For `NO_PACKAGES_YET`/`EDIT_LIMIT` (and a top-level
  `INTERNAL`) it instead echoes back whatever `packageKey` the request itself
  carried — usually null, but non-null if the message scoped the op to one
  package before there was anything to check it against.
- `rejected[].detail`: a short technical hint, **not customer-facing copy** — the
  assistant chat message already carries the sentence the group reads. May be null.
- `tierRulesRelaxed`: `true` iff **this batch** applied something. It is a
  property of the batch, not of the plan: the next edit turn, and the `editReport`
  stored on the next `EDITED` row, start again from `false`. It says the tier
  heuristics (`TIER_ORDER`/`TIER_NOT_DISTINCT` — strictly rising per-person price,
  one activity per tier the others lack) no longer hold: an organizer edited a
  package by hand, so the tiers may legitimately cross or overlap from here on.
  Because the flag does not persist, treat **any generation with `kind: "EDITED"`
  anywhere in its lineage** (follow `parentId`) as tier-relaxed, rather than
  reading one report's flag.
- `textsRefreshed`: whether the copy (`description`, day `summary`, item `why`) of
  what was touched actually came back rewritten. `false` means the touched
  package's texts may now read slightly stale against the new activity list — the
  refresh is best-effort and a model outage or a garbled answer leaves the previous
  copy in place rather than failing the edit.

All nullable fields above (`generationId`, `applied[].replacement`,
`applied[].slot`, `rejected[].packageKey`, `rejected[].detail`) are emitted as
explicit JSON `null`, never omitted — this API is not serialised with
Jackson's `NON_NULL`.

`generation` on an edit turn: null on a fully rejected batch (nothing changed);
otherwise the **new `EDITED` generation**, already `status: "READY"` — there is
nothing to poll for. It carries no `selectedPackageKey` (edits never select), so
the organizer picks again via `POST /ai/generations/{id}/select` with the new id.
Refresh `limits.editsLeft` by re-`GET`ting the session — the turn response itself
never carries it (see "Limits" below).

There is one rare shape to defend against: `generation: null` together with a
**non-empty `applied[]`**. The row was stored and the edit really landed; only
reading it back inside the same request failed. The budget is spent, the report
is accurate, and the packages are there — re-`GET /ai/sessions/{token}` and
render `latestReadyGeneration`. Do not treat it as "nothing happened".

An applied edit also puts the chat back to `status: "READY"` even if it was
`FAILED`: a failed regeneration does not take away packages that are still
editable, and an edit on them must not stay filed under a failure.

**Example — an edit that landed clean:**

```json
{
  "message": { "role": "ASSISTANT", "content": "Swapped Beer Bike in for the club night on day 2.", "at": "2026-09-18T11:04:05.123Z" },
  "brief": { "...Brief..." },
  "missingFields": [],
  "readyToGenerate": true,
  "generation": {
    "id": "6f1f0f7a-6c1b-4a2e-9a43-7b0d6b2a11ce",
    "sessionToken": "b1d3a7e2-2c8e-4a6d-9f21-0e5c9d3f1a44",
    "status": "READY", "degraded": false, "selectedPackageKey": null,
    "brief": { "...Brief snapshot..." },
    "packages": [ "...Package × 3, one re-priced and re-texted..." ],
    "error": null,
    "createdAt": "2026-09-18T11:04:05.100", "finishedAt": "2026-09-18T11:04:05.100",
    "kind": "EDITED",
    "parentId": "3a7c1e90-59d2-4b17-8d5e-2f4a6c8b0d13",
    "editReport": {
      "generationId": "6f1f0f7a-6c1b-4a2e-9a43-7b0d6b2a11ce",
      "applied": [ { "op": "REPLACE", "activity": "VIP Club Night", "replacement": "Beer Bike", "packageKey": "MEDIUM", "dayNumber": 2, "slot": "EVENING" } ],
      "rejected": [], "tierRulesRelaxed": true, "textsRefreshed": true
    }
  },
  "edit": {
    "generationId": "6f1f0f7a-6c1b-4a2e-9a43-7b0d6b2a11ce",
    "applied": [ { "op": "REPLACE", "activity": "VIP Club Night", "replacement": "Beer Bike", "packageKey": "MEDIUM", "dayNumber": 2, "slot": "EVENING" } ],
    "rejected": [], "tierRulesRelaxed": true, "textsRefreshed": true
  },
  "messages": [ { "role": "ASSISTANT", "content": "Swapped Beer Bike in for the club night on day 2.", "at": "2026-09-18T11:04:05.123Z" } ]
}
```

`edit` and `generation.editReport` describe the same batch — they are equal for
the row this turn just created.

**Example — a batch where one op landed and one did not:**

```json
"generation": { "...the EDITED row, one op landed so it exists...": null },
"edit": {
  "generationId": "6f1f0f7a-6c1b-4a2e-9a43-7b0d6b2a11ce",
  "applied": [ { "op": "REMOVE", "activity": "Beer Bike", "replacement": null, "packageKey": "MEDIUM", "dayNumber": 2, "slot": "EVENING" } ],
  "rejected": [ { "op": "ADD", "activity": "Karting", "packageKey": "PREMIUM", "reason": "NO_FREE_SLOT", "detail": "no free slot for Karting in PREMIUM" } ],
  "tierRulesRelaxed": true, "textsRefreshed": true
},
"messages": [
  { "role": "ASSISTANT", "content": "Dropped the Beer Bike from the middle option.", "at": "2026-09-18T11:06:41.201Z" },
  { "role": "ASSISTANT", "content": "I could not fit Karting in: no free slot left.", "at": "2026-09-18T11:06:41.204Z" }
]
```

The rejection line is the **second** entry of `messages[]`, and it is the one
`message` points at — render both or the agent's own answer is lost. It carries
one sentence per distinct rejection reason (never one line per rejected op), so
the group reads e.g. one line about a full day rather than four repeats of the
same explanation.

**Example — a fully rejected batch (nothing landed):**

```json
"generation": null,
"edit": {
  "generationId": null,
  "applied": [],
  "rejected": [ { "op": "REPLACE", "activity": "Hot Air Balloon", "packageKey": null, "reason": "UNKNOWN_ACTIVITY", "detail": "Hot Air Balloon" } ],
  "tierRulesRelaxed": false, "textsRefreshed": false
}
```

**Example — asked for an edit before any generation exists:**

```json
"generation": null,
"edit": {
  "generationId": null,
  "applied": [],
  "rejected": [ { "op": "ADD", "activity": "Karting", "packageKey": null, "reason": "NO_PACKAGES_YET", "detail": null } ],
  "tierRulesRelaxed": false, "textsRefreshed": false
}
```

A second assistant message says the packages have to be built first — it is the
second entry of `messages[]`, and the one `message` points at.

**Example — over the edit budget (the 21st edit turn):**

Still `200`, still an ordinary turn body — the cap is never its own HTTP error:

```json
"generation": null,
"edit": {
  "generationId": null,
  "applied": [],
  "rejected": [ { "op": "REPLACE", "activity": "VIP Club Night", "packageKey": null, "reason": "EDIT_LIMIT", "detail": null } ],
  "tierRulesRelaxed": false, "textsRefreshed": false
}
```

This branch still costs **exactly one chat-model call**, the same as any other
turn: the chat turn runs first and is what extracts the ops in the first place,
and only then does Java see that the budget is gone. What is skipped is the
*second* call, the text refresh — there is nothing to re-word. `NO_PACKAGES_YET`
works the same way. `limits.editsLeft` is `0` from here on (re-`GET` the session
to see it).

**Rejection reasons** (`edit.rejected[].reason`, ten values):

| Reason | Meaning | Suggested UI treatment |
|---|---|---|
| `UNKNOWN_ACTIVITY` | The name could not be matched to anything in the destination's catalog. | Show the chat sentence; no retry button — ask the group to rephrase or pick from the list. |
| `AMBIGUOUS_ACTIVITY` | The name matches more than one catalog entry. | `detail` holds the candidate names, comma-separated — offer them as quick replies. |
| `NOT_IN_PACKAGE` | `REMOVE`/`REPLACE` named an activity that is not actually in the targeted package (or in any package, when untargeted) — **and** an op of any kind, `ADD` included, scoped to a `packageKey` the plan does not have. | Informational — nothing to retry, the plan already matches what was asked for. |
| `ALREADY_IN_PACKAGE` | `ADD`/`REPLACE` named an activity already present in the target. On a `REPLACE` this is about the **replacement**: `activity` is still the original, and `detail` names the replacement that is already there. | Informational, same treatment as above. |
| `WOULD_EMPTY_PACKAGE` | A `REMOVE` would leave that package with zero activities. | Suggest a `REPLACE` instead of a bare drop. |
| `NO_FREE_SLOT` | Nowhere left to put it: every day in that package is at its tier's item/minute cap, **or** the op named a `dayNumber`/`slot` whose cell is taken or lies outside that day's arrival/departure window. | Suggest dropping something first, naming another day/slot, or trying a roomier tier. |
| `WOULD_BREAK_SCHEDULE` | The activity placed fine, but re-validating the touched package showed a scheduling rule the op **introduced** (an emptied middle day, a duplicate, a day over its caps). Rules the package already broke — a degraded or fallback plan often has some — are ignored, so this never fires for a problem the edit did not cause. | Show only the chat sentence to the group; `detail` (validator code + message) is for support/logs. |
| `NO_PACKAGES_YET` | An edit was requested while the chat has no packages in its state — which is normally "before the first generation finished", not "no generation row exists". | Prompt to finish the brief (or hit "Generate now") first. |
| `EDIT_LIMIT` | This chat's 20 edit-turn budget is spent. | Same treatment as `SESSION_TURN_LIMIT`/`GENERATION_LIMIT` — offer "start a new chat". |
| `INTERNAL` | Something on our side, never the group's fault. Two shapes: **per op**, when the editor itself failed on that one edit and the rest of the batch went through; and **the whole batch** (`applied[]` empty, `generationId` null, one `INTERNAL` entry per requested op), when storing the result failed, when the parent generation is missing or not `READY`, or when the plan references activities the catalog snapshot no longer has. | Generic "try again". On a whole-batch `INTERNAL` the packages on screen are unchanged and still correct. |

One spelling nuance: on a `REPLACE`, if it is the **replacement's** name that
fails to resolve (`UNKNOWN_ACTIVITY`/`AMBIGUOUS_ACTIVITY`), `rejected[].activity`
carries the *replacement's* spelling, not the activity being replaced — the
original is resolved first and only reported separately if it is the one that
fails.

**Applying against a stale snapshot:** edits are applied against the catalog
snapshot taken when the packages were generated, not a fresh catalog read. An
activity that was deleted or re-priced in the admin since generation can still
appear in an edit's `applied[]`/be offered as a target — final validation happens
at booking, same as any other planner package.

### `POST /ai/sessions/{token}/generations` — (re)generate explicitly

Empty body. Use for a "Generate now" / "Try other options" button.
`202` → `{ "id": "…", "status": "QUEUED" }`. Errors: `BRIEF_INCOMPLETE`,
`GENERATION_IN_PROGRESS`, `GENERATION_LIMIT`, `AI_BUSY`.

### `GET /ai/generations/{id}` — poll

```json
{ "id": "…", "sessionToken": "…", "status": "RUNNING", "createdAt": "…", "finishedAt": null }
```

`status`: `QUEUED` | `RUNNING` | `READY` | `FAILED`. Poll every 2 s; give up after
90 s and show the retry button. On `READY`:

```json
{ "id": "…", "status": "READY", "degraded": false, "selectedPackageKey": null,
  "brief": { "...Brief snapshot used..." },
  "packages": [ "...Package × 3, ordered BASIC, MEDIUM, PREMIUM..." ],
  "kind": "GENERATED",                        // GENERATED | EDITED
  "parentId": null,                           // non-null only on an EDITED row
  "editReport": null }                        // non-null only on an EDITED row (see below)
```

`degraded: true` means the deterministic fallback composed the packages (the model
failed twice); they are valid but blander — a small "auto-composed" hint is enough.

Every generation — not only ones produced by an edit turn — now carries `kind`,
`parentId` and `editReport`. A `GENERATED` row (the planner model's own output, or
the deterministic fallback) always has `kind: "GENERATED"`, `parentId: null` and
`editReport: null`. An `EDITED` row (produced by `PackageEditor` applying a chat
edit to its parent, without a regeneration) has `kind: "EDITED"`, `parentId` set to
the generation it was derived from, and `editReport` normally set to the same
`EditDTO` shape as `TurnResponseDTO.edit` above — **except** `editReport` can still
come back `null` on an `EDITED` row: the stored report failed to parse (for
example, a reason value written by a newer backend and read by an older one after
a rollback). Treat `editReport` as optional on every generation, `EDITED` included
— never assume it is present just because `kind` is `EDITED`.

`parentId` is likewise not guaranteed on an `EDITED` row: the link is cleared if
the parent is ever deleted (chats are purged after 30 idle days, and a purge can
catch a parent), so walking a lineage has to stop on a `null` rather than assume
another row is there.

An `EDITED` row is derived from its parent in more than the itinerary: it
**inherits the parent's `brief` snapshot and its `degraded` flag**. A package set
built by the deterministic fallback stays `degraded: true` after an edit — that
is correct, the edit did not make the packages any less auto-composed — and the
`brief` on an `EDITED` row describes the generation the plan started from, not a
brief the group changed since (changing the brief regenerates instead).

Editing always continues from the generation whose packages the chat is actually
showing. That is normally the newest `EDITED` row, so a second edit stacks on the
first. It is **not** always the newest row overall: a generation that failed does
not become the base of an edit, and `POST /ai/generations/{id}/select` on an older
`READY` generation is the documented **undo** — after it, that row's packages are
what the chat holds, what `select` puts in the cart, and what the next edit is
applied to (the next `EDITED` row's `parentId` is that row).

On `FAILED`:

```json
{ "id": "…", "status": "FAILED", "error": { "code": "LLM_TIMEOUT", "retryable": true } }
```

`code`: `LLM_TIMEOUT` | `LLM_UNAVAILABLE` | `LLM_INVALID_OUTPUT` | `AI_BUSY` |
`STALE` | `INTERNAL`. Everything but `INTERNAL` comes back `retryable: true`.
`AI_BUSY` appears here as well as in the `429` on the request that was rejected: the
row is persisted so a chat restored from its token still explains itself. It does
not count against the session's five generations.

### `POST /ai/generations/{id}/select` — pick a package

```json
{ "packageKey": "MEDIUM" }
```

`200`:

```json
{ "packageKey": "MEDIUM",
  "groupSize": 8,
  "tripItems": [ "...VotePoolActivityDTO × N (same shape the quiz pool returns)..." ] }
```

Map `activityId → id` exactly as `CuratePage`/`TripBuilder` do for the quiz pool, then
replace the cart (the same way `SET_TRIP_ITEMS_FROM_VOTE` does) and set travelers to
`groupSize`. Selecting again with another key is allowed and overwrites the choice.

Selecting an **older** `READY` generation is how an edit is undone: it does not
only fill the cart, it moves the chat back onto that generation's packages, so a
following edit is applied to them and hangs off that row. There is no separate
undo endpoint; offering "go back to these" on an earlier generation is the whole
feature.

## Types

### `SessionState`

```json
{
  "token": "3f1c…", "destinationSlug": "prague", "locale": "en",
  "status": "COLLECTING",                     // COLLECTING | GENERATING | READY | FAILED
  "brief": { "...Brief..." },
  "missingFields": ["days"],
  "readyToGenerate": false,
  "messages": [ { "role": "ASSISTANT", "content": "…", "at": "…" }, { "role": "USER", "content": "…", "at": "…" } ],
  "latestGeneration": null,                   // or the GET /ai/generations/{id} body
  "latestReadyGeneration": null,              // newest READY generation, same body; null if none
  "firstTurnError": null,                     // or { "code": "LLM_UNAVAILABLE" | "LLM_TIMEOUT" }
  "limits": { "messagesLeft": 27, "generationsLeft": 5, "editsLeft": 20 }
}
```

`messages[0]` is always the assistant greeting (EN/DE), seeded when the session is
created — it costs no model call and is not something the agent "said".

`limits.editsLeft` (20 − edit turns spent so far, never negative) lives **only**
here and on the `201` from `POST /ai/sessions` — it is not on
`TurnResponseDTO`. An edit turn does not tell the client its own remaining
budget; re-`GET /ai/sessions/{token}` after an edit to refresh the number shown
in the UI. Unlike `messagesLeft`/`generationsLeft`, running out of `editsLeft`
never produces its own `429`: every edit requested past the cap simply comes
back rejected with `EDIT_LIMIT` inside an otherwise ordinary `200` turn (see
"Editing packages" above).

The unit is a **turn**, not an op. One turn that applied at least one op costs
exactly one, however many entries it put in `applied[]` — an untargeted "drop
the club night" that lands in all three packages is three applied entries and
one unit of budget. A turn where nothing landed costs nothing, including one
rejected only for `EDIT_LIMIT`, so "20 changes" is the wrong thing to show the
group; "20 rounds of changes" is closer.

`latestGeneration` is the **newest** generation whatever its status;
`latestReadyGeneration` is the newest one that actually produced packages. They are
the same row in the normal case and differ after a failed or `AI_BUSY` regeneration
— render the packages from `latestReadyGeneration` and the error from
`latestGeneration`. A failed regeneration also leaves `status` at `READY` rather
than `FAILED` while packages survive, so `status: "FAILED"` really does mean "this
chat has nothing to show".

`firstTurnError` is set only by `POST /ai/sessions` (see above) and is always null
on `GET /ai/sessions/{token}`.

### `Brief`

```json
{ "days": 3, "groupSize": 8,
  "categorySlugs": ["driving", "nightlife", "dining"],
  "vibe": "loud but not stupid", "dislikes": "no strip clubs", "budget": "MID",
  "arrival": "EVENING", "departure": "MORNING", "notes": null }
```

`budget`: `LOW` | `MID` | `HIGH` | null. `arrival`/`departure`: `MORNING` |
`AFTERNOON` | `EVENING`. Fields are null until the agent learns them.

### `Package`

```json
{
  "key": "MEDIUM",                             // BASIC | MEDIUM | PREMIUM (stable, use for i18n badges/analytics)
  "title": "The Full Prague",                  // model-written, ≤ 60 chars
  "tagline": "Karting by day, beer by night",  // ≤ 120
  "description": "…",                          // ≤ 600
  "pricePerPerson": 245.50, "totalPrice": 1964.00, "currency": "EUR",
  "totalDurationMinutes": 780,
  "activityIds": ["…", "…"],
  "days": [
    { "dayNumber": 1, "title": "Landing night", "summary": "…",
      "items": [
        { "slot": "EVENING",                   // MORNING | AFTERNOON | EVENING | NIGHT
          "startHint": "19:30",                // free-form, may be null
          "activityId": "…", "slug": "beer-bike", "name": "Beer Bike",
          "imageUrl": "https://…", "durationMinutes": 120,
          "price": 35.00, "minPrice": 280.00, "lineTotal": 280.00,
          "groupMinApplied": true,
          "why": "Gets everyone loose without a hangover before karting." }
      ] }
  ]
}
```

`lineTotal` already applies the group minimum (`max(price × groupSize, minPrice)`),
identical to the Trip Builder formula; show `groupMinApplied` the way the Trip
Builder shows "(group min)".

## Non-goals of this contract

No streaming, no message editing/deletion, no lead capture in chat, no admin
endpoints. If the UI needs suggested quick replies, that will arrive as an
additive `suggestedReplies: string[]` on the message response in a later version.
