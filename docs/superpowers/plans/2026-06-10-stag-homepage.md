# Stag-Do Homepage Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the homepage with a six-section stag-do landing per the approved spec (`docs/superpowers/specs/2026-06-10-stag-homepage-design.md`), backed by a new admin-configurable `Activity.featured` flag.

**Architecture:** Backend adds a `featured` boolean to `Activity` (entity → DTO → service mapping → public `GET /activities?featured=true` filter → admin checkbox). Frontend rewrites `HomePage.js` into six sections built from small components under `src/components/home/`, reusing `ActivityCard` and `TripSetupModal` (vote mode); the vote-start logic is extracted from `TripBuilderDropdown` into a shared `useStartGroupVote` hook.

**Tech Stack:** Spring Boot 4.0 / Java 25 / JUnit 5 + Mockito + MockMvc (backend); React 19 / CRA / Jest + React Testing Library (frontend).

**Conventions reminders (CLAUDE.md):** no wildcard imports; braces always; `expected`-prefixed variables in tests; DTOs built inline in tests when asserting field values; backend tests required for changed code.

---

### Task 1: Backend — `featured` field on Activity (entity + DTO + mapping)

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java` (after `featuredWeight`, ~line 61)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java` (after `featuredWeight`, ~line 42)
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java` (`applyDtoToEntity` ~line 156, `convertToDTO` ~line 173)
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `ActivityServiceTest` (next to the existing `getActivityById_includesFeaturedWeight` / `updateActivity_persistsFeaturedWeight` tests at lines ~290–341, mirroring their structure):

```java
@Test
void getActivityById_includesFeatured() {
    Activity act = TestDataFactory.activity(destination);
    act.setFeatured(true);
    when(activityRepository.findById(act.getId())).thenReturn(Optional.of(act));

    ActivityDTO dto = activityService.getActivityById(act.getId());

    assertThat(dto.getFeatured()).isTrue();
}

@Test
void updateActivity_persistsFeatured() {
    Activity existing = TestDataFactory.activity(destination);
    existing.setFeatured(false);
    when(activityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

    ActivityDTO input = new ActivityDTO();
    input.setName(existing.getName());
    input.setPrice(existing.getPrice());
    input.setFeatured(true);

    activityService.updateActivity(existing.getId(), input);

    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(captor.capture());
    assertThat(captor.getValue().isFeatured()).isTrue();
}

@Test
void updateActivity_nullFeatured_defaultsToFalse() {
    Activity existing = TestDataFactory.activity(destination);
    existing.setFeatured(true);
    when(activityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

    ActivityDTO input = new ActivityDTO();
    input.setName(existing.getName());
    input.setPrice(existing.getPrice());
    input.setFeatured(null);

    activityService.updateActivity(existing.getId(), input);

    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(activityRepository).save(captor.capture());
    assertThat(captor.getValue().isFeatured()).isFalse();
}
```

Note: the existing `updateActivity_persistsFeaturedWeight` test shows the exact mocking pattern used in this file (it may also mock `destinationRepository`/`categoryRepository` — copy whatever it does so `applyDtoToEntity` doesn't NPE; `CategoryResolver.resolve` with empty `categoryIds` does not hit the repository).

- [ ] **Step 2: Run tests to verify they fail to compile**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: compilation error — `setFeatured`/`isFeatured`/`getFeatured` not defined.

- [ ] **Step 3: Implement the field and mapping**

`Activity.java` — after the `featuredWeight` field (same column-default pattern, safe for prod `ddl-auto=update`):

```java
@Column(nullable = false, columnDefinition = "boolean default false")
private boolean featured = false;
```

`ActivityDTO.java` — after `featuredWeight`:

```java
private Boolean featured;
```

`ActivityService.applyDtoToEntity` — after the `setFeaturedWeight` line:

```java
activity.setFeatured(Boolean.TRUE.equals(dto.getFeatured()));
```

`ActivityService.convertToDTO` — after the `setFeaturedWeight` line:

```java
dto.setFeatured(activity.isFeatured());
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest'`
Expected: PASS (all, including pre-existing tests).

- [ ] **Step 5: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/entity/Activity.java myhive-backend/src/main/java/com/myhive/backend/dto/ActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java
git commit -m "feat: add featured flag to Activity entity and DTO"
```

---

### Task 2: Backend — featured filter on public `GET /activities`

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/ActivityController.java:22-36`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java`
- Test: `myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing service tests**

Add to `ActivityServiceTest`:

```java
@Test
void getFeaturedActivities_noCategory_returnsFeaturedOnly() {
    when(activityRepository.findByFeaturedTrueOrderByNameAsc()).thenReturn(List.of(activity));

    List<ActivityDTO> result = activityService.getFeaturedActivities(null);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getName()).isEqualTo(activity.getName());
}

@Test
void getFeaturedActivities_withCategory_usesCombinedQuery() {
    String expectedCategorySlug = "nightlife";
    when(activityRepository.findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(expectedCategorySlug))
            .thenReturn(List.of(activity));

    List<ActivityDTO> result = activityService.getFeaturedActivities(expectedCategorySlug);

    assertThat(result).hasSize(1);
    verify(activityRepository).findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(expectedCategorySlug);
}
```

- [ ] **Step 2: Write the failing controller integration test**

Add to `PublicControllerIntegrationTest` (the `setUp` activity "temple-visit" stays non-featured):

```java
@Test
void getActivities_featuredTrue_returnsOnlyFeatured() throws Exception {
    Destination dest = destinationRepository.findById(destinationId).orElseThrow();
    Activity featuredActivity = new Activity();
    featuredActivity.setSlug("featured-act");
    featuredActivity.setDestination(dest);
    featuredActivity.setName("Featured Act");
    featuredActivity.setPrice(new BigDecimal("99.00"));
    featuredActivity.setFeatured(true);
    activityRepository.save(featuredActivity);

    mockMvc.perform(get("/activities").param("featured", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].slug", is("featured-act")))
            .andExpect(jsonPath("$[0].featured", is(true)));
}

@Test
void getActivities_withoutFeaturedParam_returnsAllIncludingNonFeatured() throws Exception {
    mockMvc.perform(get("/activities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].featured", is(false)));
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest' --tests '*PublicControllerIntegrationTest'`
Expected: compilation error — `getFeaturedActivities` / repository methods not defined.

- [ ] **Step 4: Implement repository, service, controller**

`ActivityRepository.java` — add derived queries after `findByDestinationIdAndCategoriesSlug` (~line 28):

```java
List<Activity> findByFeaturedTrueOrderByNameAsc();

List<Activity> findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(String categorySlug);
```

`ActivityService.java` — add after `getActivitiesByDestinationAndCategorySlug`:

```java
public List<ActivityDTO> getFeaturedActivities(String categorySlug) {
    List<Activity> featuredActivities = categorySlug == null
            ? activityRepository.findByFeaturedTrueOrderByNameAsc()
            : activityRepository.findByFeaturedTrueAndCategoriesSlugOrderByNameAsc(categorySlug);
    return featuredActivities.stream()
            .map(this::convertToDTO)
            .toList();
}
```

`ActivityController.getAllActivities` — add the `featured` param and check it first:

```java
@GetMapping
public ResponseEntity<List<ActivityDTO>> getAllActivities(
        @RequestParam(required = false) UUID destinationId,
        @RequestParam(required = false) String categorySlug,
        @RequestParam(required = false, defaultValue = "false") boolean featured) {

    if (featured) {
        return ResponseEntity.ok(activityService.getFeaturedActivities(categorySlug));
    }
    if (destinationId != null && categorySlug != null) {
        return ResponseEntity.ok(activityService.getActivitiesByDestinationAndCategorySlug(destinationId, categorySlug));
    } else if (destinationId != null) {
        return ResponseEntity.ok(activityService.getActivitiesByDestination(destinationId));
    } else if (categorySlug != null) {
        return ResponseEntity.ok(activityService.getActivitiesByCategorySlug(categorySlug));
    } else {
        return ResponseEntity.ok(activityService.getAllActivities());
    }
}
```

(Note: `ActivityController` currently uses a wildcard import `org.springframework.web.bind.annotation.*` — leave existing imports as-is, but do not add new wildcard imports.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd myhive-backend && ./gradlew test --tests '*ActivityServiceTest' --tests '*PublicControllerIntegrationTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java myhive-backend/src/main/java/com/myhive/backend/service/ActivityService.java myhive-backend/src/main/java/com/myhive/backend/controller/ActivityController.java myhive-backend/src/test/java/com/myhive/backend/service/ActivityServiceTest.java myhive-backend/src/test/java/com/myhive/backend/controller/PublicControllerIntegrationTest.java
git commit -m "feat: featured filter on public GET /activities"
```

---

### Task 3: Backend — featured sample data for dev

**Files:**
- Modify: `myhive-backend/src/main/resources/data.sql` (append at end of file)

- [ ] **Step 1: Append featured updates**

```sql
-- Featured activities shown on the homepage grid
UPDATE activities SET featured = TRUE WHERE slug IN (
    'prague-pub-crawl', 'beer-tasting-experience', 'absinth-bar-experience',
    'nightclub-vip-experience', 'rooftop-jazz-night', 'segway-city-tour',
    'e-scooter-adventure', 'kayaking-on-the-vltava', 'underground-bunker-tour',
    'hot-air-balloon-ride', 'jet-ski-adventure', 'sunset-boat-party'
);
```

(These 12 slugs all exist in `data.sql`; verify with a quick grep before committing — if any slug is missing, swap it for another existing one so exactly 12 rows are featured.)

- [ ] **Step 2: Verify dev profile boots and serves featured activities**

Run: `cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` (in background), then `curl "http://localhost:8080/activities?featured=true"`.
Expected: JSON array of 12 activities, each with `"featured": true`. Stop the server afterwards.

- [ ] **Step 3: Commit**

```bash
git add myhive-backend/src/main/resources/data.sql
git commit -m "feat: mark sample activities as featured in dev data"
```

---

### Task 4: Admin UI — featured checkbox + table column

**Files:**
- Modify: `myhive-react-app/src/pages/AdminActivities.js`

No automated tests exist for admin pages; manual verification step below (CLAUDE.md requires backend tests, which Tasks 1–2 cover).

- [ ] **Step 1: Add `featured` to the form model**

In `EMPTY_FORM` (line ~11): add `featured: false,` after `featuredWeight: 0,`.

In `COLUMNS` (line ~24): add `{key: 'featured', label: 'Featured'},` after the `featuredWeight` entry.

In `mapItemToForm` (line ~56): add `featured: a.featured ?? false,` after the `featuredWeight` line.

(`buildPayload` spreads `...form`, so the boolean passes through unchanged — no edit needed.)

- [ ] **Step 2: Render the column and the switch**

In `renderRow`, after the `featuredWeight` cell (`<td className="small">{activity.featuredWeight ?? 0}</td>`):

```jsx
<td className="small">{activity.featured ? '✓' : '—'}</td>
```

In the modal form, after the «Featured weight» `Form.Group`:

```jsx
<Form.Group className="mb-3">
    <Form.Check
        type="switch"
        id="featured-on-homepage"
        label="Featured on homepage"
        className="text-white"
        checked={!!form.featured}
        onChange={e => setForm({...form, featured: e.target.checked})}
    />
</Form.Group>
```

- [ ] **Step 3: Manual verification**

Run backend (`dev` profile) + `cd myhive-react-app && npm start`. In `/admin/activities`: edit an activity, toggle «Featured on homepage», save, confirm the ✓ appears in the table and survives a refresh.

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/pages/AdminActivities.js
git commit -m "feat: featured-on-homepage toggle in admin activities"
```

---

### Task 5: Frontend — API method + contact-link constants

**Files:**
- Modify: `myhive-react-app/src/services/api.js` (after `getActivitiesPaged`, ~line 48)
- Modify: `myhive-react-app/src/services/config.js`

- [ ] **Step 1: Add `getFeaturedActivities` to `api.js`**

```js
async getFeaturedActivities() {
    const response = await fetch(`${API_BASE_URL}/activities?featured=true`);
    if (!response.ok) throw new Error('Failed to fetch featured activities');
    return response.json();
},
```

- [ ] **Step 2: Add support-link constants to `config.js`**

```js
// Placeholder support links until the real WhatsApp number / FB page are provided
export const WHATSAPP_URL = 'https://wa.me/0000000000';
export const MESSENGER_URL = 'https://m.me/trivlu';
```

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/services/api.js myhive-react-app/src/services/config.js
git commit -m "feat: featured activities API method and support link config"
```

---

### Task 6: Frontend — `useStartGroupVote` hook + TripBuilderDropdown refactor

**Files:**
- Create: `myhive-react-app/src/hooks/useStartGroupVote.js`
- Modify: `myhive-react-app/src/components/TripBuilderDropdown.js:10-27`
- Test: `myhive-react-app/src/hooks/useStartGroupVote.test.js`

- [ ] **Step 1: Write the failing hook test**

```jsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AppContext } from '../context/AppContext';
import { useStartGroupVote } from './useStartGroupVote';

function QuizStub() {
  const location = useLocation();
  return <div>quiz page for {location.state?.setup?.destination?.slug}</div>;
}

function Harness() {
  const { voteSetupOpen, openVoteSetup, handleVoteConfirm } = useStartGroupVote();
  return (
    <div>
      <span data-testid="open-state">{voteSetupOpen ? 'open' : 'closed'}</span>
      <button onClick={openVoteSetup}>open setup</button>
      <button
        onClick={() =>
          handleVoteConfirm({
            travelers: 4,
            startDate: '2026-08-01',
            endDate: '2026-08-03',
            email: 'a@b.c',
            destination: { id: 'd1', slug: 'prague' },
            budget: null,
          })
        }
      >
        confirm
      </button>
    </div>
  );
}

function renderHarness(dispatch = jest.fn()) {
  const state = { tripItems: [], destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }] };
  return render(
    <AppContext.Provider value={{ state, dispatch }}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Harness />} />
          <Route path="/vote/new/quiz" element={<QuizStub />} />
        </Routes>
      </MemoryRouter>
    </AppContext.Provider>
  );
}

test('openVoteSetup flips voteSetupOpen', async () => {
  renderHarness();

  expect(screen.getByTestId('open-state')).toHaveTextContent('closed');
  await userEvent.click(screen.getByText('open setup'));
  expect(screen.getByTestId('open-state')).toHaveTextContent('open');
});

test('handleVoteConfirm navigates to the quiz with setup state', async () => {
  const dispatch = jest.fn();
  renderHarness(dispatch);

  await userEvent.click(screen.getByText('confirm'));

  expect(await screen.findByText('quiz page for prague')).toBeInTheDocument();
  expect(dispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd myhive-react-app && npx jest src/hooks/useStartGroupVote.test.js --watchAll=false`
Expected: FAIL — module `./useStartGroupVote` not found.

- [ ] **Step 3: Implement the hook**

`src/hooks/useStartGroupVote.js`:

```js
import {useContext, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';

/**
 * Shared entry point for starting a group vote: owns the vote-setup modal
 * open state and the confirm handler that launches the quiz flow.
 */
export function useStartGroupVote() {
    const {state, dispatch} = useContext(AppContext);
    const navigate = useNavigate();
    const [voteSetupOpen, setVoteSetupOpen] = useState(false);

    const destSlug = state.tripItems.find(i => i.destinationSlug)?.destinationSlug;
    const preselectedDestination = state.destinations.find(d => d.slug === destSlug) || null;

    const openVoteSetup = () => setVoteSetupOpen(true);
    const closeVoteSetup = () => setVoteSetupOpen(false);

    const handleVoteConfirm = ({travelers, startDate, endDate, email, destination, budget}) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate('/vote/new/quiz', {
            state: {
                setup: {travelers, startDate, endDate, email, destination, budget},
            },
        });
    };

    return {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination};
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd myhive-react-app && npx jest src/hooks/useStartGroupVote.test.js --watchAll=false`
Expected: PASS.

- [ ] **Step 5: Refactor TripBuilderDropdown to use the hook**

In `TripBuilderDropdown.js` replace lines 10–27 (the `voteSetupOpen` state, `handleVoteClick`, `destSlug`/`preselectedDestination` computation, and `handleVoteConfirm`) with:

```js
const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();
```

Add the import `import {useStartGroupVote} from '../hooks/useStartGroupVote';`, change `useState` import back to just `useContext` (drop `useState` and `useNavigate` if now unused), replace both `onClick={handleVoteClick}` with `onClick={openVoteSetup}` and both `onVoteCancel={() => setVoteSetupOpen(false)}` with `onVoteCancel={closeVoteSetup}`.

Keep the early return `if (!state.tripBuilderModalOpen) return null;` AFTER the hook call (hooks must run unconditionally).

- [ ] **Step 6: Run the full frontend suite**

Run: `cd myhive-react-app && npx jest --watchAll=false`
Expected: PASS (no regressions in vote-flow tests).

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/hooks/useStartGroupVote.js myhive-react-app/src/hooks/useStartGroupVote.test.js myhive-react-app/src/components/TripBuilderDropdown.js
git commit -m "refactor: extract useStartGroupVote hook from TripBuilderDropdown"
```

---

### Task 7: Frontend — static homepage sections (Trust, How It Works, How Booking Works, Reviews)

**Files:**
- Create: `myhive-react-app/src/components/home/TrustBar.js` + `TrustBar.css`
- Create: `myhive-react-app/src/components/home/HowItWorksSection.js` + `HowItWorksSection.css`
- Create: `myhive-react-app/src/components/home/HowBookingWorksSection.js` + `HowBookingWorksSection.css`
- Create: `myhive-react-app/src/components/home/ReviewsSection.js` + `ReviewsSection.css`

All four are presentational; they are covered by the HomePage test in Task 9. Copy below is verbatim from the ТЗ.

- [ ] **Step 1: TrustBar**

`TrustBar.js`:

```jsx
import './TrustBar.css';

const TRUST_ITEMS = [
    {icon: '🏆', title: 'Stag Do Specialists', text: "We've done this thousands of times"},
    {icon: '🗳️', title: 'Group Voted Itinerary', text: 'Built on what your mates actually want'},
    {icon: '✅', title: 'We Handle Everything', text: 'Booking, logistics, support'},
    {icon: '💬', title: 'Real Human Support', text: 'WhatsApp & chat, 7 days a week'},
];

function TrustBar() {
    return (
        <section className="trust-bar">
            {TRUST_ITEMS.map(item => (
                <div key={item.title} className="trust-item">
                    <span className="trust-icon" aria-hidden="true">{item.icon}</span>
                    <h3 className="trust-title">{item.title}</h3>
                    <p className="trust-text">{item.text}</p>
                </div>
            ))}
        </section>
    );
}

export default TrustBar;
```

`TrustBar.css`:

```css
.trust-bar {
    display: flex;
    justify-content: center;
    gap: var(--gap-xl);
    padding: var(--gap-lg) var(--page-padding);
    background: var(--bg);
    border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    flex-wrap: wrap;
}

.trust-item {
    text-align: center;
    max-width: 14rem;
}

.trust-icon {
    font-size: 1.75rem;
    display: block;
    margin-bottom: 0.25rem;
}

.trust-title {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text);
    margin-bottom: 0.125rem;
}

.trust-text {
    font-size: 0.875rem;
    color: var(--text);
    opacity: 0.75;
    margin: 0;
}

@media (max-width: 768px) {
    .trust-bar {
        flex-direction: column;
        align-items: center;
        gap: var(--gap);
    }
}
```

- [ ] **Step 2: HowItWorksSection** (accepts `onStartVote` callback)

`HowItWorksSection.js`:

```jsx
import './HowItWorksSection.css';

const STEPS = [
    {icon: '🎯', title: 'Define your stag style', text: 'wild or classy, chill or adrenaline'},
    {icon: '👆', title: 'Handpick the shortlist', text: 'pick what the group gets to vote on'},
    {icon: '🗳️', title: 'Send the vote link', text: 'your mates pick their favourites'},
    {icon: '✏️', title: 'Review & confirm', text: 'add, remove or tweak activities before you book'},
];

function HowItWorksSection({onStartVote}) {
    return (
        <section className="how-it-works">
            <h2 className="section-title">The Smartest Way to Plan a Stag Do</h2>
            <p className="section-subtitle">
                Our Trip Builder uses group voting to turn everyone's preferences into one perfect stag do package.
            </p>
            <div className="how-it-works-steps">
                {STEPS.map((step, index) => (
                    <div key={step.title} className="how-it-works-step">
                        <span className="step-number">{index + 1}</span>
                        <span className="step-icon" aria-hidden="true">{step.icon}</span>
                        <h3 className="step-title">{step.title}</h3>
                        <p className="step-text">{step.text}</p>
                    </div>
                ))}
            </div>
            <button className="btn btn--primary btn--lg" onClick={onStartVote}>
                Start Group Vote
            </button>
        </section>
    );
}

export default HowItWorksSection;
```

`HowItWorksSection.css`:

```css
.how-it-works {
    padding: var(--gap-xl) var(--page-padding);
    background: var(--bg);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--gap-lg);
    text-align: center;
}

.section-subtitle {
    font-size: 1rem;
    color: var(--text);
    opacity: 0.8;
    max-width: 40rem;
    margin: 0 auto;
}

.how-it-works-steps {
    display: flex;
    justify-content: center;
    gap: var(--gap-lg);
    flex-wrap: wrap;
}

.how-it-works-step {
    position: relative;
    max-width: 13rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.375rem;
}

/* dashed connector between steps on desktop */
.how-it-works-step:not(:last-child)::after {
    content: '';
    position: absolute;
    top: 1.25rem;
    left: 100%;
    width: var(--gap-lg);
    border-top: 2px dashed rgba(0, 0, 0, 0.25);
}

.step-number {
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 50%;
    background: var(--white);
    border: 2px solid var(--text);
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
}

.step-icon {
    font-size: 1.5rem;
}

.step-title {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text);
    margin: 0;
}

.step-text {
    font-size: 0.875rem;
    color: var(--text);
    opacity: 0.75;
    margin: 0;
}

@media (max-width: 768px) {
    .how-it-works-steps {
        flex-direction: column;
        align-items: center;
    }

    .how-it-works-step:not(:last-child)::after {
        display: none;
    }
}
```

- [ ] **Step 3: HowBookingWorksSection**

`HowBookingWorksSection.js`:

```jsx
import {MESSENGER_URL, WHATSAPP_URL} from '../../services/config';
import './HowBookingWorksSection.css';

const BOOKING_STEPS = [
    {icon: '🗳️', title: 'Vote & Confirm', text: 'your group votes on activities via Trip Builder'},
    {icon: '📝', title: 'Tweak the List', text: 'add or remove activities to fit your budget'},
    {icon: '🔒', title: 'Lock It In', text: '30% deposit secures the booking, rest paid closer to the date'},
];

function HowBookingWorksSection() {
    return (
        <section className="how-booking-works">
            <h2 className="section-title">How Booking Works</h2>
            <p className="section-subtitle">Vote, tweak, pay — your stag do decided in 10 minutes.</p>
            <div className="booking-steps">
                {BOOKING_STEPS.map(step => (
                    <div key={step.title} className="booking-step">
                        <span className="booking-step-icon" aria-hidden="true">{step.icon}</span>
                        <h3 className="booking-step-title">{step.title}</h3>
                        <p className="booking-step-text">{step.text}</p>
                    </div>
                ))}
            </div>
            <div className="booking-support">
                <p className="booking-support-text">Got questions? Contact us.</p>
                <div className="booking-support-buttons">
                    <a className="btn btn--primary" href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">
                        WhatsApp
                    </a>
                    <a className="btn btn--primary" href={MESSENGER_URL} target="_blank" rel="noopener noreferrer">
                        Facebook Messenger
                    </a>
                </div>
            </div>
        </section>
    );
}

export default HowBookingWorksSection;
```

`HowBookingWorksSection.css`:

```css
.how-booking-works {
    padding: var(--gap-xl) var(--page-padding);
    background: var(--white);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--gap-lg);
    text-align: center;
}

.booking-steps {
    display: flex;
    justify-content: center;
    gap: var(--gap-xl);
    flex-wrap: wrap;
}

.booking-step {
    max-width: 16rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.375rem;
}

.booking-step-icon {
    font-size: 1.75rem;
    width: 3.5rem;
    height: 3.5rem;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg);
    border-radius: 0.75rem;
}

.booking-step-title {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text);
    margin: 0;
}

.booking-step-text {
    font-size: 0.875rem;
    color: var(--text);
    opacity: 0.75;
    margin: 0;
}

.booking-support {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--gap);
}

.booking-support-text {
    font-weight: 600;
    margin: 0;
}

.booking-support-buttons {
    display: flex;
    gap: var(--gap);
    flex-wrap: wrap;
    justify-content: center;
}

@media (max-width: 768px) {
    .booking-steps {
        flex-direction: column;
        align-items: center;
    }
}
```

- [ ] **Step 4: ReviewsSection** (accepts `onStartVote`; dark-green styling after the stagweb reference)

`ReviewsSection.js`:

```jsx
import './ReviewsSection.css';

// Hardcoded until real reviews exist; replace content only, keep the shape.
const REVIEWS = [
    {
        quote: "Easiest stag do I've ever organised. The lads voted, Trivlu sorted the rest — all I did was show up.",
        name: 'James W.',
        country: 'United Kingdom',
    },
    {
        quote: 'Booked shooting, karting and a boat party for 14 guys. Zero chaos, brilliant weekend.',
        name: 'Connor M.',
        country: 'Ireland',
    },
    {
        quote: 'The group vote ended every argument in the group chat. 10/10, would use again.',
        name: 'Mark D.',
        country: 'United Kingdom',
    },
    {
        quote: 'Great communication and the itinerary was spot on. The deposit system made paying painless.',
        name: 'Tom V.',
        country: 'Netherlands',
    },
];

function initials(name) {
    return name.split(' ').map(part => part[0]).join('').toUpperCase();
}

function ReviewsSection({onStartVote}) {
    return (
        <section className="reviews-section">
            <h2 className="section-title reviews-title">What the Lads Say</h2>
            <p className="section-subtitle reviews-subtitle">Real reviews from real stag dos.</p>
            <div className="reviews-grid">
                {REVIEWS.map(review => (
                    <div key={review.name} className="review-card">
                        <div className="review-stars" aria-label="5 out of 5 stars">★★★★★</div>
                        <blockquote className="review-quote">"{review.quote}"</blockquote>
                        <div className="review-author">
                            <span className="review-avatar" aria-hidden="true">{initials(review.name)}</span>
                            <div>
                                <div className="review-name">{review.name}</div>
                                <div className="review-country">{review.country}</div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
            <button className="btn btn--primary btn--lg" onClick={onStartVote}>
                Build Your Trip
            </button>
        </section>
    );
}

export default ReviewsSection;
```

`ReviewsSection.css`:

```css
.reviews-section {
    padding: var(--gap-xl) var(--page-padding);
    background: #14342b;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--gap-lg);
    text-align: center;
}

.reviews-title,
.reviews-subtitle {
    color: var(--white);
}

.reviews-subtitle {
    opacity: 0.85;
}

.reviews-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
    gap: var(--gap-lg);
    width: 100%;
    max-width: 70rem;
}

.review-card {
    background: rgba(255, 255, 255, 0.08);
    border-radius: 0.75rem;
    padding: var(--gap-lg);
    text-align: left;
    display: flex;
    flex-direction: column;
    gap: var(--gap);
}

.review-stars {
    color: #b6e64c;
    letter-spacing: 0.2rem;
}

.review-quote {
    color: var(--white);
    font-style: italic;
    font-size: 0.9375rem;
    margin: 0;
    flex-grow: 1;
}

.review-avatar {
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.2);
    color: var(--white);
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 0.875rem;
}

.review-author {
    display: flex;
    align-items: center;
    gap: 0.75rem;
}

.review-name {
    color: var(--white);
    font-weight: 700;
    font-size: 0.875rem;
    text-transform: uppercase;
}

.review-country {
    color: var(--white);
    opacity: 0.7;
    font-size: 0.75rem;
}
```

- [ ] **Step 5: Commit**

```bash
git add myhive-react-app/src/components/home/
git commit -m "feat: static homepage sections (trust, how-it-works, booking, reviews)"
```

---

### Task 8: Frontend — FeaturedActivitiesSection

**Files:**
- Create: `myhive-react-app/src/components/home/FeaturedActivitiesSection.js` + `FeaturedActivitiesSection.css`

Covered by the HomePage test in Task 9.

- [ ] **Step 1: Implement the component**

`FeaturedActivitiesSection.js`:

```jsx
import {useContext, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import api from '../../services/api';
import {AppContext} from '../../context/AppContext';
import ActivityCard from '../ActivityCard';
import './FeaturedActivitiesSection.css';

const MAX_FEATURED = 12;

function FeaturedActivitiesSection() {
    const {state} = useContext(AppContext);
    const [activities, setActivities] = useState([]);

    useEffect(() => {
        let cancelled = false;
        api.getFeaturedActivities()
            .then(data => {
                if (!cancelled) {
                    setActivities((data || []).slice(0, MAX_FEATURED));
                }
            })
            .catch(() => {
                // The featured grid is optional on the homepage; keep it hidden on fetch failure.
            });
        return () => {
            cancelled = true;
        };
    }, []);

    if (activities.length === 0) {
        return null;
    }

    const mainDestination = state.destinations[0] || null;

    return (
        <section className="featured-activities" id="activities">
            <h2 className="section-title">70+ Activities. Something for Every Group.</h2>
            <p className="section-subtitle">
                From tank driving to strip clubs and spa — we've got every stag style covered.
            </p>
            <div className="featured-activities-grid">
                {activities.map(activity => (
                    <ActivityCard
                        key={activity.id}
                        activity={activity}
                        isAdded={state.tripItems.some(item => item.id === activity.id)}
                    />
                ))}
            </div>
            {mainDestination && (
                <Link to={`/destination/${mainDestination.slug}`} className="btn btn--primary btn--lg">
                    View All Activities
                </Link>
            )}
        </section>
    );
}

export default FeaturedActivitiesSection;
```

`FeaturedActivitiesSection.css`:

```css
.featured-activities {
    padding: var(--gap-xl) var(--page-padding);
    background: var(--bg);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--gap-lg);
    text-align: center;
    scroll-margin-top: var(--header-height);
}

.featured-activities-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: var(--gap-lg);
    width: 100%;
    max-width: 80rem;
    text-align: left;
}

@media (max-width: 1024px) {
    .featured-activities-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (max-width: 768px) {
    .featured-activities-grid {
        grid-template-columns: repeat(2, 1fr);
        gap: var(--gap);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/components/home/FeaturedActivitiesSection.js myhive-react-app/src/components/home/FeaturedActivitiesSection.css
git commit -m "feat: featured activities homepage section"
```

---

### Task 9: Frontend — assemble the new HomePage (+ Footer anchor fix)

**Files:**
- Modify: `myhive-react-app/src/pages/HomePage.js` (full rewrite)
- Modify: `myhive-react-app/src/pages/HomePage.css` (replace destinations styles)
- Modify: `myhive-react-app/src/components/Footer.js:16` (anchor)
- Modify: `myhive-react-app/src/App.test.js` (mock new api method)
- Test: `myhive-react-app/src/pages/HomePage.test.js` (new)

- [ ] **Step 1: Write the failing HomePage test**

`src/pages/HomePage.test.js`:

```jsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import HomePage from './HomePage';
import api from '../services/api';
import { AppContext } from '../context/AppContext';

jest.mock('../services/api');

beforeEach(() => {
  // jsdom does not implement media playback; the hero video autoplays.
  jest.spyOn(window.HTMLMediaElement.prototype, 'play').mockResolvedValue();
});

const baseState = {
  destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  tripItems: [],
  loading: false,
  error: null,
};

function renderHome(state = baseState) {
  return render(
    <HelmetProvider>
      <AppContext.Provider value={{ state, dispatch: jest.fn() }}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </AppContext.Provider>
    </HelmetProvider>
  );
}

test('renders all homepage sections', async () => {
  api.getFeaturedActivities.mockResolvedValue([
    { id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: [] },
  ]);

  renderHome();

  expect(screen.getByText('The Easiest Stag Do Decision. All Sorted For You.')).toBeInTheDocument();
  expect(screen.getByText('Stag Do Specialists')).toBeInTheDocument();
  expect(screen.getByText('The Smartest Way to Plan a Stag Do')).toBeInTheDocument();
  expect(await screen.findByText('Go-Karting')).toBeInTheDocument();
  expect(screen.getByText('View All Activities')).toHaveAttribute('href', '/destination/prague');
  expect(screen.getByText('How Booking Works')).toBeInTheDocument();
  expect(screen.getByText('What the Lads Say')).toBeInTheDocument();
});

test('hides the activities section when no featured activities exist', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  // Reviews section renders, so the page is done mounting.
  expect(await screen.findByText('What the Lads Say')).toBeInTheDocument();
  expect(screen.queryByText('70+ Activities. Something for Every Group.')).not.toBeInTheDocument();
});

test('Start Group Vote opens the vote setup modal', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await userEvent.click(screen.getAllByText('Start Group Vote')[0]);

  // TripSetupModal in vote mode shows the vote-specific confirm button.
  expect(await screen.findByText('Continue to Categories')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd myhive-react-app && npx jest src/pages/HomePage.test.js --watchAll=false`
Expected: FAIL — old homepage renders «Epic Weekend of Freedom», not the new sections.

- [ ] **Step 3: Rewrite HomePage.js**

```jsx
import {useEffect, useRef} from 'react';
import {Helmet} from 'react-helmet-async';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import TripSetupModal from '../components/TripSetupModal';
import TrustBar from '../components/home/TrustBar';
import HowItWorksSection from '../components/home/HowItWorksSection';
import FeaturedActivitiesSection from '../components/home/FeaturedActivitiesSection';
import HowBookingWorksSection from '../components/home/HowBookingWorksSection';
import ReviewsSection from '../components/home/ReviewsSection';
import {SITE_URL} from '../services/config';
import './HomePage.css';

function HomePage() {
    const videoRef = useRef(null);
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();

    useEffect(() => {
        const video = videoRef.current;
        if (!video) {
            return;
        }
        video.play().catch(() => {});
    }, []);

    return (
        <div className="homepage">
            <Helmet>
                <title>Trivlu — The Easiest Stag Do Decision. All Sorted For You.</title>
                <meta name="description"
                      content="Your mates vote in 10 minutes. We deliver the perfect stag do weekend — activities, booking and logistics all sorted for you."/>
                <link rel="canonical" href={`${SITE_URL}/`}/>
            </Helmet>

            <section className="hero">
                <video ref={videoRef} autoPlay muted loop playsInline className="hero-video">
                    <source src="https://res.cloudinary.com/dfhvltbjz/video/upload/ac_none,q_auto/v1758716526/panorama_sqshpf.mp4" type="video/mp4"/>
                    Your browser does not support the video tag.
                </video>
                <div className="hero-content">
                    <h1 className="hero-title">The Easiest Stag Do Decision. All Sorted For You.</h1>
                    <p className="hero-subtitle">
                        Your mates vote in 10 minutes. We deliver the perfect weekend.
                    </p>
                    <button className="btn btn--primary btn--lg" onClick={openVoteSetup}>
                        Start Group Vote
                    </button>
                </div>
            </section>

            <TrustBar/>
            <HowItWorksSection onStartVote={openVoteSetup}/>
            <FeaturedActivitiesSection/>
            <HowBookingWorksSection/>
            <ReviewsSection onStartVote={openVoteSetup}/>

            <TripSetupModal
                isVoteMode={true}
                voteOpen={voteSetupOpen}
                onVoteConfirm={handleVoteConfirm}
                onVoteCancel={closeVoteSetup}
                preselectedDestination={preselectedDestination}
            />
        </div>
    );
}

export default HomePage;
```

- [ ] **Step 4: Update HomePage.css**

Keep the `.hero*` rules (lines 1–49) unchanged. Replace everything from `/* Destinations Section */` (line 51) to the end with:

```css
/* Shared section typography */
.section-title {
    text-align: center;
    font-size: clamp(1.25rem, 3vw, 1.75rem);
    font-weight: 600;
    color: var(--text);
}
```

(`.section-subtitle` lives in `HowItWorksSection.css`, which every section that needs it imports transitively — it is defined once there to stay DRY.)

- [ ] **Step 5: Fix the Footer anchor**

In `Footer.js` line 16 replace:

```jsx
<a href="#destinations">Destinations</a>
```

with:

```jsx
<a href="/#activities">Activities</a>
```

- [ ] **Step 6: Update App.test.js**

After `api.getActivities.mockResolvedValue([]);` (line 14) add:

```js
api.getFeaturedActivities.mockResolvedValue([]);
```

- [ ] **Step 7: Run the full frontend suite**

Run: `cd myhive-react-app && npx jest --watchAll=false`
Expected: PASS (HomePage tests green, App.test.js green, no regressions).

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/pages/HomePage.js myhive-react-app/src/pages/HomePage.css myhive-react-app/src/pages/HomePage.test.js myhive-react-app/src/components/Footer.js myhive-react-app/src/App.test.js
git commit -m "feat: stag-do homepage with six landing sections"
```

---

### Task 10: Full verification + visual check

- [ ] **Step 1: Backend full suite**

Run: `cd myhive-backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Frontend full suite**

Run: `cd myhive-react-app && npx jest --watchAll=false`
Expected: all suites pass.

- [ ] **Step 3: Visual check in the browser**

Start backend (`./gradlew bootRun --args='--spring.profiles.active=dev'`) and frontend (`npm start`), open `http://localhost:3000`:
- Hero shows new headline + «Start Group Vote» opens the vote setup modal; completing it lands on `/vote/new/quiz`.
- Trust bar, 4 how-it-works steps, 12 activity cards, booking steps with WhatsApp/Messenger buttons, dark reviews section all render.
- «View All Activities» navigates to `/destination/prague`.
- Footer «Activities» link scrolls to the grid.
- Mobile width (~375px): sections stack vertically.

- [ ] **Step 4: Code review**

Per CLAUDE.md, run a code review over the branch diff before declaring done (use the project's `/code-review` flow).

**After user approval (per CLAUDE.md):** update `README.md` and memory files (`project_overview.md`) — new `featured` flag, `?featured=true` endpoint param, homepage structure.
