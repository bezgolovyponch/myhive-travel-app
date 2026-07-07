# CART Swipe Voting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CART vote-session participants ("Let your mates vote", the no-quiz flow) vote through the existing Tinder-style swipe deck instead of the ♥-toggle list; a left swipe is recorded on the backend as a skip (`liked:false`).

**Architecture:** Remove the backend `assertUpvoteOnly` guard so CART sessions accept `liked:false` like QUIZ sessions already do (ranking and tally count only `liked=true`, so skips are pure advisory data). Then delete the `voteMode` branch in `ActivityVotePage` so both modes render the shared `SwipeCard` deck, and delete the now-dead `CartVoteList`. No new endpoints, entities, or components.

**Tech Stack:** Spring Boot 4.0 / Java 25 / Gradle (backend, JUnit 5 + AssertJ + H2), React 19 / CRA Jest + React Testing Library (frontend).

**Spec:** `docs/superpowers/specs/2026-07-07-cart-swipe-voting-design.md`

## Global Constraints

- Google Java Style: no wildcard imports; always braces on `if/for/while`; K&R braces; one variable per declaration; `@Override` where applicable.
- Test style: `expected`-prefixed variables for values appearing in both arrange and assert sections; DTOs built inline when asserting specific field values.
- Shell commands below are for the Bash tool (Git Bash on Windows). Backend commands run from `myhive-backend/`, frontend from `myhive-react-app/`.
- CRA Jest gotcha: `resetMocks: true` is CRA's default — mock implementations set at module scope are reset between tests; set them in `beforeEach` or inside the test.
- Out of scope (do NOT touch): QUIZ flow behavior, `CuratePage`, session creation, emails, `VoteWaitingPage`/`VoteResultPage`, redirect-by-status for COMPLETED sessions, undo, showing skip counts in any UI.

---

### Task 1: Backend — CART sessions accept skips (remove `assertUpvoteOnly`)

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java` (call sites at lines 349 and 380, method at lines 664-668)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartVotingTest.java`

**Interfaces:**
- Consumes: existing `VoteSessionService.castVote(UUID shareToken, VoteRequest)`, `castVotes(UUID shareToken, VoteBatchRequest)`, `VoteActivityLikeRepository.findBySessionIdAndVoterTokenAndActivityId(UUID, UUID, UUID)`, `VoteSessionRepository.findByShareToken(UUID)`.
- Produces: CART sessions persist `liked:false` rows in `vote_activity_likes` exactly like QUIZ sessions (upsert per `(session, voterToken, activity)`). `BadRequestException("This vote session accepts upvotes only")` no longer exists anywhere. Tasks 2-3 rely on this.

- [ ] **Step 1: Rewrite the two rejection tests as skip-recording tests**

In `VoteSessionCartVotingTest.java`:

Add imports (keep alphabetical order within the existing groups):

```java
import com.myhive.backend.entity.VoteActivityLike;
import com.myhive.backend.repository.VoteActivityLikeRepository;
import com.myhive.backend.repository.VoteSessionRepository;
```

Remove now-unused imports: `com.myhive.backend.exception.BadRequestException` and the static `org.assertj.core.api.Assertions.assertThatThrownBy` (the `assertThatCode` static import stays — `castVotes_acceptsUpvoteBatchOnCartSession` still uses it).

Add two autowired fields next to the existing ones:

```java
    @Autowired private VoteActivityLikeRepository voteActivityLikeRepository;
    @Autowired private VoteSessionRepository voteSessionRepository;
```

Replace `castVote_rejectsDownvoteOnCartSession_400` (lines 38-53) with:

```java
    @Test
    void castVote_recordsSkipOnCartSession() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);
        UUID expectedVoterToken = UUID.randomUUID();

        VoteRequest skip = new VoteRequest();
        skip.setVoterToken(expectedVoterToken);
        skip.setActivityId(barCrawl.getId());
        skip.setLiked(false);

        voteSessionService.castVote(session.getShareToken(), skip);

        VoteActivityLike recorded =
                recordedVote(session.getShareToken(), expectedVoterToken, barCrawl.getId());
        assertThat(recorded.getLiked()).isFalse();
    }
```

Replace `castVotes_rejectsBatchContainingDownvote_400` (lines 55-70) with:

```java
    @Test
    void castVotes_recordsSkipsInMixedBatchOnCartSession() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        Activity karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);
        UUID expectedVoterToken = UUID.randomUUID();

        VoteBatchRequest batch = batch(expectedVoterToken,
                vote(barCrawl.getId(), true), vote(karting.getId(), false));

        voteSessionService.castVotes(session.getShareToken(), batch);

        assertThat(recordedVote(session.getShareToken(), expectedVoterToken, barCrawl.getId())
                .getLiked()).isTrue();
        assertThat(recordedVote(session.getShareToken(), expectedVoterToken, karting.getId())
                .getLiked()).isFalse();
    }
```

Add a private helper below the existing helpers:

```java
    private VoteActivityLike recordedVote(UUID shareToken, UUID voterToken, UUID activityId) {
        UUID sessionId = voteSessionRepository.findByShareToken(shareToken).orElseThrow().getId();
        return voteActivityLikeRepository
                .findBySessionIdAndVoterTokenAndActivityId(sessionId, voterToken, activityId)
                .orElseThrow();
    }
```

- [ ] **Step 2: Run the test class — expect the two new tests to FAIL**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartVotingTest'`
Expected: FAIL — `castVote_recordsSkipOnCartSession` and `castVotes_recordsSkipsInMixedBatchOnCartSession` throw `BadRequestException: This vote session accepts upvotes only`. `castVotes_acceptsUpvoteBatchOnCartSession` still passes.

- [ ] **Step 3: Remove the guard from `VoteSessionService`**

In `myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java`:

Delete from `castVote` (line 349 plus its trailing blank line):

```java
        assertUpvoteOnly(session, !request.getLiked());

```

Delete from `castVotes` (line 380 plus its trailing blank line):

```java
        assertUpvoteOnly(session, request.getVotes().stream().anyMatch(item -> !item.getLiked()));

```

Delete the method (lines 664-668):

```java
    private void assertUpvoteOnly(VoteSession session, boolean downvoteRequested) {
        if (session.getVoteMode() == VoteMode.CART && downvoteRequested) {
            throw new BadRequestException("This vote session accepts upvotes only");
        }
    }
```

`VoteMode` and `BadRequestException` are still used elsewhere in the class (e.g. `getTally`'s 409 check uses `VoteMode`, `castVote` still throws `BadRequestException` for inactive sessions), so their imports stay. Verify with: `cd myhive-backend && grep -n 'VoteMode\.\|BadRequestException(' src/main/java/com/myhive/backend/service/VoteSessionService.java` — both must still have hits; if either has none, remove its import.

- [ ] **Step 4: Run the test class — expect PASS**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionCartVotingTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/VoteSessionService.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartVotingTest.java
git commit -m "feat(vote): accept skips (liked:false) on CART sessions"
```

---

### Task 2: Backend — regression coverage: skips don't affect tally, ranking, or participation

These are characterization tests pinning behavior that already exists after Task 1 (the like/skip separation lives in `VoteActivityLikeRepository.findVoteCountsBySessionId`, tally access in `existsBySessionIdAndVoterToken`). They are expected to pass on first run — if any fails, STOP and investigate before changing production code.

**Files:**
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionTallyTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartProcessTest.java`

**Interfaces:**
- Consumes: Task 1 (CART accepts `liked:false`); existing `getTally(UUID, UUID, UUID)`, `processSession(VoteSession)`, `getResult(UUID)`, `ResultActivityDTO.getSkipCount()`.
- Produces: nothing new — regression guard only.

- [ ] **Step 1: Generalize the vote helper in `VoteSessionTallyTest` and add two tests**

In `VoteSessionTallyTest.java`, replace the `castUpvote` helper (lines 123-131) with:

```java
    private void castVote(UUID shareToken, UUID voterToken, UUID activityId, boolean liked) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(voterToken);
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        batch.setVotes(List.of(item));
        voteSessionService.castVotes(shareToken, batch);
    }
```

Update the single existing call site (line 70):

```java
        castVote(session.getShareToken(), voterToken, karting.getId(), true);
```

Add two tests after `getTally_returnsSortedCountsForVoter`:

```java
    @Test
    void getTally_allSkipsVoterIsParticipantWithTallyAccess() {
        long expectedParticipants = 1L;

        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));
        VoteSessionResponse session = createCartSession(prague, barCrawl);

        UUID voterToken = UUID.randomUUID();
        castVote(session.getShareToken(), voterToken, barCrawl.getId(), false);

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterToken, null);

        assertThat(tally.getParticipantCount()).isEqualTo(expectedParticipants);
        assertThat(tally.getRows().get(0).getLikeCount()).isZero();
    }

    @Test
    void getTally_skipsDoNotInflateLikeCountsOrRanking() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity barCrawl = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 0 likes, 2 skips
        Activity karting = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 1 like, 1 skip
        VoteSessionResponse session = createCartSession(prague, barCrawl, karting);

        UUID voterA = UUID.randomUUID();
        UUID voterB = UUID.randomUUID();
        castVote(session.getShareToken(), voterA, barCrawl.getId(), false);
        castVote(session.getShareToken(), voterA, karting.getId(), true);
        castVote(session.getShareToken(), voterB, barCrawl.getId(), false);
        castVote(session.getShareToken(), voterB, karting.getId(), false);

        VoteTallyResponse tally = voteSessionService.getTally(session.getShareToken(), voterA, null);

        assertThat(tally.getParticipantCount()).isEqualTo(2);
        assertThat(tally.getRows()).extracting(VoteTallyResponse.TallyRow::getName)
                .containsExactly("Karting", "Bar Crawl");
        assertThat(tally.getRows().get(0).getLikeCount()).isEqualTo(1);
        assertThat(tally.getRows().get(1).getLikeCount()).isZero();
    }
```

- [ ] **Step 2: Add a frozen-ranking-with-skips test to `VoteSessionCartProcessTest`**

In `VoteSessionCartProcessTest.java`:

Add import `java.util.ArrayList` (the `java.util.List` and `java.util.UUID` imports already exist).

Replace the `castUpvotes` helper (lines 123-133) with a general helper plus a delegating wrapper (existing call sites keep compiling):

```java
    private void castUpvotes(UUID shareToken, List<UUID> activityIds) {
        castBallot(shareToken, activityIds, List.of());
    }

    private void castBallot(UUID shareToken, List<UUID> likedIds, List<UUID> skippedIds) {
        VoteBatchRequest batch = new VoteBatchRequest();
        batch.setVoterToken(UUID.randomUUID());
        List<VoteBatchRequest.VoteItem> items = new ArrayList<>();
        likedIds.forEach(id -> items.add(voteItem(id, true)));
        skippedIds.forEach(id -> items.add(voteItem(id, false)));
        batch.setVotes(items);
        voteSessionService.castVotes(shareToken, batch);
    }

    private VoteBatchRequest.VoteItem voteItem(UUID activityId, boolean liked) {
        VoteBatchRequest.VoteItem item = new VoteBatchRequest.VoteItem();
        item.setActivityId(activityId);
        item.setLiked(liked);
        return item;
    }
```

Add a test after `processSession_cart_breaksTiesByCartOrder`:

```java
    @Test
    void processSession_cart_skipsDoNotAffectRankingButShowInResult() {
        Destination prague = destinationRepository.save(TestDataFactory.destination("Prague"));
        Activity first = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Bar Crawl", new BigDecimal("45.00")));   // 0 likes, 2 skips
        Activity second = activityRepository.saveAndFlush(
                TestDataFactory.activity(prague, "Karting", new BigDecimal("45.00")));     // 1 like, 1 skip
        VoteSessionResponse created = createCartSession(prague, first, second);

        castBallot(created.getShareToken(), List.of(second.getId()), List.of(first.getId()));
        castBallot(created.getShareToken(), List.of(), List.of(first.getId(), second.getId()));

        VoteSession session = voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow();
        voteSessionService.processSession(session);

        List<VoteSessionResultActivity> results = resultActivityRepository
                .findBySessionIdOrderBySortOrder(
                        voteSessionRepository.findByShareToken(created.getShareToken()).orElseThrow().getId());

        // Karting holds the only like; Bar Crawl's two skips must not count as votes.
        assertThat(results).extracting(r -> r.getActivity().getId())
                .containsExactly(second.getId(), first.getId());

        VoteResultResponse result = voteSessionService.getResult(created.getShareToken());
        assertThat(result.getResult().get(0).getLikeCount()).isEqualTo(1);
        assertThat(result.getResult().get(0).getSkipCount()).isEqualTo(1);
        assertThat(result.getResult().get(1).getLikeCount()).isZero();
        assertThat(result.getResult().get(1).getSkipCount()).isEqualTo(2);
    }
```

- [ ] **Step 3: Run both test classes — expect PASS**

Run: `cd myhive-backend && ./gradlew test --tests '*VoteSessionTallyTest' --tests '*VoteSessionCartProcessTest'`
Expected: PASS (all tests, including the 3 new ones). If a new test fails, STOP — the skip semantics differ from the spec's verified assumptions; investigate before touching production code.

- [ ] **Step 4: Run the full backend suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: PASS. (Catches any other test that depended on the removed guard.)

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionTallyTest.java myhive-backend/src/test/java/com/myhive/backend/service/VoteSessionCartProcessTest.java
git commit -m "test(vote): pin skip semantics for CART tally, ranking and participation"
```

---

### Task 3: Frontend — `ActivityVotePage` renders the swipe deck for both modes

**Files:**
- Modify: `myhive-react-app/src/pages/vote/ActivityVotePage.js`
- Test: `myhive-react-app/src/pages/vote/ActivityVotePage.test.js`

**Interfaces:**
- Consumes: Task 1 (backend accepts `liked:false` on CART); existing `SwipeCard` (props: `cards`, `currentIndex`, `onSwipe(direction, cardId)`, `title`, `subtitle`, `shareUrl`, `getCardLink`; its ✕/♥ buttons have aria-labels `Dislike`/`Like`); `voteApi.castVotes(shareToken, {voterToken, votes})`.
- Produces: `ActivityVotePage` never calls `voteApi.getSession` and has no `CartVoteList` import — Task 4 relies on that import being gone.

- [ ] **Step 1: Update the tests**

In `ActivityVotePage.test.js`:

1. Simplify `beforeEach` (remove the `getSession` stub):

```js
beforeEach(() => {
    localStorage.clear();
    pushEvent.mockClear();
});
```

2. Delete the test `'a getSession failure blocks the voting UI instead of falling back to swipe'` (lines 50-58) — the hazard it guarded (swipe deck emitting `liked:false` into a CART session) no longer exists.

3. Replace the `// --- CART voteMode branch ---` section (lines 200-211, the `'renders the list voting UI for CART sessions'` test) with:

```js
// --- CART unification: one swipe deck for both modes ---

test('renders the swipe deck without fetching the session', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);

    renderAt('/vote/tok-cart/activities');

    expect(await screen.findByText(/which activities are you up for/i)).toBeInTheDocument();
    expect(voteApi.getSession).not.toHaveBeenCalled();
});

test('left swipes are submitted as liked:false alongside right swipes', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);
    voteApi.castVotes.mockResolvedValue({});

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByLabelText('Like')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Like'));    // act1 → right
    await userEvent.click(screen.getByLabelText('Dislike')); // act2 → left (last card)

    await screen.findByText('waiting page');

    expect(voteApi.castVotes).toHaveBeenCalledWith('tok-abc', {
        voterToken: expect.any(String),
        votes: [
            { activityId: 'act1', liked: true },
            { activityId: 'act2', liked: false },
        ],
    });
});
```

- [ ] **Step 2: Run the test file — expect broad FAIL**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=ActivityVotePage`
Expected: FAIL. Without the `beforeEach` stub, `voteApi.getSession` is an auto-mock returning `undefined`, so the page's `.then(setSession)` throws in every test that renders the deck, and `'renders the swipe deck without fetching the session'` fails its `not.toHaveBeenCalled()` assertion.

- [ ] **Step 3: Remove the session fetch and CART branch from `ActivityVotePage.js`**

Four deletions and one simplification (rest of the file is untouched — `handleSwipe`, `submitVotes`, error states, `getCardLink`, the `SwipeCard` render, `VoteMeta`):

1. Delete the import: `import CartVoteList from '../../components/vote/CartVoteList';`
2. Delete the two state lines:

```js
    const [session, setSession] = useState(null);
    const [sessionLoaded, setSessionLoaded] = useState(false);
```

3. Delete the whole `getSession` effect (lines 45-63, including the "failed session fetch must block the voting UI" comment — its rationale died with the upvote-only guard):

```js
    useEffect(() => {
        // Already-voted visitors are redirected by the effect above — skip the
        // session request entirely for them.
        if (localStorage.getItem(votedKey(shareToken))) {
            return;
        }
        voteApi.getSession(shareToken)
            .then(setSession)
            .catch(e => {
                // A failed session fetch must block the voting UI, not fall back to
                // the swipe deck: for a CART session that fallback would let the
                // participant submit liked:false votes, which the backend rejects
                // with 400 ("This vote session accepts upvotes only"), stranding
                // them on a terminal error screen. Reuse the same error state the
                // activities fetch uses so the existing error UI renders instead.
                setError(e.message);
            })
            .finally(() => setSessionLoaded(true));
    }, [shareToken]);
```

4. Simplify the loading gate:

```js
    if (loading) return (
        <div className="vote-state">Loading activities...</div>
    );
```

5. Delete the CART branch:

```js
    if (session?.voteMode === 'CART') {
        return (
            <CartVoteList
                shareToken={shareToken}
                activities={activities}
                voterToken={voterToken}
            />
        );
    }
```

- [ ] **Step 4: Run the test file — expect PASS**

Run: `cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=ActivityVotePage`
Expected: PASS (all tests, including both new ones).

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/vote/ActivityVotePage.js myhive-react-app/src/pages/vote/ActivityVotePage.test.js
git commit -m "feat(vote): swipe deck voting for CART participants"
```

---

### Task 4: Frontend — delete the dead `CartVoteList`

**Files:**
- Delete: `myhive-react-app/src/components/vote/CartVoteList.js`
- Delete: `myhive-react-app/src/components/vote/CartVoteList.css`
- Delete: `myhive-react-app/src/components/vote/CartVoteList.test.js`

**Interfaces:**
- Consumes: Task 3 (the only production import of `CartVoteList` is gone).
- Produces: nothing — dead-code removal.

- [ ] **Step 1: Verify nothing references it anymore**

Run: `cd myhive-react-app && grep -rn 'CartVoteList' src`
Expected: hits only inside the three files being deleted. If any other file matches, STOP — Task 3 is incomplete.

- [ ] **Step 2: Delete the three files**

```bash
git rm myhive-react-app/src/components/vote/CartVoteList.js myhive-react-app/src/components/vote/CartVoteList.css myhive-react-app/src/components/vote/CartVoteList.test.js
```

- [ ] **Step 3: Run the full frontend suite**

Run: `cd myhive-react-app && npm test -- --watchAll=false`
Expected: PASS, no test file errors about missing modules.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(vote): delete dead CartVoteList after swipe unification"
```

---

### Task 5: Docs & memory — AFTER user approval only

Per CLAUDE.md workflow rule 3, run this task only after the user has reviewed and approved the implementation (Tasks 1-4). If executing via subagents, the orchestrator handles this task in the main session after approval.

**Files:**
- Modify: `README.md:55` and `README.md:60`
- Modify: `C:\Users\dijtb\.claude\projects\C--Users-dijtb-IdeaProjects-myhive-travel-app\memory\project_cart_vote_flow.md`

**Interfaces:**
- Consumes: shipped Tasks 1-4.
- Produces: docs consistent with the new behavior.

- [ ] **Step 1: Fix the two stale "upvote-only" claims in `README.md`**

Lines 55-56, replace:

```markdown
  - `POST /vote/sessions/cart` — create a cart-seeded, upvote-only vote session (no quiz); body
    `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds}`.
```

with:

```markdown
  - `POST /vote/sessions/cart` — create a cart-seeded vote session (no quiz); body
    `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds}`.
```

Lines 59-61, replace:

```markdown
  - Sessions carry a `voteMode`: `QUIZ` (default) runs the existing quiz + score-cutoff + budget-knapsack
    flow; `CART` is an advisory upvote-only ranking of the traveler's own cart with no score cutoff and no
    budget knapsack — results annotate the Trip Builder itinerary and never replace the cart.
```

with:

```markdown
  - Sessions carry a `voteMode`: `QUIZ` (default) runs the existing quiz + score-cutoff + budget-knapsack
    flow; `CART` is an advisory swipe vote (right = like, left = skip) over the traveler's own cart, ranked
    by like count with cart-order ties (skips are recorded but never affect the ranking), with no score
    cutoff and no budget knapsack — results annotate the Trip Builder itinerary and never replace the cart.
```

- [ ] **Step 2: Update memory `project_cart_vote_flow.md`**

Update the stale claims: CART is no longer upvote-only (`assertUpvoteOnly` removed 2026-07-07); participants use the shared `SwipeCard` deck (CartVoteList deleted); the "participants who like nothing can't submit" gotcha is gone (every card yields a vote; all-skips voters count as participants and can see the tally). Reference spec `docs/superpowers/specs/2026-07-07-cart-swipe-voting-design.md`.

- [ ] **Step 3: Commit the README change**

```bash
git add README.md
git commit -m "docs: CART vote sessions record swipe skips (no longer upvote-only)"
```

---

## Verification checklist (whole feature)

- `cd myhive-backend && ./gradlew test` — all green.
- `cd myhive-react-app && npm test -- --watchAll=false` — all green.
- Manual smoke (dev): create a CART session from Trip Builder ("Let your mates vote"), open the invite link in an incognito window → swipe deck renders; swipe some left/right → lands on waiting page with live tally; tally like counts match right swipes only.
