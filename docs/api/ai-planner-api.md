# AI Planner API — frontend contract

Status: **v1.1, reconciled with the implementation on 2026-09-16**. Backend and
frontend are built in parallel against this document. Changes go through a PR that
edits this file first.

Design rationale: [`docs/superpowers/specs/2026-09-15-ai-stag-planner-design.md`](../superpowers/specs/2026-09-15-ai-stag-planner-design.md).

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

`content`: 1–1000 chars. Synchronous, typically 2–8 s.

```json
{
  "message": { "role": "ASSISTANT", "content": "Nice — 8 lads, 3 days. What's the vibe: karting and beer, something wild, or a bit of everything?", "at": "2026-09-15T10:01:03Z" },
  "brief": { "...Brief..." },
  "missingFields": ["preferences"],
  "readyToGenerate": false,
  "generation": null
}
```

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
  "packages": [ "...Package × 3, ordered BASIC, MEDIUM, PREMIUM..." ] }
```

`degraded: true` means the deterministic fallback composed the packages (the model
failed twice); they are valid but blander — a small "auto-composed" hint is enough.

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
  "limits": { "messagesLeft": 27, "generationsLeft": 5 }
}
```

`messages[0]` is always the assistant greeting (EN/DE), seeded when the session is
created — it costs no model call and is not something the agent "said".

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
