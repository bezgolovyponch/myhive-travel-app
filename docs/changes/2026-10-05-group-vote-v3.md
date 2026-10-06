# Group vote v3: "Ask the group", WhatsApp contact, organiser dashboard, friend review, "from" prices

Branch: `claude/nice-clarke-t5poik` → `main` ([PR #33](https://github.com/bezgolovyponch/myhive-travel-app/pull/33)),
also pushed to `test` · 10 code commits (94 files, +4369 / −1592) plus this summary

Code commits:
- `9ac316b` feat(vote): schema for group vote v3 - WhatsApp contacts, dropped activities, recommendations
- `54592ea` feat(vote): API for group vote v3 - WhatsApp contact, one ballot, recommendations, live edits
- `d1f4220` feat(vote): "Ask the group" and the new organiser contact modal (v3 4a)
- `2b681df` feat(vote): organiser dashboard in the Trip Builder tab (v3 4b)
- `2f0eb49` feat(vote): friend flow - full-screen swipe, review and recommend, then thank-you (v3 4c)
- `b7a7c68` feat(pricing): no per-person prices in lists; packages and the plan show "from €X"
- `19ca0c2` test(vote): keep the new tests within the repo's testing-library lint rules
- `51d3097` fix(vote): send the browser-picked link token with the create request
- `9f49f93` fix(vote): no screen wider than the phone, no button stretched across the page, no row without its picture
- `ff1cd28` fix(vote): phone-sized contact modal, fields and buttons keep their height

Design: `Group_Vote_v3.dc_1.html` (screens 4a contact modal, 4b organiser dashboard, 4c friend review).

## Why

- The main goal is to capture the organiser's contact. Before this change, the vote modal asked only for an email and listed benefits we could not deliver ("See who has voted").
- The organiser had no live view of the vote. The waiting page mixed the organiser and the friends, and friends saw the tally and the invite link.
- Friends could swipe but could not recommend anything. They could also come back and vote again.
- Lists showed per-person prices; the business wants one "from" price for the plan.

## The flow now

1. **Choose a package.** In the AI Trip Builder (`/plan`) the organiser picks a package and edits it. Every package shows "from €X".
2. **Ask the group.** The organiser clicks **Ask the group**, and the contact modal (4a) opens.
3. **Leave a contact.** Either button creates the vote, then opens the dashboard. If both fields are filled, both contacts are stored.
   - **Send to WhatsApp group** needs a valid number. It opens WhatsApp with the group message and the link.
   - **Start planning together** needs a valid email.
4. **Watch the vote.** The dashboard (4b) is the Trip Builder tab, opened through the manager link. It shows:
   - who has voted;
   - yes/no counts on every activity;
   - the invite link;
   - the group's recommendations;
   - **Complete booking**.
5. **Friends vote.** Each friend swipes full screen, then reviews their keeps and drops, recommends activities and sends once (4c). After that they only see a thank-you screen. Votes are anonymous.

## What changed

### 1. Database (`V10__group_vote_v3.sql`)
- `vote_sessions.initiator_phone varchar(32)`: the organiser's WhatsApp number in E.164 format.
- `contacts.phone varchar(32)` with a unique index, and `contacts.email` becomes nullable. Each row must have an email or a phone (check constraint `ck_contacts_email_or_phone`).
- `vote_session_activities.excluded_at`: set when the organiser drops an activity and cleared on Restore. Votes already cast are kept.
- New table `vote_recommendations` (session, voter token, activity, created_at), unique per (session, voter, activity).
- Verified against Postgres 16: a schema built from the previous entities, plus V10, passes `ddl-auto=validate` with the new entities.

### 2. Backend API
- `POST /vote/sessions/cart` and `POST /vote/sessions` take:
  - `initiatorPhone` (E.164; spaces and dashes are tolerated);
  - an optional `shareToken`, the link token picked in the browser.

  A cart vote needs a phone or an email, otherwise 400. A number without a country code is also 400. A `shareToken` already in use is 409.
- `PATCH /vote/sessions/{token}/contact?managerToken=` adds the organiser's other contact to the same vote. A newly added email gets the vote-created email with the dashboard link.
- `POST /vote/sessions/{token}/votes/batch`:
  - accepts `recommendedActivityIds`, saved in the same transaction as the votes;
  - a second ballot from the same friend returns 409.
- `GET /vote/sessions/{token}/tally` is organiser-only (manager token); friends get 403. Each row now also returns:
  - `skipCount` (the no votes), `excluded` and `imageUrl`;
  - `numberOfTravelers`;
  - `recommendations`: what friends recommended that is not on the ballot.
- Organiser-only, while the vote runs:
  - `POST …/activities/{id}/exclude` and `…/restore` drop and restore an activity;
  - `POST …/activities` adds one.
- `GET /vote/sessions/{token}/activities` leaves out dropped activities, and the frozen result leaves them out too.
- `GET /vote/sessions/{token}` returns `startDate` and `endDate`.
- `POST /pricing/quote {activityIds, travelers}` returns `{fromPrice}`. It is public and priced from the catalog, with the group minimum applied.
- AI packages get `fromPrice`. All "from" prices come from `FromPrice.of(total)`: the total less a fixed margin, in whole euros.
- The dashboard link in vote emails now opens `/destination/{slug}?tab=trip-builder&voteSession=…&manager=…`.
- `ContactService.touchPhone` and `PhoneNumbers` handle numbers. Phone numbers appear in the admin contacts list, the CSV export and the daily digest.

### 3. Contact modal (4a), `StartGroupVoteModal`
- Copy:
  - Headline: "Your group votes. You get the result."
  - Timeline: TODAY "The group gets the plan" · IN 12 HOURS "We keep everyone up to date" · IN 24 HOURS "The plan is ready, agreed by all".
  - Example card "The group's choice", fixed for everyone: AK-47 shooting 8 yes / 1 no · Steak and tits 7 / 2 · Tank driving 3 / 6 (dropped), "9 of 10 voted", "Group's recommendations +7".
  - A WhatsApp-style chat with the real message: "Lads! {Destination} stag, {dates}. Vote yes or no on the plan. Takes 1 minute 👇".
- Then: number field → **Send to WhatsApp group** → "or" → email field → **Start planning together** → "We only write to you about this trip."
- The link token is generated in the browser, so WhatsApp opens with the link in the same tap (phones only allow that inside the tap). A retry after a failed create reuses the same token.
- Size: 375px wide on desktop, and 10px from the screen edges on a phone. Fields and buttons stay 54px tall, and the sheet scrolls on short screens.
- The **Ask the group** button replaces "Send to a Prague planner" in the AI Trip Builder and "Start group vote" in the Trip Builder.

### 4. Organiser dashboard (4b), in the Trip Builder tab
- `useOrganizerVote`:
  - adopts the manager token from `?manager=` and strips it from the URL;
  - polls the tally every 30 s while the vote runs;
  - on a device with an empty cart, seeds the cart from the ballot, with the vote's group size and dates.
- The dashboard shows:
  - "Your weekend" and "N of M voted";
  - yes/no counts and a split bar on every activity;
  - the invite link block with Send to WhatsApp group and Copy link;
  - "Recommended by the group" with an Add button;
  - "Happy with the plan? Lock it in now" above Complete booking.
- Edits change the vote:
  - × drops an activity from the vote; a dropped row stays struck through, with its picture and **Restore**;
  - anything added joins the vote for friends who haven't voted yet;
  - a booking closes the vote.
- The old `/vote/{token}/waiting` page sends the organiser to the dashboard.

### 5. Friend flow (4c)
- A full-screen Tinder-style swipe, with no invite link and no prices.
- "My votes" review. Copy: "You kept 5 of 7 · 1 recommended · Browse activities and add more". Each keep or drop can be flipped, and the catalogue can be filtered by category chips to recommend activities.
- One **Send to group** sends everything. After that, and on every later visit, the friend sees only "Thanks, your vote is in." A friend arriving after the vote has closed sees "Voting has closed."
- The organiser opening the friend link is sent to the dashboard.

### 6. Prices
- No per-person price or group minimum on:
  - activity cards and the activity preview;
  - swipe cards;
  - the Trip Builder lists and itinerary lines;
  - the vote result;
  - the landing activity rows, swipe deck and shortlist.
- AI packages show `fromPrice`. The Trip Builder plan shows "Your plan · from €X" from `/pricing/quote`.
- Unchanged: the activity detail page, the landing cost calculator (a per-person estimate by design), catalogue packages, checkout and admin.

### 7. Copy
- All new strings are in `en.json` and `de.json`. The German copy still needs a native read, especially "Steak und Titten" and "JGA".

## How it was tested
- Backend: 1,228 tests, 0 failures. New tests: `VoteSessionGroupVoteTest`, `PriceQuoteServiceTest`, `FromPriceTest`, `PhoneNumbersTest`, plus contact, tally and controller tests.
- Frontend (CRA): 647 tests pass. New tests: `TripBuilderDashboard.test.js`, plus rewritten tests for the modal, vote page and waiting page.
- Next.js: 30 tests pass and `tsc` is clean. Both production builds succeed.
- End-to-end with Playwright at 390×844 and 1280×800, against the dev backend (H2 seed):
  - organiser: modal → WhatsApp → dashboard;
  - three friends: swipe → review → recommend → send → reopen the link;
  - organiser edits: drop, restore, add;
  - the tally refuses friends (403).

  Every screen was checked for page overflow, buttons wider than 480px and pictures that don't render.
- Two bugs were found by the end-to-end run and fixed:
  - the WhatsApp link pointed at a non-existent vote, because `voteApi` dropped `shareToken`;
  - the dashboard was 538px wide on a 390px phone.

## Deploy notes
- Flyway runs V10 on the next production deploy.
- The sandbox had only JDK 21, so the backend was built and tested on 21 with `--enable-preview` through a local init script, which is not committed. Run one build on JDK 25 before release.
- WhatsApp messages are never sent automatically: numbers are stored for a planner to follow up. Votes with only a phone get no emails.
