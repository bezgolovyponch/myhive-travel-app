# 2026-10-07 — Post-merge review of PR #33–#37: the three fixes that touch money and the organiser's vote

A review of the range `8e38b502..main` (group vote v3, its follow-up, preset packages, the trip draft,
the memory headroom) found eleven issues; this change closes the three that lose an organiser their
vote or show a wrong price. The rest (AI planner edit-by-name, the stuck organiser page, the catalog
re-parsed per read, the fifth copy of the floor, `docs/.DS_Store`) follow in a second PR.

## 1. The organiser dashboard hammered the server when the vote refused an activity

`TripBuilder`'s ballot-sync effect puts every planned activity the vote lacks onto the ballot. A refused
add (an activity from another destination in the cart, or one deleted from the catalogue — the server
answers 4xx every time) reloaded the tally like any edit, the fresh tally reset the "already asked" set,
and the effect asked again: an unbounded `POST …/activities` + `GET …/tally` loop until the tab closed,
which also tripped the 100 req/min rate limit and took the dashboard down.

Now each activity is asked for **once per plan**: the set resets when the plan's standalone ids change
(or the vote does), never on a tally. A refused activity (4xx, bar 429) is asked again only after the
organiser changes the plan; an add that got no answer, a 5xx or a 429 is asked again on the next tally
(the 30 s poll), as before. `voteApi`'s edit errors carry the HTTP `status` for that, and
`useOrganizerVote.edit` resolves to the failure (or null) instead of swallowing it.
`TripBuilderDashboard.test.js` reproduces the loop with event-loop-timed responses (so a loop shows as a
count, not a hung test) and the poll-driven retry with fake timers.

## 2. A retry after a lost create response met a 409 forever

The contact modal picks the vote's link token in the browser (so the WhatsApp message can open in the
same tap) and reuses it on a retry. When the create committed but its response was lost on a flaky
connection, every retry hit `VoteSessionService.newSession`'s "This vote link is already in use" and the
modal showed a generic error: the group had a working link, the organiser never reached the dashboard,
and reopening the modal (new token) made a second vote.

The browser now picks the **manager token** too (`managerToken` on `POST /vote/sessions` and
`POST /vote/sessions/cart`, optional; the server still picks one when absent). A create that names a
link token already in use **with the same manager token** returns the vote already created — same
tokens, live participant count, nothing written, no second confirmation email. Any other manager token,
or none, is the 409 it always was: the manager token is the secret that authorises organiser actions, so
only the browser that created the vote can replay the create. Same entropy as before: `generateUuid()`
uses `crypto.getRandomValues`, as `UUID.randomUUID()` does on the server.

The rule lives in one place (`VoteSessionService.alreadyCreated`); `newSession` no longer repeats the
lookup. Two creates racing for one link token (a retry overlapping the original) meet the unique index on
`share_token`, and `GlobalExceptionHandler` now reports a `DataIntegrityViolationException` as a 409
instead of a 500 — the next tap replays the create.

## 3. The plan's "from €X" ignored package discounts

`usePlanFromPrice` sent every trip item's activity id to `POST /pricing/quote`, which summed them at
catalog price × 0.90. The booking step (`tripPricing.computeTripTotal`, `BookingService`) applies each
package's `discount_pct` to its group, so for any package above 10 % the teaser read **higher** than the
price asked on the next screen.

`POST /pricing/quote` now takes `items: [{activityId, packageId?}]`; lines that name the same package
are that package and get its catalog discount after each line's group minimum (floor-before-discount,
as the booking bills it). The discount comes from the database by `packageId`; an unknown package, or an
activity that is not one of the package's (`Package.containsActivity`, the check `BookingService` makes
before it bills a package line), is a 400 — the hook then shows no price rather than a discounted one
the booking would refuse. The old `activityIds` shape is still read when `items` is absent, so a browser
on the previous bundle keeps its price during the deploy; it goes with the next release.
`PriceQuoteService` prices each line with `PlanPricer.lineTotal` instead of carrying a fifth copy of the
floor. `pricingApi.quote` sends `items` (the adapter is covered by `pricingApi.test.js` now, since the
component tests mock it).

Known limit, not new: a cart whose package was deleted, or whose activity an admin took out of the
package, gets no "from" price and is refused at booking — the stale package context lives in
`localStorage` and needs its own fix.

## Tests

- Backend: `PriceQuoteServiceTest` (package lines discounted after the floor; an activity outside its
  package rejected; unknown package rejected; the `activityIds` shape still priced),
  `VoteSessionCartCreateTest` (the browser's manager token is used; the same tokens return the vote
  already created without a second ballot; another or no manager token is a conflict),
  `VoteSessionServiceTest` (QUIZ replay writes nothing and sends no email; conflict),
  `GlobalExceptionHandlerTest` (integrity violation → 409).
- Frontend: `TripBuilderDashboard.test.js` (refused add asked once; an unanswered add asked again on the
  next poll), `StartGroupVoteModal.test.js` (retry reuses both tokens), `usePlanFromPrice.test.js` (items
  with their package), `pricingApi.test.js` (request body), `TripBuilder.test.js` (quote shape).
