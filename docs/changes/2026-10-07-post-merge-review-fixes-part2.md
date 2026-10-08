# 2026-10-07 — Post-merge review of PR #33–#37, part 2: the remaining findings

Part 1 (`2026-10-07-post-merge-review-fixes.md`, PR #38) closed the three findings that lose an organiser
their vote or show a wrong price. This part closes the rest of the review of `8e38b502..main`.

## AI planner

**A tap on a card is answered by id, not by name** — `AiSessionService.editDraft`, `EditRequest`, `PackageEditor`
`editDraft` resolved the tapped activity by UUID, then handed only its *name* to the editor, which resolved it
again through `ActivityNameResolver`. Prod carries pairs of activities with one name (slugs suffixed on
collision: `bubble-football`/`-2`, `axe-throwing`/`-2`, `laser-tag`/`-2`), so a tap on one of them came back as
"which one?". `EditRequest` gained an optional `activityId`, set only by a tap, never by the model; the editor
takes that row from the snapshot (`byIdOrReject`) and resolves by name only when there is no id. The chat's
JSON contract and the model's schema are unchanged, and `LlmOutputParser` strips an `activityId` the model
writes into an edit before reading it (the planner prompts carry such codes, and a code is no UUID — read as
one it would have failed the parse and lost the edit).

**"The top match is above" only when there is one** — `ChatTurnNode`
Under a draft a typed "add X" is offered on a card rather than carried out. The reply was replaced with
"The top match is above - add it" whenever an ADD was intercepted, but the names came straight from the
model, and `AiDtoMapper.recommendations()` drops what does not resolve: for "add bungee jumping" the organiser
read that line over an empty row, and the old "I could not find … in the catalog" was gone because the ADD
never reached `ApplyEditsNode`. The node now resolves the offered names itself: `RECOMMENDATIONS` holds only
catalog rows; what the organiser typed and the catalog lacks is said ("I could not find …"), a typed name that
fits two rows is a question back ("… which one?", as a rejected edit would put it), and when there is also a
row to point at the two are said together ("I could not find X. The top match is above …"). The model's own
unprompted recommendations and alternatives are offered when they resolve and dropped quietly when they do
not — nobody asked for them, so the model's reply stands. The catalog blob is parsed once per chat turn (it
was parsed up to four times: the request, the themes, the variants check, the chips).

**The checkpoint is parsed once per response** — `AiDtoMapper`
`recommendations()`, `gaps()` and `suggestions()` each called `state.catalog()`, `state.brief()` and
`state.result()`, and `PlannerState` deserialises the JSON on every call — three to four parses of the
catalog per `GET /ai/sessions/{token}`, on the request thread of a page that polls. A private `Draft` record
parses the four blobs once in `sessionState`/`turn` and hands them to the three helpers.
`AiDtoMapper.recommendations(PlannerState)` stays public for `editDraft` and reads only the catalog and the
brief, as before.

## Group vote

**The organiser is not left on "loading" forever** — `ActivityVotePage`, `voteApi.getSession`
A browser holding the manager token never requests the activities, so a failed `getSession` (a deleted or
expired vote, the API down for a moment) had no one to report it, and the render kept the loading state for
every organiser. The error is now set for the organiser and checked before the loading state; `getSession`
names a 404 `Vote session not found`, like `getActivities`, so the organiser gets the same "this vote session
no longer exists" message a friend does. An organiser whose vote has no destination page is sent to the
result page instead of being left on "loading", as on `VoteWaitingPage`.

**A removal before the first tally still drops the activity from the vote** — `TripBuilder.handleRemoveActivity`
`excludeActivity` was sent only when the tally already listed the activity; in the window before the first
tally (or after a failed poll) a removal left the plan and the ballot apart, with no Restore row. While the
vote is live, a removal is sent whenever the tally is unknown or lists it — the server answers 404 for an
activity it does not hold, harmlessly.

**No link to `/destination/undefined`** — `VoteWaitingPage`
The organiser redirect now checks `destinationSlug` and sends a vote without one to the result page, as the
pre-v3 page did. An older email link's `?manager=` is kept in `localStorage` on that path — the dashboard
adopts it itself, the result page only reads it from storage.

## Repository

`docs/.DS_Store` (committed in `c9f0e31`) is untracked, and the root `.gitignore` now ignores `.DS_Store`
(only the two module `.gitignore`s did).

## Tests

- Backend: `PackageEditorTest` (an id places its row where the name is ambiguous), `AiSessionServiceTest`
  (`editDraft` carries the tapped id), `ChatTurnNodeTest` (a typed add the catalog lacks is said, not shown;
  a resolvable alternative is shown with the missing name said first; "beer" over two rows asks which one;
  an unresolvable model recommendation is dropped and the reply kept; the catalog is parsed once per turn),
  `LlmOutputParserTest` (a model-written `activityId` is ignored, the edit kept), `AiDtoMapperTest` (one
  parse per response; the tap path reads only catalog and brief — both verified on a spied state).
- Frontend: `ActivityVotePage.test.js` (organiser of a gone vote sees the message; no slug → result page),
  `voteApi.test.js` (`getSession` 404), `TripBuilderDashboard.test.js` (removal before the first tally),
  `VoteWaitingPage.test.js` (no slug → result page; the email link's manager token is kept on that path).

## Not in this PR

A cart whose package was deleted, or whose activity an admin took out of the package, gets no "from" price
and is refused at booking: the stale package context lives in `localStorage` and needs its own fix.
