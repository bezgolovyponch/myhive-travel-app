# CART Swipe Voting — Design

**Date:** 2026-07-07
**Status:** Approved
**Supersedes (partially):** `2026-07-05-cart-vote-flow-design.md` — the participant voting UI and the
upvote-only rule described there are replaced by this design. Session creation, tally access rules,
ranking, result handling, and Trip Builder annotation are unchanged.

## Problem

Participants in a CART vote session ("Let your mates vote", the flow without a quiz) currently vote
through `CartVoteList` — a scrollable list with ♥ toggle buttons and a manual submit. Participants
in QUIZ sessions vote through `SwipeCard`, a Tinder-style swipe deck. The product decision is to
give CART participants the same swipe experience.

The blocker is the backend guard `assertUpvoteOnly` (`VoteSessionService`): CART sessions reject
`liked:false` with 400 "This vote session accepts upvotes only", while a left swipe in the deck
produces exactly `liked:false`.

## Decision summary

1. **Left swipe records a skip on the backend.** The `assertUpvoteOnly` guard is removed (both
   call sites: `castVote`, `castVotes`). CART sessions accept `liked:false` rows the same way QUIZ
   sessions do.
2. **Auto-submit after the last card**, identical to the QUIZ flow: the batch is sent right after
   the final swipe, then the participant is redirected to `/vote/{token}/waiting`.
3. **Full UI unification.** `ActivityVotePage` renders `SwipeCard` for both modes; `CartVoteList`
   is deleted.

## Frontend changes (`myhive-react-app`)

### `ActivityVotePage.js`

- Remove the `session?.voteMode === 'CART'` branch and the `CartVoteList` render path.
- Remove the `voteApi.getSession(shareToken)` effect entirely, together with the `session` and
  `sessionLoaded` state and the `!sessionLoaded` loading gate. The session fetch existed only to
  pick the UI branch; with a single UI it has no remaining consumer. (Verified: nothing else in the
  page reads `session`.) This also deletes the "failed session fetch must block the voting UI"
  comment block — the hazard it guarded against (swipe deck emitting `liked:false` into a CART
  session) no longer exists.
- The swipe deck keeps its current copy ("Which activities are you up for?" / "Swipe right to vote
  yes, left to skip"), the ✕/♥ buttons, the `ActivityPreviewModal` info modal via card-name tap,
  and `getCardLink`.

### Deletions

- `src/components/vote/CartVoteList.js` (+ its CSS if dedicated) and its tests — dead code after
  unification.

### Behavior notes

- Every card produces a vote (left → `liked:false`, right → `liked:true`), so the batch always
  contains one item per activity — the `@NotEmpty` constraint on `VoteBatchRequest` can never
  trigger from this flow. The former gotcha "participants who like nothing can't submit and never
  see the tally" disappears.
- Analytics events are unchanged: `vote_opened` on page load, `vote_completed` after successful
  batch submit (already emitted by the shared `submitVotes` path).
- The existing "Session is full" catch (redirect to waiting) and error states in `submitVotes` are
  unchanged.

## Backend changes (`myhive-backend`)

### `VoteSessionService`

- Delete `assertUpvoteOnly(VoteSession, boolean)` and both call sites in `castVote` and
  `castVotes`.
- Everything else stays: `assertVoterAllowed` (max-participants), upsert semantics on
  `vote_activity_likes` (unique `(session_id, voter_token, activity_id)`), ACTIVE-status check,
  destination-membership validation.

### Ranking and tally — no changes needed (verified)

- Live tally (`getTally`) and the frozen CART ranking (`freezeCartRanking` via `cartRankingOrder`)
  rank by like count descending with cart-order tiebreak. The count query
  (`VoteActivityLikeRepository.findVoteCountsBySessionId`) separates `likeCount` (`liked = true`)
  and `skipCount` (`liked = false`), so skip rows never inflate like counts.
- Tally access (`existsBySessionIdAndVoterToken`) and `participantCount`
  (`countDistinctVoterTokensBySessionId`) count any vote rows regardless of `liked`, so a
  participant who swipes everything left still counts as having voted, gains live-tally access,
  and appears in the participant count.
- `ResultActivityDTO` already carries both `like` and `skip`; CART results will now contain
  non-zero skips. No result-UI changes are in scope.

## Testing

### Backend

- Update tests that assert 400 on `liked:false` for CART sessions (castVote and castVotes paths):
  the new expectation is that the skip is recorded.
- Add/adjust coverage: CART tally and frozen ranking ignore skips (an activity with many skips and
  few likes ranks below one with more likes); an all-skips voter is counted as a participant and
  passes the tally access check.
- Remove tests of `assertUpvoteOnly` itself.

### Frontend

- Update `ActivityVotePage` tests: CART sessions render the swipe deck; the page no longer calls
  `getSession`; left swipes are submitted as `liked:false`.
- Delete `CartVoteList` tests.

## Out of scope

- Redirect-by-status for COMPLETED sessions on the voting page (pre-existing backlog item).
- Undo/review of swipes before submit (explicitly decided against — auto-submit like QUIZ).
- Displaying skip counts in the tally or result UI.
- Any change to session creation, emails, Trip Builder annotation, or the QUIZ flow.
