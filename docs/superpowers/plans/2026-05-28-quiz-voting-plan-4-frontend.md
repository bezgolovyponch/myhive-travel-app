# Quiz-Driven Voting — Plan 4: Frontend & Admin UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the React UI to the Plan 1–3 backend. Replace `CategoryVotePage` with the new flow (Quiz → Pool → Curate → vote curated list → new Result page with budget + suggestions). Add `featured_weight` to the activity admin form + CSV, and a "Quiz" tab in the destinations admin.

**Architecture:** A single `voteApi.js` module gains the new endpoints (`getPublicQuiz`, `buildPool`, atomic `createSession`, participant quiz, new-shape `getResult`). The organizer flow becomes a 3-page wizard (`TripSetupModal` → `/vote/new/quiz` → `/vote/new/curate` → atomic `POST /vote/sessions` → `/vote/:shareToken/waiting`). A `voterToken` is generated once per browser and stored in `localStorage` (used by both organizer and participants). The legacy `CategoryVotePage` and its route are removed. Admin gets a `Featured weight` numeric field on the activity edit form, a new `featured_weight` CSV column (optional, default 0), and a "Quiz" tab on the destinations admin that loads/saves the whole quiz via `GET/PUT /admin/destinations/{id}/quiz`.

**Tech Stack:** React 19 (CRA), React Router v6, plain CSS, Jest + React Testing Library. Backend: Spring Boot 4.0 (already shipped Plans 1–3).

**Reference:** spec at `docs/superpowers/specs/2026-05-11-quiz-driven-voting-design.md`. Plan 1–3 done state on `feat/quiz-driven-voting` (HEAD `d96e493` at time of writing). Existing user flow on this branch: `/vote/new/categories` → `/vote/:shareToken/activities` → `/vote/:shareToken/waiting` → `/vote/:shareToken/result`.

**Scope notes:**
- This plan ships **functional UI**, not pixel-perfect design. Follow existing styling conventions (`.app-modal`, `.contact-form`, `.trip-setup-description`). Polish is a separate effort.
- The admin Quiz tab is **MVP CRUD**: list questions + their answers + a category-weight matrix; load via `GET`, save the whole quiz via `PUT` (bulk replace). No drag-and-drop reordering, no inline reordering — sort-order is a numeric field. Drag-and-drop polish is a separate effort.
- `featured_weight` in admin: minimal — one numeric input on the activity edit form, one CSV column added end-of-row. Default `0`. CSV column is **optional** (absent column → default 0; importing pre-Plan-4 CSVs still works).
- No backwards-compat layer for `likedCategoryIds`; the field on `VoteSessionCreateRequest` is tolerated by the backend per spec line 328 but the frontend stops sending it.
- E2E tests are **out of scope** for Plan 4 — unit tests with React Testing Library for the new pages are enough. Spec line 454 lists an E2E happy-path; defer to QA/manual.

---

## File Structure

**New files (frontend):**
- `myhive-react-app/src/pages/vote/QuizPage.js` (+ `.css`) — works for both organizer (`/vote/new/quiz`) and participant (`/vote/:shareToken/quiz`) via the URL pattern
- `myhive-react-app/src/pages/vote/CuratePage.js` (+ `.css`)
- `myhive-react-app/src/utils/voterToken.js` — get-or-create UUID in `localStorage`
- `myhive-react-app/src/pages/admin/AdminDestinationQuiz.js` (+ `.css`) — Quiz tab content
- Tests: `QuizPage.test.js`, `CuratePage.test.js`, `voterToken.test.js`

**Modified files (frontend):**
- `myhive-react-app/src/services/voteApi.js` — new endpoints + reshape `createSession`/`getResult`
- `myhive-react-app/src/components/TripSetupModal.js` — add `budget` field, change vote-mode confirm to navigate to `/vote/new/quiz`
- `myhive-react-app/src/components/Layout.js` — drop `/vote/new/categories`, add `/vote/new/quiz`, `/vote/new/curate`, `/vote/:shareToken/quiz`
- `myhive-react-app/src/pages/vote/ActivityVotePage.js` — same shape, but reads curated list via existing endpoint (unchanged URL — the backend `getActivities` already serves curated for new sessions), persists `voterToken` from `localStorage`
- `myhive-react-app/src/pages/vote/VoteResultPage.js` — rewrite to consume new `VoteResultResponse` shape
- `myhive-react-app/src/pages/AdminActivities.js` — add `featuredWeight` to form + admin payload
- `myhive-react-app/src/pages/AdminDestinations.js` — add Quiz tab tab-switcher
- `myhive-react-app/src/services/adminApi.js` — new admin quiz endpoints + featuredWeight passthrough

**Deleted files (frontend):**
- `myhive-react-app/src/pages/vote/CategoryVotePage.js`

**Modified files (backend prep):**
- `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java` — add `featuredWeight` field
- `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java` — pass `featuredWeight` through `convertToDTO` and on update
- `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java` — emit `featured_weight` column
- `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java` — parse optional `featured_weight` column, default 0
- Existing tests: extend `ActivityServiceTest`, `ActivityCsvExporterTest`, `ActivityCsvImporterTest`

---

## Task 1: Backend prep — `ActivityDTO.featuredWeight`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java` (or `ActivityServiceTest` equivalent)

- [ ] **Step 1: Write the failing test**

In the existing `ActivityServiceTest` (Mockito unit test), add a test that verifies `featuredWeight` round-trips through the DTO. Pattern:

```java
    @Test
    void convertToDTO_includesFeaturedWeight() {
        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setName("Tank Driving");
        activity.setPrice(new BigDecimal("150"));
        activity.setFeaturedWeight(7);
        // Set required collaborators (destination, categories) — copy the existing pattern in this file.

        // Look up how the file already invokes convertToDTO / getActivity; mirror.
        // If the service exposes only public getActivity(id):
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        ActivityDTO dto = activityService.getActivity(activity.getId());

        assertThat(dto.getFeaturedWeight()).isEqualTo(7);
    }

    @Test
    void updateActivity_persistsFeaturedWeight() {
        // Mirror an existing update test in this file. Set dto.setFeaturedWeight(5) on the input
        // and assert the captured save has 5.
    }
```

If the existing test class doesn't already mock the repos in a way that supports this, mirror an adjacent test's setup verbatim.

- [ ] **Step 2: Run red**

`cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: FAIL — `getFeaturedWeight` / `setFeaturedWeight` not on `ActivityDTO`.

- [ ] **Step 3: Add the field to `ActivityDTO`**

Open `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java`. Add `private int featuredWeight;` (or `Integer` — match the surrounding style; the entity has `int featuredWeight = 0`, so `Integer` is safer for null-tolerance over JSON). Place it among the other numeric fields. Add `@JsonProperty("featured_weight")` ONLY if the file already uses Jackson naming annotations — otherwise rely on default `featuredWeight ↔ featuredWeight` JSON mapping.

- [ ] **Step 4: Wire through `ActivityService.convertToDTO` and the create/update paths**

Open `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`. In `convertToDTO(Activity)`, add `dto.setFeaturedWeight(activity.getFeaturedWeight());`. In `createActivity(ActivityDTO dto)` and `updateActivity(UUID, ActivityDTO dto)`, add `activity.setFeaturedWeight(dto.getFeaturedWeight());`.

If the file uses a builder or fluent setter pattern, follow it.

- [ ] **Step 5: Run green**

`./gradlew test --tests '*ActivityServiceTest'` — passes.
`./gradlew test` — full suite green.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java
git commit -m "feat: expose featuredWeight on ActivityDTO and admin create/update"
```

---

## Task 2: Backend prep — `featured_weight` in CSV export + import

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java`

Spec line 381: `featured_weight` is **mutable** (CSV updates can change it), **optional** (absent → default 0, so older exports still import).

- [ ] **Step 1: Read the existing CSV column order**

Before writing tests, open `ActivityCsvExporter.java` and `ActivityCsvImporter.java`. Note the existing column header line (likely something like `id,slug,name,description,price,duration,includes,category_slugs,destination_slug,image_url`). The new `featured_weight` column goes **at the end** so existing imports/exports keep their column order.

- [ ] **Step 2: Extend exporter test**

In `ActivityCsvExporterTest`, add a test asserting the new column header is present and that an Activity with `featuredWeight = 5` exports `5` in that column.

- [ ] **Step 3: Extend importer test**

In `ActivityCsvImporterTest`, add tests:
- Importing a CSV row that includes `featured_weight=7` updates the activity's `featuredWeight` to 7.
- Importing a CSV row whose header is missing the `featured_weight` column (legacy CSV) succeeds; the activity's `featuredWeight` stays whatever it was before (no overwrite).
- Importing a row whose value is empty defaults to 0 (or stays unchanged — pick the convention by reading how `price` handles empties and mirror it).

- [ ] **Step 4: Run red**

`./gradlew test --tests '*ActivityCsv*'` — fails.

- [ ] **Step 5: Implement**

Exporter: append `featured_weight` to the header list and `activity.getFeaturedWeight()` to each data row. Mirror existing CSV-quoting + formula-injection logic.

Importer: the file probably parses headers into an index map. Add `featured_weight` as an **optional** column — if present, parse to int; if absent (header missing) or value blank, leave the field alone on update. Crucially, do NOT add `featured_weight` to `REQUIRED_COLUMNS` — pre-Plan-4 CSVs must still import.

- [ ] **Step 6: Run green**

`./gradlew test --tests '*ActivityCsv*'` — passes.
`./gradlew test` — full suite green.

- [ ] **Step 7: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvExporter.java myhive-backend/src/main/java/com/myhive/backend/service/activity/ActivityCsvImporter.java myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvExporterTest.java myhive-backend/src/test/java/com/myhive/backend/service/activity/ActivityCsvImporterTest.java
git commit -m "feat: add optional featured_weight column to activity CSV"
```

---

## Task 3: Frontend — `voteApi.js` rewrite

**Files:**
- Modify: `myhive-react-app/src/services/voteApi.js`

Add the new endpoints and reshape `createSession`. Keep the old methods that still apply (`getSession`, `castVote`, `castVotes`, `getParticipantCount`, `closeSession`). Replace `createSession` to take the new shape; replace `getResult` to return the new two-tier shape (consumer change in Task 9).

- [ ] **Step 1: Rewrite the file**

Replace `myhive-react-app/src/services/voteApi.js` with:

```javascript
import { API_BASE_URL } from './config';

const voteApi = {
  // Public quiz fetch (organizer pre-session, by destinationId)
  async getPublicQuizForDestination(destinationId) {
    const response = await fetch(`${API_BASE_URL}/vote/destinations/${destinationId}/quiz`);
    if (response.status === 404) return { questions: [] };
    if (!response.ok) throw new Error('Failed to fetch quiz');
    return response.json();
  },

  // Build the pool (stateless, pre-session)
  async buildPool({ destinationId, responses }) {
    const response = await fetch(`${API_BASE_URL}/vote/pool`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ destinationId, responses }),
    });
    if (!response.ok) throw new Error('Failed to build pool');
    return response.json();
  },

  // Atomic session creation
  async createSession({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate,
                        budget, voterToken, quizResponses, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, numberOfTravelers, startDate, endDate,
        budget, voterToken, quizResponses, activityIds,
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  async getSession(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}`);
    if (!response.ok) throw new Error('Failed to fetch vote session');
    return response.json();
  },

  // Curated voting list (same URL as before — backend now serves curated for new sessions)
  async getActivities(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/activities`);
    if (!response.ok) throw new Error('Failed to fetch vote activities');
    return response.json();
  },

  // Participant quiz
  async getParticipantQuiz(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/quiz`);
    if (!response.ok) throw new Error('Failed to fetch participant quiz');
    return response.json();
  },

  async submitParticipantQuiz(shareToken, { voterToken, responses }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/quiz`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, responses }),
    });
    if (response.status === 409) throw new Error('Quiz already submitted');
    if (!response.ok) throw new Error('Failed to submit quiz');
  },

  async castVote(shareToken, { voterToken, activityId, liked }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/votes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, activityId, liked }),
    });
    if (response.status === 409) throw new Error('Session is full');
    if (!response.ok) throw new Error('Failed to cast vote');
  },

  async castVotes(shareToken, { voterToken, votes }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/votes/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, votes }),
    });
    if (response.status === 409) throw new Error('Session is full');
    if (!response.ok) throw new Error('Failed to cast votes');
  },

  async getParticipantCount(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/participant-count`);
    if (!response.ok) throw new Error('Failed to fetch participant count');
    return response.json();
  },

  async closeSession(shareToken, managerToken) {
    const url = managerToken
        ? `${API_BASE_URL}/vote/sessions/${shareToken}/close?managerToken=${managerToken}`
        : `${API_BASE_URL}/vote/sessions/${shareToken}/close`;
    const response = await fetch(url, { method: 'POST' });
    if (!response.ok && response.status !== 400) throw new Error('Failed to close session');
  },

  async getResult(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/result`);
    if (response.status === 409) throw new Error('Result not available yet');
    if (!response.ok) throw new Error('Failed to fetch vote result');
    return response.json();
  },
};

export default voteApi;
```

- [ ] **Step 2: Verify build (no separate test for the data layer)**

`cd myhive-react-app && npm run build` — succeeds. (CRA build runs without compile errors. The existing CategoryVotePage still references the old `createSession` shape — that's OK, it will be removed in Task 10. Until then, `CategoryVotePage.js` still passes `likedCategoryIds` and that's silently accepted by the backend. If the build fails because of TypeScript-style checks, that's a deeper issue — report.)

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/services/voteApi.js
git commit -m "feat: reshape voteApi for quiz, pool, and new result endpoints"
```

---

## Task 4: Frontend — `voterToken` localStorage helper

**Files:**
- Create: `myhive-react-app/src/utils/voterToken.js`
- Create: `myhive-react-app/src/utils/voterToken.test.js`

A `voterToken` is a stable UUID tied to one browser, used by both organizer and participants. Persist in `localStorage` under `myhive.voterToken`.

- [ ] **Step 1: Write the failing test**

`voterToken.test.js`:

```javascript
import { getOrCreateVoterToken } from './voterToken';

describe('getOrCreateVoterToken', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('creates and persists a UUID on first call', () => {
    const token = getOrCreateVoterToken();
    expect(token).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    expect(localStorage.getItem('myhive.voterToken')).toBe(token);
  });

  test('returns the same token on subsequent calls', () => {
    const first = getOrCreateVoterToken();
    const second = getOrCreateVoterToken();
    expect(second).toBe(first);
  });

  test('reuses an existing localStorage value', () => {
    const existing = '11111111-1111-4111-8111-111111111111';
    localStorage.setItem('myhive.voterToken', existing);
    expect(getOrCreateVoterToken()).toBe(existing);
  });
});
```

- [ ] **Step 2: Run red**

`cd myhive-react-app && npm test -- --watchAll=false --testPathPattern=voterToken`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement**

`voterToken.js`:

```javascript
const STORAGE_KEY = 'myhive.voterToken';

export function getOrCreateVoterToken() {
  let token = localStorage.getItem(STORAGE_KEY);
  if (!token) {
    token = crypto.randomUUID();
    localStorage.setItem(STORAGE_KEY, token);
  }
  return token;
}
```

- [ ] **Step 4: Run green**

`npm test -- --watchAll=false --testPathPattern=voterToken` — passes.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/utils/voterToken.js myhive-react-app/src/utils/voterToken.test.js
git commit -m "feat: add voterToken localStorage helper"
```

---

## Task 5: Frontend — `TripSetupModal` gets a budget field and navigates to `/vote/new/quiz`

**Files:**
- Modify: `myhive-react-app/src/components/TripSetupModal.js`

The current vote-mode confirm callback receives `{ travelers, startDate, endDate, email, destination }`. Add `budget` (a non-empty positive number, or `null` if the user leaves it blank). Also: the existing caller of TripSetupModal in vote mode (somewhere in HomePage / Header / a dropdown — the modal currently fires `onVoteConfirm` and the caller does `voteApi.createSession`) needs to change too — instead of creating the session, the caller should navigate to `/vote/new/quiz`, carrying `{ travelers, startDate, endDate, email, destination, budget }` as **router state** (`useNavigate` with `state:`).

Tip: search for the existing caller of `onVoteConfirm` in the repo (`grep -rn 'onVoteConfirm' myhive-react-app/src`). The caller is responsible for the navigation now. Move the create-session logic OUT of the caller — leave a TODO comment "Setup data → /vote/new/quiz, session created on /vote/new/curate" so the next task picks it up.

- [ ] **Step 1: Add the budget field**

In `TripSetupModal.js`, add a `budget` state (`''` initial — empty string means "no budget"). Add a labelled `<input type="number" min="0" step="100">` to the vote-mode form. Validation: numeric and non-negative. If left blank, pass `null`.

```javascript
const [budget, setBudget] = useState('');
// …
const handleConfirm = () => {
    // … existing validations
    const budgetValue = budget.trim() === '' ? null : Number(budget);
    if (budgetValue !== null && (!Number.isFinite(budgetValue) || budgetValue <= 0)) {
        return;   // invalid budget — keep the modal open; ideally show inline error
    }
    if (isVoteMode) {
        onVoteConfirm({ travelers: travelersNum, startDate, endDate, email, destination, budget: budgetValue });
    }
    // …
};
```

Add the field to the JSX inside the vote-mode form section (where the destination picker / email / dates live).

- [ ] **Step 2: Update the caller(s) of `onVoteConfirm`**

`grep -rn 'onVoteConfirm' myhive-react-app/src` — locate every caller. For each:
- Remove the inline `voteApi.createSession(...)` call.
- Add `const navigate = useNavigate();` (from `react-router-dom`) if not already imported.
- On `onVoteConfirm({ ... })`, call `navigate('/vote/new/quiz', { state: { setup: { travelers, startDate, endDate, email, destination, budget } } });`.
- Drop now-unused imports (`voteApi`, etc.).

- [ ] **Step 3: Test the modal field change manually**

Run `npm start`, open the vote modal, confirm the budget field shows up and accepts numeric input. (Optional: add a small RTL test asserting the budget input renders.)

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/components/TripSetupModal.js
# also stage caller file(s) found in Step 2
git commit -m "feat: TripSetupModal collects budget, navigates to /vote/new/quiz"
```

---

## Task 6: Frontend — `/vote/new/quiz` (organizer quiz page)

**Files:**
- Create: `myhive-react-app/src/pages/vote/QuizPage.js`
- Create: `myhive-react-app/src/pages/vote/QuizPage.css` (basic, follow existing CSS file naming)
- Create: `myhive-react-app/src/pages/vote/QuizPage.test.js`

This page handles BOTH organizer and participant — distinguished by route. The organizer flow lands here with router state from Task 5; the participant flow lands here from a share link, with no router state. The page reads either `destinationId` from `location.state.setup.destination.id` (organizer) or `shareToken` from `useParams()` (participant).

- [ ] **Step 1: Sketch the component**

`QuizPage.js`:

```javascript
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import './QuizPage.css';

export default function QuizPage() {
  const { shareToken } = useParams();   // present for participants only
  const location = useLocation();
  const navigate = useNavigate();
  const setup = location.state?.setup;    // present for organizer only

  const [quiz, setQuiz] = useState(null);
  const [stepIndex, setStepIndex] = useState(0);
  const [responses, setResponses] = useState([]);   // [{ questionId, answerId }]
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const isOrganizer = !shareToken;

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const data = isOrganizer
          ? await voteApi.getPublicQuizForDestination(setup.destination.id)
          : await voteApi.getParticipantQuiz(shareToken);
        if (cancelled) return;
        if (!data.questions || data.questions.length === 0) {
          // No quiz configured — skip straight to the next step.
          if (isOrganizer) {
            navigate('/vote/new/curate', { state: { setup, responses: [] } });
          } else {
            navigate(`/vote/${shareToken}/activities`);
          }
          return;
        }
        setQuiz(data);
      } catch (e) {
        if (!cancelled) setError(e.message || 'Failed to load quiz');
      }
    }
    if (isOrganizer && !setup) {
      navigate('/');                   // fell into the route without setup state
      return;
    }
    load();
    return () => { cancelled = true; };
  }, [isOrganizer, setup, shareToken, navigate]);

  if (error) return <div className="quiz-page-error">{error}</div>;
  if (!quiz) return <div className="quiz-page-loading">Loading quiz…</div>;

  const question = quiz.questions[stepIndex];

  const pickAnswer = async (answerId) => {
    const updated = [...responses, { questionId: question.id, answerId }];
    setResponses(updated);
    if (stepIndex + 1 < quiz.questions.length) {
      setStepIndex(stepIndex + 1);
      return;
    }
    // Last question — proceed.
    if (isOrganizer) {
      navigate('/vote/new/curate', { state: { setup, responses: updated } });
    } else {
      setSubmitting(true);
      try {
        await voteApi.submitParticipantQuiz(shareToken, {
          voterToken: getOrCreateVoterToken(),
          responses: updated,
        });
        navigate(`/vote/${shareToken}/activities`);
      } catch (e) {
        setError(e.message);
      } finally {
        setSubmitting(false);
      }
    }
  };

  return (
    <div className="quiz-page">
      <div className="quiz-progress">{stepIndex + 1} / {quiz.questions.length}</div>
      <h2 className="quiz-prompt">{question.prompt}</h2>
      <div className="quiz-answers">
        {question.answers.map(a => (
          <button
            key={a.id}
            className="quiz-answer"
            disabled={submitting}
            onClick={() => pickAnswer(a.id)}
          >
            {a.label}
          </button>
        ))}
      </div>
    </div>
  );
}
```

`QuizPage.css` — minimal. A `.quiz-page` container, large prompt, stacked answer buttons. Mirror existing button styles from `App.css`.

- [ ] **Step 2: Test**

`QuizPage.test.js`:

```javascript
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import QuizPage from './QuizPage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function renderOrganizer(setup) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/vote/new/quiz', state: { setup } }]}>
      <Routes>
        <Route path="/vote/new/quiz" element={<QuizPage />} />
        <Route path="/vote/new/curate" element={<div>curate page</div>} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

test('organizer: loads quiz, answers questions, navigates to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({
    questions: [
      { id: 'q1', prompt: 'Daytime or 4am?', answers: [{ id: 'a1', label: 'Daytime' }, { id: 'a2', label: '4am' }] },
    ],
  });
  renderOrganizer({ destination: { id: 'dest1' }, travelers: 2, startDate: '2026-08-01', endDate: '2026-08-10', email: 'a@b.c', budget: 3000 });

  expect(await screen.findByText('Daytime or 4am?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('4am'));

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});

test('organizer: empty quiz auto-skips to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({ questions: [] });
  renderOrganizer({ destination: { id: 'dest1' } });

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});
```

- [ ] **Step 3: Run red, then green**

`npm test -- --watchAll=false --testPathPattern=QuizPage`

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/vote/QuizPage.js myhive-react-app/src/pages/vote/QuizPage.css myhive-react-app/src/pages/vote/QuizPage.test.js
git commit -m "feat: add QuizPage for organizer and participant"
```

---

## Task 7: Frontend — `/vote/new/curate` (organizer curation page)

**Files:**
- Create: `myhive-react-app/src/pages/vote/CuratePage.js`
- Create: `myhive-react-app/src/pages/vote/CuratePage.css`
- Create: `myhive-react-app/src/pages/vote/CuratePage.test.js`

Loads the pool via `voteApi.buildPool({ destinationId, responses })`, shows a grid of ≤20 activities with `[Add] / [Remove]` toggle, a running "Voting list (N)" tray, and a `[Create & get link]` button that fires `voteApi.createSession(...)` and navigates to `/vote/:shareToken/waiting`.

- [ ] **Step 1: Component**

`CuratePage.js`:

```javascript
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import './CuratePage.css';

export default function CuratePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const setup = location.state?.setup;
  const responses = location.state?.responses ?? [];

  const [pool, setPool] = useState(null);
  const [picked, setPicked] = useState(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!setup) {
      navigate('/');
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const data = await voteApi.buildPool({ destinationId: setup.destination.id, responses });
        if (!cancelled) setPool(data.pool);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [setup, responses, navigate]);

  const pickedCount = picked.size;
  const togglePick = (id) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id); else next.add(id);
    setPicked(next);
  };

  const handleCreate = async () => {
    if (pickedCount === 0 || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const session = await voteApi.createSession({
        destinationId: setup.destination.id,
        initiatorEmail: setup.email,
        numberOfTravelers: setup.travelers,
        startDate: setup.startDate,
        endDate: setup.endDate,
        budget: setup.budget,
        voterToken: getOrCreateVoterToken(),
        quizResponses: responses,
        activityIds: Array.from(picked),
      });
      navigate(`/vote/${session.shareToken}/waiting`, { state: { managerToken: session.managerToken } });
    } catch (e) {
      setError(e.message);
      setSubmitting(false);
    }
  };

  if (error) return <div className="curate-page-error">{error}</div>;
  if (!pool) return <div className="curate-page-loading">Loading pool…</div>;

  return (
    <div className="curate-page">
      <h1>Pick activities for the vote</h1>
      <p className="curate-subtitle">Tray: {pickedCount} selected</p>
      <div className="curate-grid">
        {pool.map(a => (
          <div key={a.activityId} className={`curate-card ${picked.has(a.activityId) ? 'picked' : ''}`}>
            {a.imageUrl && <img src={a.imageUrl} alt={a.name} />}
            <div className="curate-card-body">
              <h3>{a.name}</h3>
              <p>{a.price} per person</p>
              {a.categories?.length > 0 && <p className="curate-card-cats">{a.categories.join(' · ')}</p>}
              <button onClick={() => togglePick(a.activityId)}>
                {picked.has(a.activityId) ? 'Remove' : 'Add'}
              </button>
            </div>
          </div>
        ))}
      </div>
      <button
        className="curate-create-btn"
        disabled={pickedCount === 0 || submitting}
        onClick={handleCreate}
      >
        {submitting ? 'Creating…' : 'Create & get link'}
      </button>
    </div>
  );
}
```

`CuratePage.css` — basic grid layout. Reuse `.activity-card`-style classes from existing pages if they exist.

- [ ] **Step 2: Test**

```javascript
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

const setupState = {
  destination: { id: 'dest1' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

test('curate: picks an activity and creates a session', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, categories: ['Chillout'] },
    ],
  });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-abc', managerToken: 'mgr-xyz' });

  render(
    <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state: { setup: setupState, responses: [] } }]}>
      <Routes>
        <Route path="/vote/new/curate" element={<CuratePage />} />
        <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
      </Routes>
    </MemoryRouter>
  );

  expect(await screen.findByText('Tank Driving')).toBeInTheDocument();
  await userEvent.click(screen.getAllByText('Add')[0]);
  await userEvent.click(screen.getByText('Create & get link'));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalled());
  const arg = voteApi.createSession.mock.calls[0][0];
  expect(arg.activityIds).toEqual(['act1']);
  expect(arg.budget).toBe(3000);
  expect(await screen.findByText('waiting page')).toBeInTheDocument();
});
```

- [ ] **Step 3: Run red/green**

`npm test -- --watchAll=false --testPathPattern=CuratePage`

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/vote/CuratePage.js myhive-react-app/src/pages/vote/CuratePage.css myhive-react-app/src/pages/vote/CuratePage.test.js
git commit -m "feat: add CuratePage with pool fetch and session creation"
```

---

## Task 8: Frontend — `ActivityVotePage` passes `voterToken`, removes liked-category logic

**Files:**
- Modify: `myhive-react-app/src/pages/vote/ActivityVotePage.js`

The backend endpoint `GET /vote/sessions/{shareToken}/activities` now returns the curated list (for new sessions) or the legacy category list (for old sessions) transparently. So the page mostly works — but it must pass `voterToken` from `localStorage` (Task 4 helper) on every `castVote` / `castVotes` call.

- [ ] **Step 1: Read the existing file**

Note where `voterToken` currently comes from (probably a `useState` with a fresh `crypto.randomUUID()` per visit, which is wrong for the new flow because organizer revisiting the page would be a different voter).

- [ ] **Step 2: Replace voterToken source**

At the top of the page:

```javascript
import { getOrCreateVoterToken } from '../../utils/voterToken';
// …
const voterToken = useMemo(() => getOrCreateVoterToken(), []);
```

Pass this `voterToken` to all `voteApi.castVote` / `castVotes` calls.

If the page also called `getOrCreate`-type logic on its own, remove it.

- [ ] **Step 3: Smoke-test manually + run existing tests**

`npm test -- --watchAll=false --testPathPattern=ActivityVotePage` if a test exists.

Manual: with the dev backend running, hit `/vote/<shareToken>/activities` on a fresh session, verify activities render and votes persist (check H2 console or by re-loading the page).

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/vote/ActivityVotePage.js
git commit -m "refactor: ActivityVotePage uses persistent voterToken"
```

---

## Task 9: Frontend — `/vote/:shareToken/result` rewrite

**Files:**
- Modify: `myhive-react-app/src/pages/vote/VoteResultPage.js`
- Modify: any associated CSS file

New `VoteResultResponse` shape from Plan 3:
```json
{
  "result": [{ "activityId", "name", "price", "likeCount", "skipCount" }],
  "suggestions": [{ "activityId", "name", "price", "imageUrl", "categories" }],
  "numberOfTravelers": 2,
  "totalPrice": 300.00,
  "budget": 3000.00,
  "remaining": 2700.00
}
```

- [ ] **Step 1: Read the existing page**

Note its current UI (likely just lists `result.activities` with `totalPrice`).

- [ ] **Step 2: Rewrite to three blocks**

Three sections, in order:

1. **Voted result** — for each `result[]` row: name, snapshot price, `🤍 likeCount · 💔 skipCount` (or text equivalents — match existing icon/label conventions). If empty: a short empathic line ("Group didn't agree on anything within budget — try the suggestions below").
2. **Budget summary** — if `budget != null`: `Spent: <totalPrice> · Budget: <budget> · Remaining: <remaining>`. If `budget == null`: just `Spent: <totalPrice>` and skip the budget/remaining line. Negative `remaining` is allowed (the soft-budget semantic per spec) — render with a warning-coloured label.
3. **Suggestions** — for each `suggestions[]` row: image, name, live price, categories, an `[Add to trip]` button. The button updates local UI only (appends to local `addedSuggestions` state and updates a running `displayedTotal`); persistence/checkout is a separate concern outside Plan 4's scope. **Note in the code with a TODO comment** that booking integration comes later.

Empty `suggestions` → omit the section.

- [ ] **Step 3: Manual test**

Boot backend, create a session via the new flow, vote, close, hit the result page, verify the new payload renders correctly.

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/vote/VoteResultPage.js
git commit -m "feat: VoteResultPage renders two-tier result + budget + suggestions"
```

---

## Task 10: Frontend — delete `CategoryVotePage`, swap routes in `Layout.js`

**Files:**
- Delete: `myhive-react-app/src/pages/vote/CategoryVotePage.js`
- Modify: `myhive-react-app/src/components/Layout.js`

- [ ] **Step 1: Confirm no remaining references**

`grep -rn 'CategoryVotePage\|/vote/new/categories' myhive-react-app/src`

Expected: only `Layout.js` (route registration) and the file itself.

- [ ] **Step 2: Update routes**

In `Layout.js`, remove:
```javascript
import CategoryVotePage from '../pages/vote/CategoryVotePage';
// …
<Route path="/vote/new/categories" element={<CategoryVotePage />} />
```

Add:
```javascript
import QuizPage from '../pages/vote/QuizPage';
import CuratePage from '../pages/vote/CuratePage';
// …
<Route path="/vote/new/quiz" element={<QuizPage />} />
<Route path="/vote/new/curate" element={<CuratePage />} />
<Route path="/vote/:shareToken/quiz" element={<QuizPage />} />
```

- [ ] **Step 3: Delete the old file**

```bash
git rm myhive-react-app/src/pages/vote/CategoryVotePage.js
# also remove its CSS file if one exists
```

- [ ] **Step 4: Build and smoke-test**

`npm run build` — succeeds.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/Layout.js
git commit -m "refactor: replace CategoryVotePage with QuizPage and CuratePage routes"
```

---

## Task 11: Frontend admin — `featured_weight` in activity edit form

**Files:**
- Modify: `myhive-react-app/src/pages/AdminActivities.js`
- Modify: `myhive-react-app/src/services/adminApi.js` (if the create/update payload helpers exist there)

- [ ] **Step 1: Add the field to the form**

Find the activity edit / create form. Add a numeric input labelled `Featured weight` with a placeholder "0" and `min="0"`. Bind to a local state `featuredWeight` (default `0`). Include it in the form payload that goes to `POST /admin/activities` / `PUT /admin/activities/{id}`.

- [ ] **Step 2: Display the value in the activity list**

If the admin activity list has a table, add a `Weight` column to make the field visible (useful for the business to see "which activities are surfacing"). If the list is card-style and adding a column would be intrusive, skip the list change — the edit form alone is enough for MVP.

- [ ] **Step 3: Smoke test**

`npm start`, log in as admin, edit an activity, set featured weight to 5, save, reload, confirm 5 persists.

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/AdminActivities.js
# also adminApi.js if you touched it
git commit -m "feat: admin activity form edits featured_weight"
```

---

## Task 12: Frontend admin — Destinations "Quiz" tab

**Files:**
- Create: `myhive-react-app/src/pages/admin/AdminDestinationQuiz.js`
- Create: `myhive-react-app/src/pages/admin/AdminDestinationQuiz.css`
- Modify: `myhive-react-app/src/pages/AdminDestinations.js` (add Quiz tab switcher)
- Modify: `myhive-react-app/src/services/adminApi.js`

The admin Quiz UI is a single-page edit form: list of questions, each with its own list of answers, each answer with a category-weight matrix. **Bulk-replace semantics**: load the whole quiz on mount via `GET /admin/destinations/{id}/quiz`; on save, `PUT` the whole quiz back. No granular endpoints used (those aren't implemented anyway, per Plan 1 scope).

- [ ] **Step 1: Extend `adminApi.js`**

Add:
```javascript
  async getDestinationQuiz(destinationId) {
    const response = await fetchWithAuth(`${API_BASE_URL}/admin/destinations/${destinationId}/quiz`);
    if (!response.ok) throw new Error('Failed to fetch quiz');
    return response.json();
  },

  async putDestinationQuiz(destinationId, quizDto) {
    const response = await fetchWithAuth(`${API_BASE_URL}/admin/destinations/${destinationId}/quiz`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(quizDto),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to save quiz');
    }
    return response.json();
  },
```

Match the existing `fetchWithAuth` helper convention from this file. If the file uses a different auth wrapper, mirror it.

- [ ] **Step 2: Create `AdminDestinationQuiz.js`**

State shape mirrors `QuizDTO`:
```javascript
{ questions: [
  { id: 'uuid|null',
    prompt: 'text',
    sortOrder: 0,
    answers: [
      { id: 'uuid|null', label: 'text', sortOrder: 0,
        weights: [ { categoryId: 'uuid', weight: 2 } ] }
    ]
  }
]}
```

UI:
- "Add question" button at the top → appends a blank question with id `null`.
- For each question card: prompt text input, sort_order number input, "Add answer" button, "Delete question" button.
- For each answer row inside a question: label input, sort_order number input, "Delete answer" button, weights matrix.
- Weights matrix: a list of `[category dropdown] [weight number input]` rows, with an "Add weight" button.
- Bottom "Save quiz" button calls `putDestinationQuiz`.

Categories for the dropdowns load via `adminApi.getCategories()` (which already exists for the admin categories page — verify with grep, otherwise add).

This UI is **functional, not pretty**. Form-style, scrollable, save button at bottom. Use existing `admin-form-*` CSS classes if they exist.

- [ ] **Step 3: Wire the tab into `AdminDestinations.js`**

The destinations admin page likely has a detail view (when an admin clicks a destination). Add a tab switcher with at least two tabs: "Details" (current content) and "Quiz" (the new component). If the page is a flat list with no per-destination detail view, skip the tab UX and add a "Edit quiz" link that routes to `/admin/destinations/{id}/quiz` — and add that route to wherever admin routes live.

- [ ] **Step 4: Smoke test**

Boot dev backend (which seeds a Prague quiz from Plan 1 Task 7). Open `/admin/destinations/{prague-id}/quiz`. Verify:
- The 2 seeded questions render with their answers.
- Editing a prompt and saving persists (reload to confirm).
- Adding a new question + answer + weight, saving, then reloading shows the new structure.
- Deleting a question and saving removes it.

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/pages/admin/AdminDestinationQuiz.js myhive-react-app/src/pages/admin/AdminDestinationQuiz.css myhive-react-app/src/pages/AdminDestinations.js myhive-react-app/src/services/adminApi.js
git commit -m "feat: admin destinations Quiz tab with bulk replace"
```

---

## Self-Review

**Spec coverage (Plan 4 portion):**
- `TripSetupModal` budget field — Task 5 ✓
- `/vote/new/quiz` organizer + `/vote/:shareToken/quiz` participant — Task 6 ✓
- `/vote/new/curate` — Task 7 ✓
- `ActivityVotePage` with persistent `voterToken`, votes curated list — Task 8 ✓
- New `/vote/:shareToken/result` shape — Task 9 ✓
- `CategoryVotePage` removed, route swapped — Task 10 ✓
- Activity admin `featured_weight` field — Task 1 + Task 11 ✓
- Activity CSV `featured_weight` column (optional) — Task 2 ✓
- Destinations admin "Quiz" tab — Task 12 ✓
- **Deferred** (separate effort, NOT in Plan 4): inline drag-and-drop reordering in the admin Quiz UI; quiz-result-page checkout integration (suggestions → booking flow); E2E test.

**Placeholder scan:** the only deliberate placeholders are "look at the existing CSS/auth-helper conventions and mirror them" — those are environment lookups, not logic placeholders. The frontend code samples are illustrative; the implementer can adapt to existing styling patterns. No TBD/TODO blocks remain in implementation steps; TODO comments inside Task 9's suggestion-Add button + Task 12's "no granular CRUD" are intentional scope-pinning markers.

**Type consistency:**
- `voteApi.createSession(...)` arguments in Task 3 match what `CuratePage.handleCreate` calls in Task 7.
- `voteApi.getResult(...)` return shape in Task 3 matches what Task 9's result page consumes.
- `getOrCreateVoterToken()` defined in Task 4, used in Tasks 6 (participant quiz), 7 (organizer session create), 8 (ActivityVotePage).
- Router state `{ setup: {...}, responses: [...] }` flows: Task 5 → Task 6 (organizer reads `setup`) → Task 6 → Task 7 (`setup + responses`). Shape matches.
- `featuredWeight` field naming: backend uses camelCase `featuredWeight` in DTO (per existing conventions), frontend reads `featuredWeight` in JSON. CSV column is snake_case `featured_weight` (per existing CSV header style).

Consistent across all tasks.

---

## Suggested execution order

Tasks 1+2 (backend prep) → Task 3 (api wiring) → Task 4 (voterToken) → Task 5 (modal) → Task 6 (quiz page) → Task 7 (curate) → Task 8 (vote page) → Task 9 (result) → Task 10 (routes) → Task 11+12 (admin).

After Task 10 the user-facing flow is end-to-end testable manually. Admin tasks 11+12 can ship before the user flow if it makes for an easier dev cycle (the admin tab loads the seeded Prague quiz, demonstrating the GET/PUT cycle works before the user-facing UI consumes it).
