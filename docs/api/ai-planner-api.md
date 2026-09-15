# AI Planner API — frontend contract

Status: **draft v1, agreed 2026-09-15**. Backend and frontend are built in parallel
against this document. Changes go through a PR that edits this file first.

Design rationale: [`docs/superpowers/specs/2026-09-15-ai-stag-planner-design.md`](../superpowers/specs/2026-09-15-ai-stag-planner-design.md).

## Basics

- Base path: `/ai` (prod: `https://www.trivlu.com/api/ai/...` through the Next
  rewrite; dev: `http://localhost:8080/ai/...`).
- No auth. The **session token** is the credential; store it in
  `localStorage['myhive-ai-session']` and never put it in analytics payloads.
- Locale: pass `locale` on session creation (`en` | `de`); the agent replies and
  names activities in that language.
- All bodies are JSON. Money is EUR, numbers in euros with two decimals (not cents).
- Errors use the shared shape:

```json
{ "timestamp": "2026-09-15T10:00:00", "status": 429, "error": "SESSION_TURN_LIMIT",
  "message": "This chat reached its 30-message limit", "path": "/ai/sessions/…/messages" }
```

| HTTP | `error` | Meaning / what the UI should do |
|---|---|---|
| 503 | `AI_DISABLED` | Feature is off. Hide the entry point. |
| 404 | `SESSION_NOT_FOUND`, `GENERATION_NOT_FOUND` | Token/id unknown or expired (30 days idle). Drop the stored token, offer a new chat. |
| 400 | `VALIDATION_ERROR` | Bad input (`fieldErrors` map present). |
| 403 | `TURNSTILE_FAILED` | Captcha rejected. Re-render Turnstile and retry. |
| 409 | `BRIEF_INCOMPLETE` | Manual generate before the agent knows days/group/preferences. Show `missingFields`. |
| 409 | `GENERATION_IN_PROGRESS` | A generation is already running. Poll it instead. |
| 409 | `GENERATION_NOT_READY` | `select` called on a generation that is not `READY`. |
| 409 | `SESSION_BUSY` | Another request for this chat is still in flight (double-click). Retry once the first call returns. |
| 429 | `SESSION_TURN_LIMIT`, `GENERATION_LIMIT` | 30 messages / 5 generations per session. Offer "start a new chat". |
| 429 | `SESSION_DAILY_LIMIT` | 20 new sessions per IP per day. |
| 429 | `AI_BUSY` | Queue full. Retry generation in ~10 s. |
| 502 | `LLM_UNAVAILABLE`, `LLM_TIMEOUT` | The chat reply failed. The user's message **was saved**; show "try again" which re-sends the same text (the backend de-dupes an identical consecutive user message). |

## Flow

```
POST /ai/sessions ─→ chat with POST /ai/sessions/{token}/messages until
  response.generation appears ─→ poll GET /ai/generations/{id} every 2 s until READY|FAILED
  ─→ render 3 packages ─→ POST /ai/generations/{id}/select ─→ dispatch tripItems into the Trip Builder
Returning later: GET /ai/sessions/{token} rebuilds the whole screen.
```

## Endpoints

### `POST /ai/sessions` — start a chat

```json
{ "destinationSlug": "prague", "locale": "en",
  "turnstileToken": "…optional, required in prod…",
  "initialMessage": "optional first user message" }
```

`201` → `SessionState` (below). When `initialMessage` is given the response already
contains the agent's first reply; otherwise `messages` holds only the greeting.

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
if it changed the brief** (e.g. "actually 6 of us"); small talk does not.

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

`code`: `LLM_TIMEOUT` | `LLM_UNAVAILABLE` | `LLM_INVALID_OUTPUT` | `STALE` | `INTERNAL`.

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
  "limits": { "messagesLeft": 27, "generationsLeft": 5 }
}
```

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
