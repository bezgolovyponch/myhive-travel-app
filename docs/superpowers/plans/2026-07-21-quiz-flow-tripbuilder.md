# Quiz Flow → Trip Builder Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant post-swipe finalize screen from the organizer quiz flow; land the organizer directly in the Trip Builder with the picked activities, quiz-based recommendations, a Start Over button, and a one-click vote-creation button — preserving every analytics event.

**Architecture:** CuratePage, after the last swipe, seeds `TripContext`, persists a `{setup, responses}` quiz-flow context in sessionStorage, and `replace`-navigates to the destination Trip Builder. TripBuilder reads that context (active only when the destination id matches) and conditionally renders quiz-mode UI. Spec: `docs/superpowers/specs/2026-07-21-quiz-flow-tripbuilder-design.md`.

**Tech Stack:** React 19 (CRA), react-router v6+, Jest + React Testing Library. No backend changes.

## Global Constraints

- All commands run from `myhive-react-app/` (`cd myhive-react-app` first).
- Test command form: `npm test -- --watchAll=false --testPathPattern=<pattern>` (CRA react-scripts; `--watchAll=false` forces a single run).
- CRA sets Jest `resetMocks: true` — mock return values MUST be (re)assigned in `beforeEach`, never only at module scope.
- sessionStorage key for the quiz-flow context: literally `'myhive-quiz-flow'`. vote_skipped dedup key: `` `myhive-vote-skipped-${tripId}` ``.
- Exact UI copy: buttons `Start Over`, `Complete Booking`, `Let your mates vote`; section heading `Recommended for you`, subheading `Based on your quiz answers`.
- Analytics payloads must keep their exact current field names: `shortlist_completed {selected_count}`, `vote_launched {trip_id, user_role, selected_count}`, `vote_skipped {trip_id, selected_count}`, `cta_click {cta_label, block}`.
- No wildcard imports. Braces always with `if/else`. Match surrounding code style (2-space indent in `src/pages/vote/`, 4-space in `TripBuilder.js` — follow each file's existing indent).
- Commit after every task (Windows PowerShell: use here-strings or single-line `-m` messages).

---

### Task 1: quizFlow sessionStorage util

**Files:**
- Create: `myhive-react-app/src/utils/quizFlow.js`
- Test: `myhive-react-app/src/utils/quizFlow.test.js`

**Interfaces:**
- Consumes: nothing.
- Produces: `readQuizFlow(): {setup, responses} | null`, `writeQuizFlow(context: {setup, responses}): void`, `clearQuizFlow(): void`. Later tasks import these by name from `'../utils/quizFlow'` (TripBuilder) / `'../../utils/quizFlow'` (CuratePage).

- [ ] **Step 1: Write the failing test**

Create `myhive-react-app/src/utils/quizFlow.test.js`:

```js
import { clearQuizFlow, readQuizFlow, writeQuizFlow } from './quizFlow';

const context = {
  setup: {
    destination: { id: 'dest-1', slug: 'prague' },
    travelers: 4,
    startDate: '2026-09-01',
    endDate: '2026-09-03',
    email: 'organizer@example.com',
    budget: 2000,
  },
  responses: [{ questionId: 'q1', answerId: 'a1' }],
};

beforeEach(() => {
  sessionStorage.clear();
});

test('write/read round-trips the context', () => {
  writeQuizFlow(context);
  expect(readQuizFlow()).toEqual(context);
});

test('read returns null when nothing is stored', () => {
  expect(readQuizFlow()).toBeNull();
});

test('clear removes the stored context', () => {
  writeQuizFlow(context);
  clearQuizFlow();
  expect(readQuizFlow()).toBeNull();
});

test('read tolerates malformed JSON', () => {
  sessionStorage.setItem('myhive-quiz-flow', '{not json');
  expect(readQuizFlow()).toBeNull();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=quizFlow`
Expected: FAIL — `Cannot find module './quizFlow'`

- [ ] **Step 3: Write minimal implementation**

Create `myhive-react-app/src/utils/quizFlow.js`:

```js
// Per-tab handoff between the organizer quiz flow (CuratePage) and the Trip
// Builder: {setup, responses} survives a refresh but not a new tab.
const QUIZ_FLOW_KEY = 'myhive-quiz-flow';

export function readQuizFlow() {
  try {
    const raw = sessionStorage.getItem(QUIZ_FLOW_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    // Malformed storage — treat as absent rather than crash the Trip Builder.
    return null;
  }
}

export function writeQuizFlow(context) {
  sessionStorage.setItem(QUIZ_FLOW_KEY, JSON.stringify(context));
}

export function clearQuizFlow() {
  sessionStorage.removeItem(QUIZ_FLOW_KEY);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=quizFlow`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/utils/quizFlow.js myhive-react-app/src/utils/quizFlow.test.js
git commit -m "feat(vote): quiz-flow sessionStorage handoff util"
```

---

### Task 2: CuratePage — auto-handoff to the Trip Builder

**Files:**
- Modify: `myhive-react-app/src/pages/vote/CuratePage.js` (full rewrite below)
- Modify: `myhive-react-app/src/pages/vote/CuratePage.css` (trim unused finalize styles)
- Test: `myhive-react-app/src/pages/vote/CuratePage.test.js` (rewrite)

**Interfaces:**
- Consumes: `writeQuizFlow` from Task 1.
- Produces: on deck completion with ≥1 pick, sessionStorage `myhive-quiz-flow` contains `{setup, responses}` and the app is at `/destination/<setup.destination.slug>?tab=trip-builder` via `replace: true`. TripContext receives `UPDATE_TRIP_TRAVELERS`, `UPDATE_TRIP_DATES`, `UPDATE_TRIP_BUDGET`, and one silent `ADD_TO_TRIP` per picked activity (activity fields: `id, name, price, slug, destinationSlug, imageUrl, categories: [{name}]`).

- [ ] **Step 1: Rewrite the test file**

Replace the entire content of `myhive-react-app/src/pages/vote/CuratePage.test.js` with:

```js
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';
import {TripContext} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

beforeEach(() => {
  sessionStorage.clear();
});

const setup = {
  destination: { id: 'dest1', slug: 'bali' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

// Destination stub exposing its location (to assert the handoff URL) and a
// back button (to assert the replace-navigation killed the deck entry).
function DestinationStub() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <div>
      destination page
      <div data-testid="dest-location">{location.pathname + location.search}</div>
      <button type="button" onClick={() => navigate(-1)}>go back</button>
    </div>
  );
}

function renderWith(state, dispatch = jest.fn()) {
  return render(
    <TripContext.Provider value={{ state: { tripItems: [] }, dispatch }}>
      <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state }]}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/destination/:slug" element={<DestinationStub />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </TripContext.Provider>
  );
}

const pool = [
  { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
  { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: ['Chillout'] },
];

test('last swipe seeds the trip, stores the quiz-flow context, and lands in the trip builder', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  expect(screen.getByTestId('dest-location')).toHaveTextContent('/destination/bali?tab=trip-builder');

  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 2 });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '2026-08-01', endDate: '2026-08-10' });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_BUDGET', budget: 3000 });

  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].silent).toBe(true);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act1', name: 'Tank Driving', categories: [{ name: 'Extreme' }] });

  expect(JSON.parse(sessionStorage.getItem('myhive-quiz-flow'))).toEqual({ setup, responses: [] });
});

test('A11: shortlist_completed fires exactly once with the picked count', async () => {
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Like'));
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Dislike'));

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 1 });
});

test('undo drops the pick — only the re-swiped selection reaches the trip', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));            // accidentally include act1
  await userEvent.click(screen.getByLabelText('Undo last swipe')); // take it back
  await userEvent.click(screen.getByLabelText('Dislike'));         // skip act1 this time
  await userEvent.click(screen.getByLabelText('Like'));            // include act2

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act2', name: 'Spa Day' });
});

test('back from the trip builder does not return to the spent deck (replace navigation)', async () => {
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));
  await userEvent.click(screen.getByLabelText('Dislike'));

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /go back/i }));

  // The curate entry was replaced — back cannot land on the deck again.
  expect(screen.getByText('destination page')).toBeInTheDocument();
  expect(screen.queryByLabelText('Like')).not.toBeInTheDocument();
});

test('zero picks stays on the page, offers a restart, and re-fires analytics after the redo', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Dislike')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip everything

  expect(await screen.findByText(/You didn't pick anything/i)).toBeInTheDocument();
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 0 });
  expect(screen.queryByText('destination page')).not.toBeInTheDocument();
  expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
  expect(dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP')).toHaveLength(0);

  await userEvent.click(screen.getByRole('button', { name: /Start over/i }));
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();

  await userEvent.click(screen.getByLabelText('Like'));      // pick this time
  expect(await screen.findByText('destination page')).toBeInTheDocument();
  // Fresh completion → the event fired a second time.
  expect(pushEvent.mock.calls.filter(([e]) => e === 'shortlist_completed')).toHaveLength(2);
});

test('no setup state redirects home', async () => {
  render(
    <TripContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
      <MemoryRouter initialEntries={['/vote/new/curate']}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </TripContext.Provider>
  );
  expect(await screen.findByText('home')).toBeInTheDocument();
});

test('empty pool shows empty-state message', async () => {
  voteApi.buildPool.mockResolvedValue({ pool: [] });
  renderWith({ setup, responses: [] });
  expect(await screen.findByText(/no activities match/i)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=CuratePage`
Expected: FAIL — the first five tests fail (the finalize screen still renders; no navigation, no sessionStorage write). `no setup`/`empty pool` tests pass.

- [ ] **Step 3: Rewrite CuratePage.js**

Replace the entire content of `myhive-react-app/src/pages/vote/CuratePage.js` with:

```js
import {useEffect, useMemo, useRef, useState} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';
import {useTrip} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';
import { writeQuizFlow } from '../../utils/quizFlow';
import VoteMeta from './VoteMeta';
import './CuratePage.css';

function CurateContent() {
  const location = useLocation();
  const navigate = useNavigate();
  const {dispatch} = useTrip();
  const setup = location.state?.setup;
  // Stable reference so the effects below don't re-run on every render
  // (the ?? [] fallback would otherwise be a fresh array each time).
  const responses = useMemo(() => location.state?.responses ?? [], [location.state]);

  const [pool, setPool] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [error, setError] = useState(null);
  const pickedRef = useRef([]);
  // Once-guard for the deck-completion handoff (A11 analytics + navigation).
  // Reset whenever the deck restarts (currentIndex back to 0 on start-over).
  const completionHandledRef = useRef(false);

  useEffect(() => {
    if (!setup) {
      navigate('/');
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const data = await voteApi.buildPool({
          destinationId: setup.destination.id,
          responses,
        });
        if (cancelled) {
          return;
        }
        // SwipeCard expects card.id; the pool DTO ships activityId. Remap.
        const mapped = (data.pool || []).map(a => ({ ...a, id: a.activityId }));
        setPool(mapped);
      } catch (e) {
        if (!cancelled) {
          setError(e.message);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [setup, responses, navigate]);

  const isComplete = pool !== null && pool.length > 0 && currentIndex >= pool.length;

  useEffect(() => {
    if (currentIndex === 0) {
      completionHandledRef.current = false;
    }
  }, [currentIndex]);

  // Deck exhausted: fire A11 shortlist_completed once, and with ≥1 pick seed
  // the trip and land the organizer straight in the Trip Builder (the old
  // finalize screen is gone). replace:true so the browser Back button returns
  // to the quiz — a spent deck would be a dead end.
  useEffect(() => {
    if (!isComplete || completionHandledRef.current) {
      return;
    }
    completionHandledRef.current = true;
    const picked = pool.filter(a => pickedRef.current.includes(a.id));
    pushEvent('shortlist_completed', { selected_count: picked.length });
    if (picked.length === 0) {
      return; // no picks — stay here and offer a restart
    }
    dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: setup.travelers });
    dispatch({
      type: 'UPDATE_TRIP_DATES',
      startDate: setup.startDate ?? '',
      endDate: setup.endDate ?? '',
    });
    dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: setup.budget ?? null });
    picked.forEach(a => {
      dispatch({
        type: 'ADD_TO_TRIP',
        silent: true,
        activity: {
          id: a.id,
          name: a.name,
          price: a.price,
          slug: a.slug,
          destinationSlug: a.destinationSlug,
          imageUrl: a.imageUrl,
          categories: (a.categories || []).map(name => ({ name })),
        },
      });
    });
    writeQuizFlow({ setup, responses });
    navigate(`/destination/${setup.destination.slug}?tab=trip-builder`, { replace: true });
  }, [isComplete, pool, setup, responses, dispatch, navigate]);

  const getCardLink = (activity) => {
    if (!activity || !activity.slug || !activity.destinationSlug) {
      return null;
    }
    return `/destination/${activity.destinationSlug}/activity/${activity.slug}`;
  };

  const handleSwipe = (direction, activityId) => {
    if (direction === 'right') {
      pickedRef.current = [...pickedRef.current, activityId];
    }
    setCurrentIndex(prev => prev + 1);
  };

  const handleUndo = () => {
    if (currentIndex === 0) {
      return;
    }
    const prevId = pool[currentIndex - 1].id;
    pickedRef.current = pickedRef.current.filter(id => id !== prevId);
    setCurrentIndex(prev => prev - 1);
  };

  const handleStartOver = () => {
    pickedRef.current = [];
    setCurrentIndex(0);
    setError(null);
  };

  if (error && !pool) {
    return <div className="curate-page-error">{error}</div>;
  }
  if (!pool) {
    return <div className="curate-page-loading">Loading pool…</div>;
  }
  if (pool.length === 0) {
    return <div className="curate-page-empty">No activities match your quiz. Try a different destination.</div>;
  }

  if (isComplete) {
    if (pickedRef.current.length > 0) {
      // Handoff in flight — the completion effect above is navigating away.
      return <div className="curate-page-loading">Building your trip…</div>;
    }
    return (
      <div className="curate-finalize">
        <p className="curate-finalize-empty">
          You didn&apos;t pick anything. Start over and swipe right on what the group should vote on.
        </p>
        <button type="button" className="curate-finalize-reset" onClick={handleStartOver}>
          Start over
        </button>
      </div>
    );
  }

  return (
    <SwipeCard
      cards={pool}
      currentIndex={currentIndex}
      onSwipe={handleSwipe}
      onUndo={handleUndo}
      canUndo={currentIndex > 0}
      title="Pick activities for the group to vote on"
      subtitle="Swipe right to include, left to skip"
      getCardLink={getCardLink}
    />
  );
}

export default function CuratePage() {
    return (
        <>
            <VoteMeta title="Pick activities"/>
            <CurateContent/>
        </>
    );
}
```

- [ ] **Step 4: Trim CuratePage.css**

In `myhive-react-app/src/pages/vote/CuratePage.css`, delete these now-unused selectors (keep everything else — `.curate-page-loading/-empty/-error`, `.curate-finalize`, `.curate-finalize-empty`, `.curate-finalize-reset`, `.curate-finalize-reset:hover`):

- `.curate-finalize h2`
- `.curate-finalize-grid`
- `.curate-finalize-card`, `.curate-finalize-card-image`, `.curate-finalize-card-body`, `.curate-finalize-card-name`, `.curate-finalize-card-link`, `.curate-finalize-card-link:hover`, `.curate-finalize-card-price`, `.curate-finalize-card-cats`
- `.curate-finalize-error`
- `.curate-finalize-actions`
- `.curate-finalize-create`, `.curate-finalize-create:hover`, `.curate-finalize-create:disabled`
- `.curate-finalize-build`, `.curate-finalize-build:disabled`

- [ ] **Step 5: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=CuratePage`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add myhive-react-app/src/pages/vote/CuratePage.js myhive-react-app/src/pages/vote/CuratePage.css myhive-react-app/src/pages/vote/CuratePage.test.js
git commit -m "feat(vote): land the organizer in the trip builder after the last swipe"
```

---

### Task 3: TripBuilder — quiz mode detection + Start Over

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`
- Test: `myhive-react-app/src/components/TripBuilder.test.js` (append a describe block)

**Interfaces:**
- Consumes: `readQuizFlow`, `clearQuizFlow` from Task 1; sessionStorage contract from Task 2.
- Produces: internal `quizFlow` state + `quizMode` boolean (`quizFlow != null && quizFlow.setup?.destination?.id === destinationId`) that Tasks 4–6 reuse; `handleQuizStartOver()` handler; `navigate` from `useNavigate()` in scope for Task 4.

- [ ] **Step 1: Write the failing tests**

Append to `myhive-react-app/src/components/TripBuilder.test.js` (top-level imports already include `useLocation` — extend the react-router-dom import if needed: `import {MemoryRouter, useLocation} from 'react-router-dom';` is already present):

```js
// ---------------------------------------------------------------------------
// Quiz mode — Start Over (organizer arrived from the quiz/swipe flow)
// ---------------------------------------------------------------------------

describe('quiz mode: Start Over', () => {
    const quizSetup = {
        destination: { id: 'dest-1', slug: 'prague' },
        travelers: 4,
        startDate: '2026-09-01',
        endDate: '2026-09-05',
        email: 'organizer@example.com',
        budget: 2000,
    };

    function seedQuizFlow(setupOverrides = {}) {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: { ...quizSetup, ...setupOverrides },
            responses: [{ questionId: 'q1', answerId: 'a1' }],
        }));
    }

    function QuizLocationProbe() {
        const location = useLocation();
        return <div data-testid="quiz-location">{location.pathname}</div>;
    }

    function renderQuizTripBuilder(tripState = buildTripState(), dispatch = jest.fn()) {
        render(
            <MemoryRouter initialEntries={['/']}>
                <TripContext.Provider value={{ state: tripState, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <QuizLocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
        return dispatch;
    }

    test('Start Over shows in quiz mode, clears the trip, and restarts the quiz', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        const dispatch = renderQuizTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Start Over' }));

        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Start Over',
            block: 'trip_builder',
        });
        expect(dispatch).toHaveBeenCalledWith({ type: 'CANCEL_TRIP_SETUP' });
        expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
        expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
        expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        expect(screen.getByTestId('quiz-location')).toHaveTextContent('/vote/new/quiz');
    });

    test('Start Over is absent without a stored quiz flow', () => {
        renderQuizTripBuilder();
        expect(screen.queryByRole('button', { name: 'Start Over' })).not.toBeInTheDocument();
    });

    test('Start Over is absent when the stored quiz flow is for another destination', () => {
        seedQuizFlow({ destination: { id: 'other-dest', slug: 'berlin' } });
        renderQuizTripBuilder();
        expect(screen.queryByRole('button', { name: 'Start Over' })).not.toBeInTheDocument();
    });

    test('Start Over still shows when the cart is empty in quiz mode', () => {
        seedQuizFlow();
        renderQuizTripBuilder(buildTripState({ tripItems: [] }));
        expect(screen.getByRole('button', { name: 'Start Over' })).toBeInTheDocument();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: FAIL — `Unable to find role="button" and name "Start Over"` (3 of the 4 new tests fail; the "absent" tests pass trivially).

- [ ] **Step 3: Implement quiz mode detection + Start Over**

In `myhive-react-app/src/components/TripBuilder.js`:

3a. Extend imports (line 2 area):

```js
import {useNavigate, useSearchParams} from 'react-router-dom';
```

and add after the other util imports:

```js
import {clearQuizFlow, readQuizFlow} from '../utils/quizFlow';
```

3b. Inside the component, after the existing `useState` declarations (below `checkingVote`), add:

```js
  // Organizer quiz-flow handoff (CuratePage writes it): active only for the
  // destination the quiz ran for — other destinations get the plain builder.
  const [quizFlow, setQuizFlow] = useState(() => readQuizFlow());
  const quizMode = quizFlow != null && quizFlow.setup?.destination?.id === destinationId;
  const navigate = useNavigate();
```

3c. After `handleStartVoteClick`, add the handler:

```js
  const handleQuizStartOver = () => {
    pushEvent('cta_click', { cta_label: 'Start Over', block: 'trip_builder' });
    dispatch({ type: 'CANCEL_TRIP_SETUP' });
    dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
    dispatch({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
    clearQuizFlow();
    setQuizFlow(null);
    navigate('/vote/new/quiz', { state: { setup: quizFlow.setup } });
  };
```

3d. In the JSX, inside the `trip-actions` block, after the `Let your mates vote` button's closing `)}`, add:

```jsx
              {quizMode && (
                  <button
                      type="button"
                      className="btn btn--full-width start-vote-btn"
                      onClick={handleQuizStartOver}
                  >
                    Start Over
                  </button>
              )}
```

3e. In the empty-state block, change:

```jsx
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
            </div>
```

to:

```jsx
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
              {quizMode && (
                  <button
                      type="button"
                      className="btn btn--full-width start-vote-btn"
                      onClick={handleQuizStartOver}
                  >
                    Start Over
                  </button>
              )}
            </div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS (all existing + 4 new tests)

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(trip-builder): quiz mode detection and Start Over button"
```

---

### Task 4: TripBuilder — one-click QUIZ vote creation

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`
- Test: `myhive-react-app/src/components/TripBuilder.test.js`

**Interfaces:**
- Consumes: `quizFlow`/`quizMode`/`navigate` from Task 3; `voteApi.createSession` (existing service, signature `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, budget, voterToken, quizResponses, activityIds}` → `{shareToken, managerToken}`); `getOrCreateVoterToken` from `'../utils/voterToken'`; existing `standalone`, `travelers`, `canStartVote` derived values.
- Produces: `handleQuizVoteCreate()` wired to the vote button in quiz mode; quiz context cleared on launch and on booking submit.

- [ ] **Step 1: Extend the voteApi mock and write the failing tests**

1a. In `myhive-react-app/src/components/TripBuilder.test.js`, extend the voteApi module mock (currently `getResult`, `getSession`) to:

```js
jest.mock('../services/voteApi', () => ({
    __esModule: true,
    default: {
        getResult: jest.fn(),
        getSession: jest.fn(),
        createSession: jest.fn(),
        buildPool: jest.fn(),
    },
}));
```

1b. In the top-level `beforeEach`, after the `voteApi.getResult.mockResolvedValue(...)` line, add (CRA `resetMocks: true` — defaults must live here):

```js
    voteApi.createSession.mockResolvedValue({ shareToken: 'quiz-tok-1', managerToken: 'mgr-1' });
    voteApi.buildPool.mockResolvedValue({ pool: [] });
```

1c. Append a describe block (reuses the same shape as Task 3's helpers — duplicate them here so the block is self-contained):

```js
// ---------------------------------------------------------------------------
// Quiz mode — one-click vote creation (no modal; QUIZ session from the cart)
// ---------------------------------------------------------------------------

describe('quiz mode: one-click vote', () => {
    const quizSetup = {
        destination: { id: 'dest-1', slug: 'prague' },
        travelers: 4,
        startDate: '2026-09-01',
        endDate: '2026-09-05',
        email: 'organizer@example.com',
        budget: 2000,
    };
    const quizResponses = [{ questionId: 'q1', answerId: 'a1' }];

    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: quizSetup,
            responses: quizResponses,
        }));
    }

    function VoteLocationProbe() {
        const location = useLocation();
        return <div data-testid="vote-location">{location.pathname}</div>;
    }

    function renderQuizTripBuilder(tripState, dispatch = jest.fn()) {
        render(
            <MemoryRouter initialEntries={['/']}>
                <TripContext.Provider value={{ state: tripState, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <VoteLocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
    }

    test('creates a QUIZ session from the current cart without a modal', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState({
            tripItems: [activity1, activity2],
            tripTravelers: 3,
            tripStartDate: '2026-09-01',
            tripEndDate: '2026-09-05',
            tripBudget: 2000,
        }));

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        // No email modal — the session is created directly.
        expect(screen.queryByLabelText('Your email')).not.toBeInTheDocument();
        await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledTimes(1));

        const arg = voteApi.createSession.mock.calls[0][0];
        expect(arg).toMatchObject({
            destinationId: 'dest-1',
            initiatorEmail: 'organizer@example.com',
            numberOfTravelers: 3,
            startDate: '2026-09-01',
            endDate: '2026-09-05',
            budget: 2000,
            quizResponses,
            activityIds: ['act-1', 'act-2'],
        });
        expect(typeof arg.voterToken).toBe('string');

        // cta_click intent + A12 vote_launched conversion, same payloads as before.
        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Let your mates vote',
            block: 'trip_builder',
        });
        expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
            trip_id: 'quiz-tok-1',
            user_role: 'organizer',
            selected_count: 2,
        });

        // Organizer markers + context cleanup + waiting-page navigation.
        expect(localStorage.getItem('myhive-initiator-quiz-tok-1')).toBe('true');
        expect(localStorage.getItem('myhive-manager-quiz-tok-1')).toBe('mgr-1');
        expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        await waitFor(() => {
            expect(screen.getByTestId('vote-location')).toHaveTextContent('/vote/quiz-tok-1/waiting');
        });
    });

    test('createSession failure surfaces the error and keeps the quiz context', async () => {
        seedQuizFlow();
        voteApi.createSession.mockRejectedValue(new Error('Server exploded'));
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByText('Server exploded')).toBeInTheDocument();
        expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
        expect(sessionStorage.getItem('myhive-quiz-flow')).not.toBeNull();
        expect(screen.getByTestId('vote-location')).toHaveTextContent('/');
    });

    test('outside quiz mode the button still opens the CART modal', async () => {
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByLabelText('Your email')).toBeInTheDocument();
        expect(voteApi.createSession).not.toHaveBeenCalled();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: FAIL — the first two new tests fail (the CART modal opens instead of `createSession` being called). The third passes.

- [ ] **Step 3: Implement the one-click handler**

In `myhive-react-app/src/components/TripBuilder.js`:

3a. Add import:

```js
import {getOrCreateVoterToken} from '../utils/voterToken';
```

(Check the export style in `src/utils/voterToken.js` first — if it is a default-object or named export, match it; CuratePage previously used `import { getOrCreateVoterToken } from '../../utils/voterToken';`, so the named import is correct.)

3b. Add state next to `checkingVote`:

```js
  const [creatingVote, setCreatingVote] = useState(false);
  const [voteCreateError, setVoteCreateError] = useState(null);
```

3c. AFTER the derived values (`travelers`, `standalone`, `canStartVote`, `totalPrice` block — i.e. right after `const totalPrice = computeTripTotal(...)`), add:

```js
  // Quiz-flow one-click vote: email and quiz answers were captured before the
  // quiz, so no modal — create the QUIZ session from the current cart and go
  // straight to the waiting page. QUIZ sessions intentionally do not set
  // myhive-trip-vote-session (parity with the old curate-screen flow).
  const handleQuizVoteCreate = async () => {
    pushEvent('cta_click', {
      cta_label: 'Let your mates vote',
      block: 'trip_builder',
    });
    if (creatingVote) {
      return;
    }
    setCreatingVote(true);
    setVoteCreateError(null);
    try {
      const session = await voteApi.createSession({
        destinationId,
        initiatorEmail: quizFlow.setup.email,
        numberOfTravelers: travelers,
        startDate: state.tripStartDate || quizFlow.setup.startDate,
        endDate: state.tripEndDate || quizFlow.setup.endDate,
        budget: state.tripBudget,
        voterToken: getOrCreateVoterToken(),
        quizResponses: quizFlow.responses,
        activityIds: standalone.map(item => item.id),
      });
      localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
      if (session.managerToken) {
        localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
      }
      // A12 — vote_launched: same field names as the CART path; shareToken is the trip_id.
      pushEvent('vote_launched', {
        trip_id: session.shareToken,
        user_role: 'organizer',
        selected_count: standalone.length,
      });
      clearQuizFlow();
      navigate(`/vote/${session.shareToken}/waiting`, {
        state: { managerToken: session.managerToken },
      });
    } catch (e) {
      setVoteCreateError(e.message || 'Failed to create the vote. Please try again.');
      setCreatingVote(false);
    }
  };
```

3d. Rewire the vote button in the `trip-actions` JSX:

```jsx
              {standalone.length > 0 && (
                  <button
                      type="button"
                      className="btn btn--full-width start-vote-btn"
                      onClick={quizMode ? handleQuizVoteCreate : handleStartVoteClick}
                      disabled={!canStartVote || checkingVote || creatingVote}
                      title={voteButtonTitle}
                  >
                    {creatingVote ? 'Creating…' : 'Let your mates vote'}
                  </button>
              )}
```

3e. Next to the existing `submitError` display inside `trip-actions`, add:

```jsx
              {voteCreateError && (
                  <div className="export-error">
                    <p>{voteCreateError}</p>
                  </div>
              )}
```

3f. In `handleContactSubmit`, right after `localStorage.removeItem('myhive-trip-vote-session');`, add:

```js
      clearQuizFlow();
      setQuizFlow(null);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(trip-builder): one-click QUIZ vote creation in quiz mode"
```

---

### Task 5: TripBuilder — vote_skipped on Complete Booking

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js` (`handleConfirmTrip`)
- Test: `myhive-react-app/src/components/TripBuilder.test.js`

**Interfaces:**
- Consumes: `quizMode` from Task 3; existing `handleConfirmTrip` trip-id resolution.
- Produces: `vote_skipped {trip_id, selected_count}` fired at most once per trip on Complete Booking while quiz mode is active.

- [ ] **Step 1: Write the failing tests**

Append to `myhive-react-app/src/components/TripBuilder.test.js`:

```js
// ---------------------------------------------------------------------------
// Quiz mode — A13 vote_skipped moves to the Complete Booking click
// ---------------------------------------------------------------------------

describe('quiz mode: vote_skipped on Complete Booking', () => {
    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: {
                destination: { id: 'dest-1', slug: 'prague' },
                travelers: 4,
                startDate: '2026-09-01',
                endDate: '2026-09-05',
                email: 'organizer@example.com',
                budget: 2000,
            },
            responses: [{ questionId: 'q1', answerId: 'a1' }],
        }));
    }

    test('A13: fires with trip_id and selected_count when quiz mode is active', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id', tripItems: [activity1, activity2] }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent).toHaveBeenCalledWith('vote_skipped', {
            trip_id: 'ctx-trip-id',
            selected_count: 2,
        });
    });

    test('A13: dedups per trip_id across repeat clicks', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));
        await user.keyboard('{Escape}');
        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent.mock.calls.filter(([e]) => e === 'vote_skipped')).toHaveLength(1);
    });

    test('A13: does not fire outside quiz mode', async () => {
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent).not.toHaveBeenCalledWith('vote_skipped', expect.anything());
    });

    test('booking submit clears the quiz-flow context', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));
        await fillAndSubmitContactForm(user);

        await waitFor(() => {
            expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        });
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: FAIL — the first two tests fail (`vote_skipped` never fired). The third passes; the fourth already passes via Task 4's cleanup (if it fails, Task 4 Step 3f was missed).

- [ ] **Step 3: Implement**

In `handleConfirmTrip` (TripBuilder.js), after the `booking_form_viewed` block and before `setShowContactForm(true);`, add:

```js
    // A13 — vote_skipped: in the quiz flow, heading into booking without
    // having launched a vote is the moment the organizer skips voting
    // (launching a vote clears quizFlow, so quizMode implies "no vote yet").
    if (quizMode) {
      const skipKey = `myhive-vote-skipped-${tripId}`;
      if (!sessionStorage.getItem(skipKey)) {
        sessionStorage.setItem(skipKey, '1');
        pushEvent('vote_skipped', { trip_id: tripId, selected_count: state.tripItems.length });
      }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(trip-builder): fire vote_skipped on Complete Booking in quiz mode"
```

---

### Task 6: TripBuilder — Recommended for you section

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`
- Modify: `myhive-react-app/src/components/TripBuilder.css`
- Test: `myhive-react-app/src/components/TripBuilder.test.js`

**Interfaces:**
- Consumes: `quizMode`/`quizFlow` from Task 3; `voteApi.buildPool({destinationId, responses})` → `{pool: [{activityId, name, price, imageUrl, slug, destinationSlug, description, duration, includes, categories: [string]}]}`; existing `handleAddActivity`; `ActivityPreviewModal` component (props: `activity`, `link`, `onClose`).
- Produces: quiz-matched recommendations section above Browse More Activities.

- [ ] **Step 1: Write the failing tests**

Append to `myhive-react-app/src/components/TripBuilder.test.js`:

```js
// ---------------------------------------------------------------------------
// Quiz mode — Recommended for you (quiz-matched pool above Browse)
// ---------------------------------------------------------------------------

describe('quiz mode: Recommended for you', () => {
    const quizResponses = [{ questionId: 'q1', answerId: 'a1' }];

    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: {
                destination: { id: 'dest-1', slug: 'prague' },
                travelers: 4,
                startDate: '2026-09-01',
                endDate: '2026-09-05',
                email: 'organizer@example.com',
                budget: 2000,
            },
            responses: quizResponses,
        }));
    }

    const recommendedPool = [
        // Already in the default cart (activity1 = act-1 "Kayaking") → Added state.
        { activityId: 'act-1', name: 'Kayaking', price: 60, imageUrl: null, slug: 'kayak', destinationSlug: 'prague', categories: ['Water'] },
        { activityId: 'rec-9', name: 'Beer Spa', price: 90, imageUrl: null, slug: 'beer-spa', destinationSlug: 'prague', description: 'Bathe in beer.', categories: ['Chillout'] },
    ];

    test('renders the quiz-matched pool with Add / Added states', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        renderTripBuilder();

        expect(await screen.findByText('Recommended for you')).toBeInTheDocument();
        expect(voteApi.buildPool).toHaveBeenCalledWith({
            destinationId: 'dest-1',
            responses: quizResponses,
        });

        expect(screen.getByRole('button', { name: 'Beer Spa' })).toBeInTheDocument();
        const addButtons = screen.getAllByRole('button', { name: 'Add' });
        expect(addButtons).toHaveLength(1); // rec-9 only
        expect(screen.getByRole('button', { name: 'Added' })).toBeDisabled(); // act-1 is in the cart
    });

    test('Add dispatches a silent ADD_TO_TRIP with mapped categories', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        const user = userEvent.setup();
        const { dispatchMock } = renderTripBuilder();

        await screen.findByText('Recommended for you');
        await user.click(screen.getByRole('button', { name: 'Add' }));

        expect(dispatchMock).toHaveBeenCalledWith({
            type: 'ADD_TO_TRIP',
            silent: true,
            activity: expect.objectContaining({
                id: 'rec-9',
                name: 'Beer Spa',
                categories: [{ name: 'Chillout' }],
            }),
        });
    });

    test('clicking a recommendation name opens the preview modal instead of navigating', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        const user = userEvent.setup();
        renderTripBuilder();

        await screen.findByText('Recommended for you');
        await user.click(screen.getByRole('button', { name: 'Beer Spa' }));

        expect(screen.getByText('Bathe in beer.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /View full page/i }))
            .toHaveAttribute('href', '/destination/prague/activity/beer-spa');
    });

    test('no section and no pool fetch outside quiz mode', async () => {
        renderTripBuilder();

        await waitFor(() => expect(api.getActivities).toHaveBeenCalled());
        expect(screen.queryByText('Recommended for you')).not.toBeInTheDocument();
        expect(voteApi.buildPool).not.toHaveBeenCalled();
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: FAIL — first three tests fail (`Unable to find text 'Recommended for you'`); the fourth passes.

- [ ] **Step 3: Implement the section**

In `myhive-react-app/src/components/TripBuilder.js`:

3a. Add import:

```js
import ActivityPreviewModal from './ActivityPreviewModal';
```

3b. Add state next to the other quiz-mode state:

```js
  const [recommended, setRecommended] = useState([]);
  const [previewActivity, setPreviewActivity] = useState(null);
```

3c. Add the fetch effect after the quiz-mode state declarations:

```js
  // Quiz-flow recommendations: the quiz-matched pool for this destination,
  // left-swiped cards included on purpose (second look). In-cart items render
  // as a disabled "Added". Failures are silent — the browse column still works.
  useEffect(() => {
    if (!quizMode) {
      setRecommended([]);
      return;
    }
    let cancelled = false;
    voteApi.buildPool({ destinationId, responses: quizFlow.responses })
        .then(data => {
          if (!cancelled) {
            setRecommended((data.pool || []).map(a => ({ ...a, id: a.activityId })));
          }
        })
        .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [quizMode, quizFlow, destinationId]);
```

3d. Add the link helper next to the other handlers:

```js
  const getPreviewLink = (activity) => {
    if (!activity || !activity.slug || !activity.destinationSlug) {
      return null;
    }
    return `/destination/${activity.destinationSlug}/activity/${activity.slug}`;
  };
```

3e. In the right-column JSX (the non-`showContactForm` branch), directly above the existing `voteResult && voteResult.suggestions` block, add:

```jsx
        {quizMode && recommended.length > 0 && (
            <div className="trip-vote-suggestions">
              <h3>Recommended for you</h3>
              <p className="trip-vote-suggestions-sub">Based on your quiz answers</p>
              <div className="browse-activities">
                {recommended.map(a => {
                    const isAdded = state.tripItems.some(item => item.id === a.id);
                    return (
                        <div key={a.id} className="browse-activity-item">
                          {a.imageUrl && (
                              <img src={a.imageUrl} alt={a.name}
                                   className="browse-activity-image" loading="lazy"/>
                          )}
                          <div className="browse-activity-content">
                            <button
                                type="button"
                                className="browse-activity-title browse-activity-link"
                                aria-haspopup="dialog"
                                onClick={() => setPreviewActivity(a)}
                            >
                              {a.name}
                            </button>
                            <div className="browse-activity-price">{formatPricePerPerson(a.price)}</div>
                          </div>
                          <button
                              className="browse-add-btn"
                              onClick={() => handleAddActivity({
                                  id: a.id,
                                  name: a.name,
                                  price: a.price,
                                  slug: a.slug,
                                  destinationSlug: a.destinationSlug,
                                  imageUrl: a.imageUrl,
                                  description: a.description,
                                  includes: a.includes,
                                  categories: (a.categories || []).map(name => ({ name })),
                              })}
                              disabled={isAdded}
                          >
                            {isAdded ? 'Added' : 'Add'}
                          </button>
                        </div>
                    );
                })}
              </div>
            </div>
        )}
```

3f. Render the modal next to the other modals at the bottom (before `</div>` closing `trip-builder-layout`):

```jsx
      <ActivityPreviewModal
          activity={previewActivity}
          link={previewActivity ? getPreviewLink(previewActivity) : null}
          onClose={() => setPreviewActivity(null)}
      />
```

3g. In `myhive-react-app/src/components/TripBuilder.css`, add after the `.start-vote-btn` rules:

```css
/* Recommended-for-you names are buttons (open the preview modal) — strip the
   native button chrome so they render like the plain titles around them. */
.browse-activity-link {
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    color: inherit;
    cursor: pointer;
    text-align: left;
    text-decoration: underline;
    text-decoration-thickness: 1px;
    text-underline-offset: 3px;
}

.browse-activity-link:hover {
    color: var(--primary, #32b8c6);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false --testPathPattern=TripBuilder`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilder.css myhive-react-app/src/components/TripBuilder.test.js
git commit -m "feat(trip-builder): quiz-matched Recommended for you section"
```

---

### Task 7: Full verification + review

**Files:**
- No new files; fixes only if the suite or build surfaces issues.

- [ ] **Step 1: Run the full frontend test suite**

Run: `npm test -- --watchAll=false`
Expected: ALL suites pass (including untouched QuizPage, StartGroupVoteModal, useStartGroupVote, DestinationPage tests). Fix any regression before proceeding.

- [ ] **Step 2: Production build (catches unused-import/eslint errors)**

Run: `npm run build`
Expected: `Compiled successfully` (warnings about pre-existing issues are acceptable; NEW warnings from touched files are not — fix them).

- [ ] **Step 3: Code review**

Per project workflow: perform a code review of the whole diff (`git diff main...HEAD`) before declaring done — check DRY, naming, comment discipline, analytics payload parity against the spec's mapping table, and that no `CuratePage` dead code remains.

- [ ] **Step 4: Post-approval docs (do NOT do this before user approval)**

After the user approves: update memory (`project_cart_vote_flow.md` — the organizer flow no longer has a finalize screen; note `myhive-quiz-flow` sessionStorage key and vote_skipped's new trigger) and `CLAUDE.md`/`README.md` only if they describe the organizer quiz flow.
