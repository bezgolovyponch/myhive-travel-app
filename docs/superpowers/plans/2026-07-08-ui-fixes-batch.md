# UI Fixes Batch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ten approved UI fixes: new hero photo, purple→white accents, orange-outline vote button, budget field removal, traveler slider, global white/gray scrollbar, clickable hero headline, swipe undo, activity-detail spec alignment, new tri▼lu logo.

**Architecture:** All changes live in the CRA app `myhive-react-app/`. CSS-only tasks edit existing stylesheets; behavior tasks (modal, undo, gallery, headline) follow TDD with React Testing Library, mirroring the mock-heavy pattern in `src/components/TripBuilder.test.js` (contexts mocked via `jest.mock`, `DateRangePicker` replaced with plain inputs).

**Tech Stack:** React 18 (CRA), Jest + React Testing Library, plain CSS with custom properties.

**Spec:** `docs/superpowers/specs/2026-07-08-ui-fixes-batch-design.md`

## Global Constraints

- Logo/outline orange: `#E24A33`. "Added to trip" state orange: `#E8852B`, hover `#cf7320`.
- Neutral white buttons: background `#fff`, text `#16131c`, hover background `rgba(255, 255, 255, 0.85)`.
- Do NOT delete or change the `--purple*` token definitions in `HomePage.css:8-11` — only change usages. Other vote pages still use them.
- Test command (CRA): `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern=<Name>`
- All file paths below are relative to `myhive-react-app/` unless they start with `docs/`.
- Run `CI=true npx react-scripts test --watchAll=false` (full suite) before the final commit of the plan.
- Commit messages follow existing style: `fix(scope): summary` / `feat(scope): summary`.

---

### Task 1: Hero background image

**Files:**
- Create: `public/hero-stag-do-prague.jpg` (generated from `hero_stag_do_prague.png`)
- Modify: `src/pages/HomePage.css:44` (desktop) and `src/pages/HomePage.css:259-264` (mobile)

**Interfaces:** none (asset + CSS only).

- [ ] **Step 1: Compress the source photo to a web-sized JPEG**

```bash
cd myhive-react-app
sips -s format jpeg -s formatOptions 78 hero_stag_do_prague.png --out public/hero-stag-do-prague.jpg
ls -la public/hero-stag-do-prague.jpg
```

Expected: file exists, size ≤ ~300KB. If larger than 350KB, re-run with `-s formatOptions 68`.

- [ ] **Step 2: Point the desktop hero at the local asset**

In `src/pages/HomePage.css` line 44, replace:

```css
    background: url('https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/af982ae2-c47d-40d8-af16-d6ec14713544.jpg') center 0 / cover no-repeat var(--bg);
```

with:

```css
    background: url('/hero-stag-do-prague.jpg') center 20% / cover no-repeat var(--bg);
```

- [ ] **Step 3: Point the mobile hero at the same asset**

In the `@media (max-width: 768px)` block (`HomePage.css:259-264`), replace:

```css
    .hero {
        min-height: 41.25rem;
        background-image: url('https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/hero_mobile2.png');
        background-position: center 55%;
    }
```

with:

```css
    .hero {
        min-height: 41.25rem;
        background-image: url('/hero-stag-do-prague.jpg');
        background-position: 42% 25%;
    }
```

- [ ] **Step 4: Verify visually**

Run `npm start`, check desktop and a 390px-wide viewport: the crowd-surfing subject must be visible (not cropped out) at both widths, text overlay readable. Adjust `background-position` percentages if needed.

- [ ] **Step 5: Commit**

```bash
git add public/hero-stag-do-prague.jpg src/pages/HomePage.css
git commit -m "feat(home): new stag-do hero photo, served locally"
```

---

### Task 2: tri▼lu logo SVG

**Files:**
- Create: `public/logo-trivlu.svg`
- Modify: `src/components/Header.js:41-43`

**Interfaces:** none.

- [ ] **Step 1: Create the SVG**

Create `public/logo-trivlu.svg` — white bold lowercase "tri" + orange downward triangle (the "v") + "lu":

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 100" role="img" aria-label="trivlu">
  <text x="0" y="78" fill="#ffffff" font-family="Arial, 'Helvetica Neue', sans-serif" font-weight="800" font-size="88" letter-spacing="-4">tri</text>
  <path d="M118 32 L180 32 L149 82 Z" fill="#E24A33"/>
  <text x="188" y="78" fill="#ffffff" font-family="Arial, 'Helvetica Neue', sans-serif" font-weight="800" font-size="88" letter-spacing="-4">lu</text>
</svg>
```

- [ ] **Step 2: Use it in the header**

In `src/components/Header.js` replace:

```jsx
<img src="/logo-white.png" alt="Trivlu" className="logo-img"/>
```

with:

```jsx
<img src="/logo-trivlu.svg" alt="Trivlu" className="logo-img"/>
```

- [ ] **Step 3: Verify visually**

`npm start`: compare against the reference (black-text mockup, orange triangle sitting between "tri" and "lu", triangle tip pointing down, baseline-aligned). Tweak the `x` coordinates / triangle path in the SVG until spacing looks like the mockup at 36px header height and 26px mobile height. `.logo-img { height: 36px; width: auto }` already handles sizing — no CSS change needed.

- [ ] **Step 4: Commit**

```bash
git add public/logo-trivlu.svg src/components/Header.js
git commit -m "feat(brand): trivlu logo with orange triangle in header"
```

---

### Task 3: Global scrollbar — white thumb, gray track

**Files:**
- Modify: `src/styles/global.css` (append after the base reset block, ~line 60)

**Interfaces:** none.

- [ ] **Step 1: Add scrollbar rules**

Append to `src/styles/global.css`:

```css
/* Site-wide scrollbar: white thumb on a gray track (dark theme).
   iOS Safari uses overlay scrollbars and ignores these rules. */
html {
    scrollbar-color: #ffffff #3a3a3a; /* Firefox */
}

::-webkit-scrollbar {
    width: 10px;
    height: 10px;
}

::-webkit-scrollbar-track {
    background: #3a3a3a;
}

::-webkit-scrollbar-thumb {
    background: #ffffff;
    border-radius: 6px;
    border: 2px solid #3a3a3a;
}
```

- [ ] **Step 2: Verify visually**

`npm start`: page scrollbar and inner scrollable areas (trip builder columns) show white thumb / gray track on desktop Chrome and in responsive mode (Android emulation).

- [ ] **Step 3: Commit**

```bash
git add src/styles/global.css
git commit -m "feat(ui): white-on-gray scrollbars site-wide"
```

---

### Task 4: Purple → neutral white (hero + activity section) + orange "Added to trip" state

**Files:**
- Modify: `src/pages/HomePage.css` (lines 24-32, 109-126, 189-198, 237-240)
- Modify: `src/pages/HomePage.js` (lines 44-48, vote-card fills)
- Modify: `src/components/ActivityCard.css` (lines 51-65, 83-104)
- Modify: `src/pages/ActivityDetailPage.css` (lines 74-77, 114-117, 137-142, 197-220, 245-248)
- Modify: `src/components/ActivityGallery.css` (line ~229, `.ag-lb-strip button.active`)

**Interfaces:** none (CSS + inline style values). Mapping rule for any purple usage encountered in these files beyond the listed lines: button fill `var(--purple)`/`var(--brand)` → `#fff` bg + `#16131c` text; hover `var(--purple-l)`/`var(--brand-dark)` → `rgba(255,255,255,0.85)`; icon/accent tints (`--purple-l`, `--purple-ll`, `--purple-ic`, `--brand` on icons) → `rgba(255,255,255,0.85)`.

- [ ] **Step 1: HomePage.css — repaint homepage primary buttons white**

Replace lines 24-32:

```css
.homepage .btn--primary {
    background: #fff;
    color: #16131c;
    box-shadow: 0 0.5rem 1.5rem rgba(0, 0, 0, 0.35);
}

.homepage .btn--primary:hover {
    background: rgba(255, 255, 255, 0.85);
}
```

Replace `.hp-btn-primary` (lines 109-126) background/color/shadow/hover the same way:

```css
.hp-btn-primary {
    background: #fff;
    color: #16131c;
    border: none;
    font-size: 1rem;
    font-weight: 500;
    padding: 0.9375rem 1.875rem;
    border-radius: 0.75rem;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    box-shadow: 0 0.5rem 1.5rem rgba(0, 0, 0, 0.35);
}

.hp-btn-primary:hover {
    background: rgba(255, 255, 255, 0.85);
}
```

- [ ] **Step 2: HomePage.css — neutralize vote-card accents**

`.vc-badge` (line 189-198): `background: var(--purple)` → `background: #fff`, and `color: #fff` → `color: #16131c`.
`.vc-name i` (line 237-240): `color: var(--purple-ic)` → `color: rgba(255, 255, 255, 0.85)`.

- [ ] **Step 3: HomePage.js — white progress-bar fills**

Lines 44-48, replace the `fill` values:

```jsx
{icon: 'ph-beer-stein', name: 'Bar Crawl', num: 8, pct: 89, fill: 'rgba(255,255,255,0.92)'},
{icon: 'ph-steering-wheel', name: 'Karting', num: 6, pct: 67, fill: 'rgba(255,255,255,0.65)'},
{icon: 'ph-target', name: 'Shooting', num: 5, pct: 56, fill: 'rgba(255,255,255,0.65)'},
{icon: 'ph-boat', name: 'Tiki Boat', num: 4, pct: 44, fill: 'rgba(255,255,255,0.65)'},
```

- [ ] **Step 4: ActivityCard.css — white buttons, orange added state**

`.more-info-btn` (lines 51-65): `color: var(--brand)` → `color: #fff`; `border: 1px solid var(--brand)` → `border: 1px solid rgba(255, 255, 255, 0.7)`; `:hover` `background: var(--tint-blue)` → `background: rgba(255, 255, 255, 0.12)`.

`.add-to-trip-btn` (lines 83-104):

```css
.add-to-trip-btn {
    background: #fff;
    color: #16131c;
    padding: var(--gap-sm) var(--gap);
    border-radius: var(--radius);
    border: none;
    font-size: 0.75rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 150ms;
}

.add-to-trip-btn:hover {
    background: rgba(255, 255, 255, 0.85);
    transform: translateY(-1px);
}

.add-to-trip-btn:disabled {
    background: #E8852B;
    color: #fff;
    cursor: not-allowed;
    transform: none;
}
```

- [ ] **Step 5: ActivityDetailPage.css — white icons + button, orange added state**

- Line 75 `.activity-detail-chip i`: `color: var(--brand)` → `color: rgba(255, 255, 255, 0.85)`
- Line 115 `.activity-detail-blk-title i`: same replacement
- Line 139 `.activity-detail-inc-list li::before`: same replacement
- Line 246 `.activity-detail-panel-meta li i`: same replacement
- Lines 197-220:

```css
.activity-detail-add-btn {
    background: #fff;
    color: #16131c;
    border: none;
    font-size: 1rem;
    /* ...keep the remaining existing declarations of this rule unchanged... */
}

.activity-detail-add-btn:hover {
    background: rgba(255, 255, 255, 0.85);
}

.activity-detail-add-btn:disabled {
    background: #E8852B;
    color: #fff;
    cursor: not-allowed;
}
```

(Only change `background`/`color` in the base rule; keep padding/radius/etc. as they are.)

- [ ] **Step 6: ActivityGallery.css — white active-thumbnail outline**

At `.ag-lb-strip button.active` (~line 229), replace the purple/brand outline color with `#fff`.

- [ ] **Step 7: Verify**

```bash
cd myhive-react-app && grep -n "var(--purple" src/pages/HomePage.css src/pages/HomePage.js
grep -n "var(--brand" src/components/ActivityCard.css src/pages/ActivityDetailPage.css src/components/ActivityGallery.css
```

Expected: no accent *usages* remain in these files except the token definitions in `HomePage.css:8-11`. Run `npm start` and eyeball hero, homepage sections, activity cards, detail page ("Add to trip" white; after adding — orange "Added to trip").

Run existing tests to catch regressions:

```bash
CI=true npx react-scripts test --watchAll=false
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/pages/HomePage.css src/pages/HomePage.js src/components/ActivityCard.css src/pages/ActivityDetailPage.css src/components/ActivityGallery.css
git commit -m "feat(ui): neutral white accents in hero and activity section, orange added-to-trip state"
```

---

### Task 5: "Let your mates vote" — orange outline

**Files:**
- Modify: `src/components/TripBuilder.css:436-445`

**Interfaces:** none.

- [ ] **Step 1: Recolor the button**

Replace:

```css
.start-vote-btn {
    margin-top: 0.5rem;
    background: transparent;
    border: 1px solid #7c6cf5;
    color: var(--white);
}

.start-vote-btn:hover:not(:disabled) {
    background: rgba(124, 108, 245, 0.08);
}
```

with:

```css
.start-vote-btn {
    margin-top: 0.5rem;
    background: transparent;
    border: 1.5px solid #E24A33;
    color: #E24A33;
}

.start-vote-btn:hover:not(:disabled) {
    background: rgba(226, 74, 51, 0.12);
}
```

(`.start-vote-btn:disabled` stays unchanged.)

- [ ] **Step 2: Verify visually**

`npm start` → add an activity → open trip builder: button reads orange text/border, transparent fill; disabled state still dims.

- [ ] **Step 3: Commit**

```bash
git add src/components/TripBuilder.css
git commit -m "feat(trip-builder): orange outline style for Let your mates vote"
```

---

### Task 6: Clickable hero headline

**Files:**
- Create: `src/pages/HomePage.test.js`
- Modify: `src/pages/HomePage.js:33`
- Modify: `src/pages/HomePage.css` (after `.hero-title`, ~line 100)

**Interfaces:**
- Consumes: `useStartGroupVote()` hook — returns `{voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination}`.

- [ ] **Step 1: Write the failing test**

Create `src/pages/HomePage.test.js`:

```jsx
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import HomePage from './HomePage';

jest.mock('../hooks/useStartGroupVote', () => ({useStartGroupVote: jest.fn()}));
jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}));
jest.mock('../components/TripSetupModal', () => () => null);
jest.mock('../components/home/TrustBar', () => () => null);
jest.mock('../components/home/HowItWorksSection', () => () => null);
jest.mock('../components/home/FeaturedActivitiesSection', () => () => null);
jest.mock('../components/home/ReviewsSection', () => () => null);
jest.mock('../components/home/ContactCtaSection', () => () => null);

const {useStartGroupVote} = require('../hooks/useStartGroupVote');
const {pushEvent} = require('../utils/analytics');

function renderHome() {
    return render(
        <HelmetProvider>
            <MemoryRouter>
                <HomePage/>
            </MemoryRouter>
        </HelmetProvider>
    );
}

test('hero headline is clickable and opens the group vote setup', async () => {
    const openVoteSetup = jest.fn();
    useStartGroupVote.mockReturnValue({
        voteSetupOpen: false,
        openVoteSetup,
        closeVoteSetup: jest.fn(),
        handleVoteConfirm: jest.fn(),
        preselectedDestination: null,
    });
    const user = userEvent.setup();
    renderHome();

    await user.click(screen.getByRole('button', {name: /the smartest way to plan a stag do/i}));

    expect(openVoteSetup).toHaveBeenCalled();
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Hero headline', block: 'hero'});
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern=HomePage`
Expected: FAIL — no button with that name (headline is static text).

- [ ] **Step 3: Implement the headline**

In `src/pages/HomePage.js`, replace line 33:

```jsx
<h1 className="hero-title">The Easiest Stag Do Decision. All Sorted For You.</h1>
```

with:

```jsx
<h1 className="hero-title">
    <button
        type="button"
        className="hero-title-link"
        onClick={() => {
            pushEvent('cta_click', {cta_label: 'Hero headline', block: 'hero'});
            openVoteSetup();
        }}
    >
        The smartest way to plan a stag do
    </button>
</h1>
```

In `src/pages/HomePage.css`, after the `.hero-title` rule (~line 100), add:

```css
/* The headline itself is a CTA: inherits the h1 look, underline signals the link. */
.hero-title-link {
    all: unset;
    cursor: pointer;
    text-decoration: underline;
    text-decoration-color: rgba(255, 255, 255, 0.45);
    text-decoration-thickness: 2px;
    text-underline-offset: 6px;
}

.hero-title-link:hover {
    text-decoration-color: #fff;
}

.hero-title-link:focus-visible {
    outline: 2px solid #fff;
    outline-offset: 4px;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern=HomePage`
Expected: PASS.

- [ ] **Step 5: Verify visually and commit**

`npm start`: headline shows new copy with subtle underline; click opens the vote setup modal.

```bash
git add src/pages/HomePage.js src/pages/HomePage.css src/pages/HomePage.test.js
git commit -m "feat(home): clickable hero headline opens group vote setup"
```

---

### Task 7: Remove budget from the setup modal

**Files:**
- Create: `src/components/TripSetupModal.test.js`
- Modify: `src/components/TripSetupModal.js`

**Interfaces:**
- Produces: `onVoteConfirm({travelers, startDate, endDate, email, destination, budget: null})` — the `budget` key stays in the payload as `null` so `useStartGroupVote`/session seeding keeps working unchanged.

- [ ] **Step 1: Write the failing tests**

Create `src/components/TripSetupModal.test.js`:

```jsx
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TripSetupModal from './TripSetupModal';

jest.mock('../context/CatalogContext', () => ({useCatalog: jest.fn()}));
jest.mock('../context/TripContext', () => ({useTrip: jest.fn()}));
jest.mock('../services/config', () => ({DESTINATION_PICKER_ENABLED: false}));
jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}));
jest.mock('../utils/attribution', () => ({getRef: jest.fn()}));
jest.mock('../utils/consent', () => ({hasConsent: () => false}));
jest.mock('../utils/uuid', () => ({generateUuid: () => 'uuid-1'}));
jest.mock('./DateRangePicker', () =>
    function MockDateRangePicker({from, to, onChange}) {
        return (
            <>
                <input data-testid="date-from" value={from} onChange={e => onChange(e.target.value, to)}/>
                <input data-testid="date-to" value={to} onChange={e => onChange(from, e.target.value)}/>
            </>
        );
    }
);

const {useCatalog} = require('../context/CatalogContext');
const {useTrip} = require('../context/TripContext');

const destination = {id: 'd1', name: 'Prague', slug: 'prague'};

function setupMocks() {
    useCatalog.mockReturnValue({state: {destinations: [destination], loading: false, error: null}});
    useTrip.mockReturnValue({
        state: {tripSetupModalOpen: false, tripTravelers: 4, tripStartDate: '', tripEndDate: '', tripBudget: null},
        dispatch: jest.fn(),
    });
}

function renderVoteModal(onVoteConfirm = jest.fn()) {
    return render(
        <TripSetupModal isVoteMode voteOpen onVoteConfirm={onVoteConfirm} onVoteCancel={jest.fn()}/>
    );
}

beforeEach(() => {
    jest.clearAllMocks();
    setupMocks();
});

test('vote setup has no budget field', () => {
    renderVoteModal();
    expect(screen.queryByLabelText(/budget/i)).not.toBeInTheDocument();
});

test('confirm sends budget: null in the vote payload', async () => {
    const onVoteConfirm = jest.fn();
    const user = userEvent.setup();
    renderVoteModal(onVoteConfirm);

    await user.type(screen.getByTestId('date-from'), '2099-01-10');
    await user.type(screen.getByTestId('date-to'), '2099-01-12');
    await user.type(screen.getByLabelText(/your email/i), 'stag@example.com');
    await user.click(screen.getByRole('button', {name: /continue to categories/i}));

    expect(onVoteConfirm).toHaveBeenCalledWith(expect.objectContaining({budget: null}));
});
```

- [ ] **Step 2: Run tests to verify current state**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern=TripSetupModal`
Expected: FAIL — the budget field is rendered (`queryByLabelText(/budget/i)` finds it).

- [ ] **Step 3: Remove the budget field**

In `src/components/TripSetupModal.js`:

1. Delete state (lines 29-30): `const [budget, setBudget] = useState('');` and `const [budgetError, setBudgetError] = useState('');`
2. Delete seeding in the open effect (lines 51-52): `setBudget(...)` and `setBudgetError('')`.
3. In `handleConfirm` vote branch (lines 95-100), delete the `budgetValue` computation and validation; call `onVoteConfirm({ travelers: travelersNum, startDate, endDate, email, destination, budget: null })`.
4. Replace `has_budget: budget.trim() !== ''` with `has_budget: false` in BOTH `tb_group_submitted` events (vote and direct branches).
5. Delete the entire budget `form-group` JSX block (lines 220-246, the `{isVoteMode && (... voteBudget ...)}` section).

- [ ] **Step 4: Run tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern=TripSetupModal`
Expected: PASS (both tests).

- [ ] **Step 5: Run the full suite (other tests may exercise the modal)**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS. If a TripBuilder test asserts on the budget field or `has_budget`, update it to the new behavior (`has_budget: false`, field absent).

- [ ] **Step 6: Commit**

```bash
git add src/components/TripSetupModal.js src/components/TripSetupModal.test.js
git commit -m "feat(vote): drop the budget question from trip setup"
```

---

### Task 8: Traveler count — slider with big dot + number input

**Files:**
- Modify: `src/components/TripSetupModal.js` (travelers form-group, lines 187-198)
- Modify: `src/components/ContactForm.css` (append; this stylesheet is already imported by the modal)
- Modify: `src/components/TripSetupModal.test.js` (add tests)

**Interfaces:**
- Produces: same `travelers` string state as today; range and number inputs both drive `setTravelers`.

- [ ] **Step 1: Write the failing tests**

Append to `src/components/TripSetupModal.test.js`:

```jsx
test('travelers is a slider synced with a number input', () => {
    renderVoteModal();
    const slider = screen.getByRole('slider', {name: /number of travelers/i});
    const num = screen.getByRole('spinbutton', {name: /number of travelers/i});
    expect(slider).toHaveValue('4'); // seeded from tripTravelers: 4
    expect(num).toHaveValue(4);
});

test('moving the slider updates the number input', () => {
    renderVoteModal();
    const slider = screen.getByRole('slider', {name: /number of travelers/i});
    // fireEvent, not userEvent: range inputs don't support typing.
    const {fireEvent} = require('@testing-library/react');
    fireEvent.change(slider, {target: {value: '12'}});
    expect(screen.getByRole('spinbutton', {name: /number of travelers/i})).toHaveValue(12);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern=TripSetupModal`
Expected: the two new tests FAIL (no slider role).

- [ ] **Step 3: Implement the slider control**

In `src/components/TripSetupModal.js`, replace the travelers form-group (lines 187-198):

```jsx
<div className="form-group">
    <label htmlFor="tripTravelers">Number of Travelers *</label>
    <div className="travelers-control">
        <input
            type="range"
            id="tripTravelers"
            className="travelers-range"
            min="1"
            max="20"
            step="1"
            value={Math.min(20, Math.max(1, parseInt(travelers, 10) || 1))}
            onChange={e => setTravelers(e.target.value)}
        />
        <input
            type="number"
            className="travelers-count"
            aria-label="Number of Travelers"
            min="1"
            max="20"
            value={travelers}
            onChange={e => setTravelers(e.target.value)}
            onBlur={e => setTravelers(String(Math.min(20, Math.max(1, parseInt(e.target.value, 10) || 1))))}
        />
    </div>
</div>
```

Append to `src/components/ContactForm.css`:

```css
/* Travelers slider: gray bar + large white dot, thumb-friendly on mobile. */
.travelers-control {
    display: flex;
    align-items: center;
    gap: 0.875rem;
}

.travelers-range {
    flex: 1;
    -webkit-appearance: none;
    appearance: none;
    height: 6px;
    border-radius: 3px;
    background: rgba(153, 153, 153, 0.45);
    outline: none;
    padding: 0;
    border: none;
}

.travelers-range::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: #fff;
    border: none;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
    cursor: pointer;
}

.travelers-range::-moz-range-thumb {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: #fff;
    border: none;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
    cursor: pointer;
}

.travelers-count {
    width: 4.25rem;
    text-align: center;
    flex-shrink: 0;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern=TripSetupModal`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Verify visually on mobile width and commit**

`npm start`, 390px viewport: slider dot easy to grab, number editable, both stay in sync.

```bash
git add src/components/TripSetupModal.js src/components/TripSetupModal.test.js src/components/ContactForm.css
git commit -m "feat(vote): traveler count as mobile-friendly slider with number input"
```

---

### Task 9: Undo last swipe in the vote deck

**Files:**
- Create: `src/pages/vote/ActivityVotePage.test.js`
- Modify: `src/pages/vote/ActivityVotePage.js` (add `handleUndo`, pass props)
- Modify: `src/components/SwipeCard.js` (render undo button)
- Modify: `src/components/SwipeCard.css` (undo button style)

**Interfaces:**
- Produces: `SwipeCard` accepts two new optional props: `onUndo: () => void` and `canUndo: boolean`. Renders the undo button only when `onUndo` is provided.
- Context: votes are batched client-side in `votesRef` and submitted once at deck end (`ActivityVotePage.js:42-51`) — undo is purely local: pop the last vote and step `currentIndex` back.

- [ ] **Step 1: Write the failing test**

Create `src/pages/vote/ActivityVotePage.test.js`:

```jsx
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import ActivityVotePage from './ActivityVotePage';

jest.mock('../../services/voteApi', () => ({
    __esModule: true,
    default: {getActivities: jest.fn(), castVotes: jest.fn()},
}));
jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));
jest.mock('./VoteMeta', () => () => null);

const voteApi = require('../../services/voteApi').default;

const activities = [
    {id: 'a1', name: 'Karting'},
    {id: 'a2', name: 'Bar Crawl'},
];

function renderPage() {
    return render(
        <MemoryRouter initialEntries={['/vote/tok1']}>
            <Routes>
                <Route path="/vote/:shareToken" element={<ActivityVotePage/>}/>
                <Route path="/vote/:shareToken/waiting" element={<div>waiting room</div>}/>
            </Routes>
        </MemoryRouter>
    );
}

beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    voteApi.getActivities.mockResolvedValue(activities);
    voteApi.castVotes.mockResolvedValue({});
});

test('undo restores the previous card and drops the recorded vote', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Karting');

    // Undo starts disabled.
    expect(screen.getByRole('button', {name: /undo last swipe/i})).toBeDisabled();

    // Like Karting -> card 2 of 2.
    await user.click(screen.getByRole('button', {name: 'Like'}));
    expect(screen.getByText('2 / 2')).toBeInTheDocument();

    // Undo -> back to card 1.
    await user.click(screen.getByRole('button', {name: /undo last swipe/i}));
    expect(screen.getByText('1 / 2')).toBeInTheDocument();

    // Re-vote: dislike Karting, like Bar Crawl -> deck ends, votes submitted.
    await user.click(screen.getByRole('button', {name: 'Dislike'}));
    await user.click(screen.getByRole('button', {name: 'Like'}));

    await waitFor(() => expect(voteApi.castVotes).toHaveBeenCalledTimes(1));
    expect(voteApi.castVotes).toHaveBeenCalledWith('tok1', expect.objectContaining({
        votes: [
            {activityId: 'a1', liked: false},
            {activityId: 'a2', liked: true},
        ],
    }));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern=ActivityVotePage`
Expected: FAIL — no "Undo last swipe" button.

- [ ] **Step 3: Implement undo**

In `src/pages/vote/ActivityVotePage.js`, after `handleSwipe` add:

```js
const handleUndo = () => {
    if (votesRef.current.length === 0 || submittingRef.current) return;
    votesRef.current.pop();
    setCurrentIndex(i => Math.max(0, i - 1));
};
```

and pass it to `SwipeCard`:

```jsx
<SwipeCard
    cards={activities}
    currentIndex={currentIndex}
    onSwipe={handleSwipe}
    onUndo={handleUndo}
    canUndo={currentIndex > 0}
    title="Which activities are you up for?"
    subtitle="Swipe right to vote yes, left to skip"
    shareUrl={shareUrl}
    getCardLink={getCardLink}
/>
```

In `src/components/SwipeCard.js`, change the signature to
`function SwipeCard({ cards, currentIndex, onSwipe, onUndo, canUndo, title, subtitle, shareUrl, getCardLink })`
and in the `swipe-buttons` div render the undo button between dislike and like:

```jsx
<div className="swipe-buttons">
    <button
        className="swipe-btn swipe-btn-dislike"
        onClick={() => handleButtonSwipe('left')}
        aria-label="Dislike"
    >✕</button>
    {onUndo && (
        <button
            className="swipe-btn swipe-btn-undo"
            onClick={onUndo}
            disabled={!canUndo}
            aria-label="Undo last swipe"
        >↩</button>
    )}
    <button
        className="swipe-btn swipe-btn-like"
        onClick={() => handleButtonSwipe('right')}
        aria-label="Like"
    >♥</button>
</div>
```

In `src/components/SwipeCard.css`, next to the existing `.swipe-btn-like` / `.swipe-btn-dislike` rules add:

```css
/* Undo: visually secondary — smaller, neutral, between the two vote buttons. */
.swipe-btn-undo {
    width: 3rem;
    height: 3rem;
    font-size: 1.25rem;
    color: rgba(255, 255, 255, 0.85);
    border: 1px solid rgba(255, 255, 255, 0.4);
    background: transparent;
}

.swipe-btn-undo:disabled {
    opacity: 0.35;
    cursor: not-allowed;
}
```

(Check the existing `.swipe-btn` rule first — if it sets width/height, keep the undo override smaller than the like/dislike buttons.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern=ActivityVotePage`
Expected: PASS.

- [ ] **Step 5: Full suite + commit**

Run: `CI=true npx react-scripts test --watchAll=false` — expected PASS (SwipeCard is also used by category voting; the undo button only renders when `onUndo` is passed).

```bash
git add src/pages/vote/ActivityVotePage.js src/pages/vote/ActivityVotePage.test.js src/components/SwipeCard.js src/components/SwipeCard.css
git commit -m "feat(vote): undo last swipe in the activity vote deck"
```

---

### Task 10: Activity detail — zero-photo fallback + description sub-headings

**Files:**
- Create: `src/utils/descriptionBlocks.js`, `src/utils/descriptionBlocks.test.js`
- Create: `src/components/ActivityGallery.test.js`
- Modify: `src/components/ActivityGallery.js:39-41`
- Modify: `src/components/ActivityGallery.css` (placeholder rule)
- Modify: `src/pages/ActivityDetailPage.js:91-94, 159-168`
- Modify: `src/pages/ActivityDetailPage.css` (sub-heading rule)

**Interfaces:**
- Produces: `parseDescriptionBlocks(description: string) => Array<{type: 'heading'|'paragraph', text: string}>` — a line ending with `:` becomes a heading (colon stripped); other lines are paragraphs.
- Consumes: `DEFAULT_ACTIVITY_IMAGE` from `src/utils/format`.

- [ ] **Step 1: Write the failing tests**

Create `src/utils/descriptionBlocks.test.js`:

```js
import {parseDescriptionBlocks} from './descriptionBlocks';

test('lines ending with a colon become sub-headings', () => {
    const text = 'Intro paragraph.\nWhat to expect on the day:\nKarts and helmets.\nGood to know:\nBring a licence.';
    expect(parseDescriptionBlocks(text)).toEqual([
        {type: 'paragraph', text: 'Intro paragraph.'},
        {type: 'heading', text: 'What to expect on the day'},
        {type: 'paragraph', text: 'Karts and helmets.'},
        {type: 'heading', text: 'Good to know'},
        {type: 'paragraph', text: 'Bring a licence.'},
    ]);
});

test('empty and missing descriptions produce no blocks', () => {
    expect(parseDescriptionBlocks('')).toEqual([]);
    expect(parseDescriptionBlocks(null)).toEqual([]);
});
```

Create `src/components/ActivityGallery.test.js`:

```jsx
import {render, screen} from '@testing-library/react';
import ActivityGallery from './ActivityGallery';
import {DEFAULT_ACTIVITY_IMAGE} from '../utils/format';

test('renders a full-width fallback image when there are no photos', () => {
    render(<ActivityGallery images={[]} title="Karting"/>);
    const img = screen.getByAltText('Karting');
    expect(img).toHaveAttribute('src', DEFAULT_ACTIVITY_IMAGE);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false --testPathPattern="descriptionBlocks|ActivityGallery"`
Expected: FAIL — `descriptionBlocks` module missing; gallery renders `null` for empty images.

- [ ] **Step 3: Implement**

Create `src/utils/descriptionBlocks.js`:

```js
// Admin-entered descriptions are plain text; a line ending with ":" is treated
// as a sub-heading (mockup: "What to expect on the day", "Good to know").
export function parseDescriptionBlocks(description) {
    return (description || '')
        .split(/\n+/)
        .map(line => line.trim())
        .filter(Boolean)
        .map(line => line.endsWith(':')
            ? {type: 'heading', text: line.slice(0, -1).trim()}
            : {type: 'paragraph', text: line});
}
```

In `src/components/ActivityGallery.js`:

1. Add import: `import {DEFAULT_ACTIVITY_IMAGE} from '../utils/format';`
2. Replace lines 39-41 (`if (photos.length === 0) { return null; }`) with:

```jsx
if (photos.length === 0) {
    // Mockup data-count="0": single full-width image, no thumbnail grid, no lightbox.
    return (
        <div className="activity-gallery" data-count="0">
            <div className="ag-main ag-main--placeholder">
                <img src={DEFAULT_ACTIVITY_IMAGE} alt={title}/>
            </div>
        </div>
    );
}
```

In `src/components/ActivityGallery.css` add near the `.ag-main` rule:

```css
/* Placeholder main image (no photos): not clickable, no lightbox. */
.ag-main--placeholder {
    cursor: default;
}
```

In `src/pages/ActivityDetailPage.js`:

1. Add import: `import {parseDescriptionBlocks} from '../utils/descriptionBlocks';`
2. Replace lines 91-94 (`descriptionParagraphs` computation) with:

```js
const descriptionBlocks = parseDescriptionBlocks(activity.description);
```

3. Replace the description section (lines 159-168) with:

```jsx
{descriptionBlocks.length > 0 && (
    <section className="activity-detail-blk">
        <h2 className="activity-detail-blk-title">
            <i className="ph ph-note" aria-hidden="true"/> About this activity
        </h2>
        {descriptionBlocks.map(block => block.type === 'heading'
            ? <h3 className="activity-detail-subhead" key={block.text}>{block.text}</h3>
            : <p className="activity-detail-desc" key={block.text}>{block.text}</p>
        )}
    </section>
)}
```

In `src/pages/ActivityDetailPage.css` add after the `.activity-detail-desc` rule:

```css
.activity-detail-subhead {
    font-size: 1.0625rem;
    font-weight: 600;
    margin: 1.25rem 0 0.375rem;
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern="descriptionBlocks|ActivityGallery"`
Expected: PASS (3 tests).

- [ ] **Step 5: Full suite, visual check, commit**

Run: `CI=true npx react-scripts test --watchAll=false` — expected PASS.
`npm start`: open an activity detail page; description shows sub-headings for `...:` lines.

```bash
git add src/utils/descriptionBlocks.js src/utils/descriptionBlocks.test.js src/components/ActivityGallery.js src/components/ActivityGallery.test.js src/components/ActivityGallery.css src/pages/ActivityDetailPage.js src/pages/ActivityDetailPage.css
git commit -m "feat(activity): zero-photo fallback and description sub-headings per final mockup"
```

---

## Final verification (after all tasks)

- [ ] Run the whole suite: `cd myhive-react-app && CI=true npx react-scripts test --watchAll=false` — all PASS.
- [ ] `npm run build` succeeds.
- [ ] Manual pass at desktop + 390px: hero photo/headline/white buttons, logo, scrollbars, trip builder orange vote button, setup modal (no budget, slider), swipe undo, activity detail (white accents, orange added state).
