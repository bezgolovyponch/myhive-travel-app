# Activity preview modal in the vote flow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "click activity name → navigate to the activity page in a new tab" behavior in the Tinder-style vote flow with an informational popup that keeps the user in the swipe / trip-builder flow.

**Architecture:** A new presentational `ActivityPreviewModal` (built on the existing global `.app-modal*` classes) shows image + name + meta + description and an optional "View full page ↗" link. Two call sites open it: `SwipeCard` (owns local `infoCard` state) and the `CuratePage` finalize list (owns local `selected` state). A small backend change adds `description` + `duration` to `VotePoolActivityDTO` so the organizer (curate) flow has those values to show; the participant flow (`VoteActivityResponse`) already ships them.

**Tech Stack:** React 19 (CRA, Jest + React Testing Library), Spring Boot 4 / Java 25 (Gradle, JUnit 5 + AssertJ, `@DataJpaTest` with H2).

**Spec:** `docs/superpowers/specs/2026-06-01-vote-activity-preview-modal-design.md`

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `myhive-backend/.../dto/VotePoolActivityDTO.java` | Pool DTO shape | Modify — add `description`, `duration` |
| `myhive-backend/.../service/VotePoolService.java` | Build pool DTOs | Modify — populate new fields in `toDTO` |
| `myhive-backend/.../service/VotePoolServiceTest.java` | Pool service tests | Modify — assert new fields |
| `myhive-react-app/src/components/ActivityPreviewModal.js` | Info popup (display only) | Create |
| `myhive-react-app/src/components/ActivityPreviewModal.css` | Popup-specific styling | Create |
| `myhive-react-app/src/components/ActivityPreviewModal.test.js` | Popup unit tests | Create |
| `myhive-react-app/src/components/SwipeCard.js` | Swipe deck; opens popup on name click | Modify |
| `myhive-react-app/src/components/SwipeCard.css` | Name-trigger button reset | Modify |
| `myhive-react-app/src/components/SwipeCard.test.js` | Swipe name-click test | Create |
| `myhive-react-app/src/pages/vote/CuratePage.js` | Finalize list; opens popup on name click | Modify |
| `myhive-react-app/src/pages/vote/CuratePage.css` | Finalize name-trigger button reset | Modify |
| `myhive-react-app/src/pages/vote/CuratePage.test.js` | Finalize tests | Modify — fix stale assertion + add modal test |

**Windows note:** backend commands below use `.\gradlew.bat` (PowerShell). Run backend commands from `myhive-backend`, frontend commands from `myhive-react-app`.

---

## Task 1: Backend — add `description` + `duration` to the pool DTO

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolActivityDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/VotePoolService.java:61-71`
- Test: `myhive-backend/src/test/java/com/myhive/backend/service/VotePoolServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `VotePoolServiceTest` (after `buildPool_cappedAtTwenty`, before the `// ---- helpers ----` line). It builds a single-activity pool and asserts the two new fields map through. `getDescription()` / `getDuration()` do not exist yet, so this is the red.

```java
    @Test
    void buildPool_mapsDescriptionAndDuration() {
        Destination destination = saveDestination("Prague");
        Category nightlife = saveCategory("Nightlife", "nightlife", true);
        attachCategoryToDestination(destination, nightlife);

        String expectedDescription = "All-night guided club crawl.";
        Integer expectedDuration = 240;
        Activity activity = saveActivity(destination, "Club Crawl", new BigDecimal("100"), 5, Set.of(nightlife));
        activity.setDescription(expectedDescription);
        activity.setDuration(expectedDuration);
        activityRepository.saveAndFlush(activity);

        VotePoolResponse response = votePoolService.buildPool(
                new VotePoolRequest(destination.getId(), List.of()));

        assertThat(response.getPool()).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getDescription()).isEqualTo(expectedDescription);
                    assertThat(dto.getDuration()).isEqualTo(expectedDuration);
                });
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat test --tests "*VotePoolServiceTest"`
Expected: FAILS to compile — `cannot find symbol: method getDescription()` / `getDuration()` on `VotePoolActivityDTO`.

- [ ] **Step 3: Add the two fields to the DTO**

Edit `VotePoolActivityDTO.java`. Add `description` and `duration` to the end of the field list (the `@AllArgsConstructor` is positional — appending keeps existing arguments in place):

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotePoolActivityDTO {

    private UUID activityId;
    private String name;
    private BigDecimal price;
    private String imageUrl;
    private String slug;
    private String destinationSlug;
    private List<String> categories;
    private String description;
    private Integer duration;
}
```

- [ ] **Step 4: Populate the new fields in the service**

Edit `VotePoolService.toDTO` (`VotePoolService.java:61-71`). Append the two new constructor arguments in the same order as the DTO fields:

```java
    private VotePoolActivityDTO toDTO(Activity activity) {
        List<String> categories = activity.getCategories().stream()
                .map(Category::getName)
                .sorted()
                .toList();
        String destinationSlug = activity.getDestination() == null
                ? null : activity.getDestination().getSlug();
        return new VotePoolActivityDTO(activity.getId(), activity.getName(),
                activity.getPrice(), activity.getImageUrl(),
                activity.getSlug(), destinationSlug, categories,
                activity.getDescription(), activity.getDuration());
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `.\gradlew.bat test --tests "*VotePoolServiceTest"`
Expected: PASS (all `VotePoolServiceTest` methods, including the new one).

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/VotePoolActivityDTO.java myhive-backend/src/main/java/com/myhive/backend/service/VotePoolService.java myhive-backend/src/test/java/com/myhive/backend/service/VotePoolServiceTest.java
git commit -m "feat: expose description and duration on vote pool DTO"
```

---

## Task 2: Frontend — create the `ActivityPreviewModal` component

**Files:**
- Create: `myhive-react-app/src/components/ActivityPreviewModal.js`
- Create: `myhive-react-app/src/components/ActivityPreviewModal.css`
- Test: `myhive-react-app/src/components/ActivityPreviewModal.test.js`

- [ ] **Step 1: Write the failing test**

Create `ActivityPreviewModal.test.js`:

```jsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActivityPreviewModal from './ActivityPreviewModal';

const activity = {
  name: 'Snorkeling Tour',
  price: 45,
  duration: 180,
  categories: ['Water', 'Nature'],
  imageUrl: 'http://img/snorkel.jpg',
  description: 'Explore the coral reefs with a guide.',
};

test('renders nothing when activity is null', () => {
  const { container } = render(
    <ActivityPreviewModal activity={null} link={null} onClose={jest.fn()} />
  );
  expect(container).toBeEmptyDOMElement();
});

test('shows name, meta and description', () => {
  render(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);

  expect(screen.getByRole('heading', { name: 'Snorkeling Tour' })).toBeInTheDocument();
  expect(screen.getByText(/€45\/person/)).toBeInTheDocument();
  expect(screen.getByText(/3h/)).toBeInTheDocument();
  expect(screen.getByText(/Water · Nature/)).toBeInTheDocument();
  expect(screen.getByText('Explore the coral reefs with a guide.')).toBeInTheDocument();
});

test('shows a muted placeholder when there is no description', () => {
  render(
    <ActivityPreviewModal activity={{ ...activity, description: '' }} link={null} onClose={jest.fn()} />
  );
  expect(screen.getByText(/No description yet/i)).toBeInTheDocument();
});

test('shows the View full page link only when link is provided', () => {
  const { rerender } = render(
    <ActivityPreviewModal activity={activity} link="/destination/bali/activity/snorkel" onClose={jest.fn()} />
  );
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/snorkel');

  rerender(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);
  expect(screen.queryByRole('link', { name: /View full page/i })).not.toBeInTheDocument();
});

test('calls onClose on close button, backdrop click, and Escape', async () => {
  const onClose = jest.fn();
  render(<ActivityPreviewModal activity={activity} link={null} onClose={onClose} />);

  await userEvent.click(screen.getByRole('button', { name: /close/i }));
  expect(onClose).toHaveBeenCalledTimes(1);

  await userEvent.click(screen.getByRole('dialog'));   // backdrop
  expect(onClose).toHaveBeenCalledTimes(2);

  await userEvent.keyboard('{Escape}');
  expect(onClose).toHaveBeenCalledTimes(3);
});

test('clicking inside the content does not close', async () => {
  const onClose = jest.fn();
  render(<ActivityPreviewModal activity={activity} link={null} onClose={onClose} />);

  await userEvent.click(screen.getByRole('heading', { name: 'Snorkeling Tour' }));
  expect(onClose).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false ActivityPreviewModal`
Expected: FAIL — `Cannot find module './ActivityPreviewModal'`.

- [ ] **Step 3: Create the component**

Create `ActivityPreviewModal.js`:

```jsx
import { useEffect } from 'react';
import './ActivityPreviewModal.css';

function ActivityPreviewModal({ activity, link, onClose }) {
    useEffect(() => {
        if (!activity) {
            return undefined;
        }
        const handleKey = (e) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };
        document.addEventListener('keydown', handleKey);
        return () => document.removeEventListener('keydown', handleKey);
    }, [activity, onClose]);

    if (!activity) {
        return null;
    }

    const meta = [];
    if (activity.price != null) {
        meta.push(`€${activity.price}/person`);
    }
    if (activity.duration) {
        meta.push(`${Math.round(activity.duration / 60)}h`);
    }
    if (activity.categories && activity.categories.length > 0) {
        meta.push(activity.categories.join(' · '));
    }

    return (
        <div
            className="app-modal activity-preview-modal"
            role="dialog"
            aria-modal="true"
            aria-label={activity.name}
            onClick={onClose}
        >
            <div className="app-modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="app-modal-header">
                    <h2>{activity.name}</h2>
                    <button className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>
                <div className="app-modal-body">
                    {activity.imageUrl && (
                        <img src={activity.imageUrl} alt={activity.name} className="activity-preview-image" />
                    )}
                    {meta.length > 0 && (
                        <div className="activity-preview-meta">{meta.join(' · ')}</div>
                    )}
                    <div className="activity-preview-description">
                        {activity.description
                            ? activity.description
                            : <span className="activity-preview-no-desc">No description yet.</span>}
                    </div>
                </div>
                {link && (
                    <div className="app-modal-footer">
                        <a
                            href={link}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="activity-preview-link"
                        >
                            View full page ↗
                        </a>
                    </div>
                )}
            </div>
        </div>
    );
}

export default ActivityPreviewModal;
```

- [ ] **Step 4: Create the stylesheet**

Create `ActivityPreviewModal.css` (relies on the global `.app-modal*` classes from `src/styles/global.css`; only adds the body-specific pieces):

```css
.activity-preview-image {
    width: 100%;
    max-height: 320px;
    object-fit: cover;
    border-radius: var(--radius);
    margin-bottom: var(--gap);
}

.activity-preview-meta {
    color: var(--text-muted);
    font-size: 0.9rem;
    margin-bottom: var(--gap);
}

.activity-preview-description {
    white-space: pre-line;
    line-height: 1.6;
    color: var(--text);
}

.activity-preview-no-desc {
    color: var(--text-muted);
    font-style: italic;
}

.activity-preview-link {
    color: var(--primary);
    text-decoration: none;
    font-weight: 600;
}

.activity-preview-link:hover {
    text-decoration: underline;
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test -- --watchAll=false ActivityPreviewModal`
Expected: PASS (all 6 tests).

- [ ] **Step 6: Commit**

```bash
git add myhive-react-app/src/components/ActivityPreviewModal.js myhive-react-app/src/components/ActivityPreviewModal.css myhive-react-app/src/components/ActivityPreviewModal.test.js
git commit -m "feat: add ActivityPreviewModal info popup component"
```

---

## Task 3: Frontend — open the popup from `SwipeCard` on name click

**Files:**
- Modify: `myhive-react-app/src/components/SwipeCard.js`
- Modify: `myhive-react-app/src/components/SwipeCard.css:106-116`
- Test: `myhive-react-app/src/components/SwipeCard.test.js` (create)

- [ ] **Step 1: Write the failing test**

Create `SwipeCard.test.js`:

```jsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SwipeCard from './SwipeCard';

const cards = [
  {
    id: 'a1',
    name: 'Snorkeling Tour',
    price: 45,
    duration: 180,
    description: 'Explore the coral reefs.',
    slug: 'snorkel',
    destinationSlug: 'bali',
    categories: ['Water'],
  },
];

const getCardLink = (a) => `/destination/${a.destinationSlug}/activity/${a.slug}`;

test('clicking the card name opens the info modal and does not trigger a swipe', async () => {
  const onSwipe = jest.fn();
  render(
    <SwipeCard cards={cards} currentIndex={0} onSwipe={onSwipe} getCardLink={getCardLink} />
  );

  await userEvent.click(screen.getByRole('button', { name: 'Snorkeling Tour' }));

  expect(screen.getByText('Explore the coral reefs.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/snorkel');
  expect(onSwipe).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false SwipeCard`
Expected: FAIL — the name is currently an `<a>` (link), so `getByRole('button', { name: 'Snorkeling Tour' })` finds nothing.

- [ ] **Step 3: Import the modal and add local state**

In `SwipeCard.js`, update the imports (line 1-2) and add the `infoCard` state at the top of the component (after line 7, `const [drag, setDrag] = useState(...)`):

```jsx
import { useCallback, useState } from 'react';
import './SwipeCard.css';
import ActivityPreviewModal from './ActivityPreviewModal';
```

```jsx
    const [drag, setDrag] = useState({ active: false, startX: 0, offsetX: 0 });
    const [copied, setCopied] = useState(false);
    const [infoCard, setInfoCard] = useState(null);
```

- [ ] **Step 4: Replace `renderName` with a button trigger**

Replace the `renderName` definition (`SwipeCard.js:64-74`) with a version that renders a button opening the modal for the active `card` (which is in scope here). The button stops pointer propagation so it never starts a swipe:

```jsx
    const renderName = (name) => (
        <button
            type="button"
            className="swipe-card-link swipe-card-name-btn"
            onPointerDown={(e) => e.stopPropagation()}
            onPointerUp={(e) => e.stopPropagation()}
            onClick={(e) => {
                e.stopPropagation();
                setInfoCard(card);
            }}
        >{name}</button>
    );
```

Then update the two call sites to drop the now-unused link argument:
- `SwipeCard.js:120` — `{renderName(card.name, cardLink)}` → `{renderName(card.name)}`
- `SwipeCard.js:129` — `{renderName(card.name, cardLink)}` → `{renderName(card.name)}`

The `cardLink` const (line 62) is still used for the modal `link` prop (next step), so keep it.

- [ ] **Step 5: Render the modal**

Add the modal just before the final closing `</div>` of the `swipe-card-page` wrapper (after the `shareUrl` block, around `SwipeCard.js:163`):

```jsx
            {shareUrl && (
                <div className="swipe-share">
                    <button className="swipe-share-btn" onClick={handleCopy}>
                        {copied ? '✓ Link Copied!' : 'Copy Invite Link'}
                    </button>
                </div>
            )}

            <ActivityPreviewModal
                activity={infoCard}
                link={infoCard && getCardLink ? getCardLink(infoCard) : null}
                onClose={() => setInfoCard(null)}
            />
        </div>
    );
```

- [ ] **Step 6: Make `.swipe-card-link` work as a button**

In `SwipeCard.css`, extend the `.swipe-card-link` rule (`SwipeCard.css:106-112`) with button-reset properties so the `<button>` looks like the old inline link:

```css
.swipe-card-link {
    color: inherit;
    text-decoration: underline;
    text-decoration-thickness: 1px;
    text-underline-offset: 3px;
    cursor: pointer;
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    text-align: left;
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `npm test -- --watchAll=false SwipeCard`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/components/SwipeCard.js myhive-react-app/src/components/SwipeCard.css myhive-react-app/src/components/SwipeCard.test.js
git commit -m "feat: open activity info popup from swipe card name"
```

---

## Task 4: Frontend — open the popup from the `CuratePage` finalize list

**Files:**
- Modify: `myhive-react-app/src/pages/vote/CuratePage.js`
- Modify: `myhive-react-app/src/pages/vote/CuratePage.css:90-99`
- Test: `myhive-react-app/src/pages/vote/CuratePage.test.js`

- [ ] **Step 1: Fix the stale assertion and write the new failing test**

In `CuratePage.test.js`, first fix the pre-existing broken assertion (the working tree currently looks for a `We Build the Stag` button that does not exist) at line 66:

```jsx
  await userEvent.click(screen.getByRole('button', { name: /Create & get link/i }));
```

Then add this new test (after the `start over resets the picked list` test):

```jsx
test('clicking an activity name on the finalize list opens the info modal', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', description: 'Drive a real tank.', duration: 120, categories: ['Extreme'] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));

  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();

  // The name is now a button that opens the info modal instead of navigating away.
  await userEvent.click(screen.getByRole('button', { name: 'Tank Driving' }));

  expect(screen.getByText('Drive a real tank.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/tank');
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false CuratePage`
Expected: the new test FAILS — the finalize name is currently an `<a>`, so `getByRole('button', { name: 'Tank Driving' })` finds nothing. (The fixed `Create & get link` test should now pass.)

- [ ] **Step 3: Import the modal and add local state**

In `CuratePage.js`, add the import (after line 5, the `SwipeCard` import) and a `selected` state (after line 19, `const [error, setError] = useState(null);`):

```jsx
import SwipeCard from '../../components/SwipeCard';
import ActivityPreviewModal from '../../components/ActivityPreviewModal';
```

```jsx
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
```

- [ ] **Step 4: Replace the finalize name link with a button trigger**

Replace the finalize card-name block (`CuratePage.js:189-202`) — the `getCardLink(a) ? <a> : a.name` ternary — with a button that opens the modal:

```jsx
                  <div className="curate-finalize-card-name">
                    <button
                      type="button"
                      className="curate-finalize-card-link"
                      onClick={() => setSelected(a)}
                    >
                      {a.name}
                    </button>
                  </div>
```

- [ ] **Step 5: Render the modal in the finalize view**

Add the modal just before the closing `</div>` of the `curate-finalize` wrapper (after the `curate-finalize-actions` block, around `CuratePage.js:233`):

```jsx
        </div>
        <ActivityPreviewModal
          activity={selected}
          link={selected ? getCardLink(selected) : null}
          onClose={() => setSelected(null)}
        />
      </div>
    );
  }
```

(The first `</div>` closes `curate-finalize-actions`; the modal sits between it and the `curate-finalize` wrapper's closing `</div>`.)

- [ ] **Step 6: Make `.curate-finalize-card-link` work as a button**

In `CuratePage.css`, extend the `.curate-finalize-card-link` rule (`CuratePage.css:90-95`) with button-reset properties:

```css
.curate-finalize-card-link {
  color: inherit;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  cursor: pointer;
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `npm test -- --watchAll=false CuratePage`
Expected: PASS (all `CuratePage` tests, including the new modal test and the fixed `Create & get link` test).

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/pages/vote/CuratePage.js myhive-react-app/src/pages/vote/CuratePage.css myhive-react-app/src/pages/vote/CuratePage.test.js
git commit -m "feat: open activity info popup from curate finalize list"
```

---

## Task 5: Full-suite verification

- [ ] **Step 1: Run the frontend suite**

Run: `npm test -- --watchAll=false`
Expected: all suites pass (no regression in `ActivityVotePage` or other consumers of `SwipeCard`).

- [ ] **Step 2: Run the backend suite**

Run (from `myhive-backend`): `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke (optional, recommended)**

With backend (`.\gradlew.bat bootRun --args="--spring.profiles.active=dev"`) and frontend (`npm start`) running: start a vote, swipe to the curate deck, click an activity name → popup opens with description and "View full page ↗"; close via ×, backdrop, and Esc; reach the finalize list and click a name → same popup. Confirm no navigation away from the flow tab.

---

## Self-Review notes (already reconciled)

- **Spec coverage:** modal component (Task 2), SwipeCard wiring incl. both flows (Task 3), finalize wiring (Task 4), backend `description`+`duration` (Task 1), all spec tests present (Tasks 1-4).
- **Pre-existing breakage:** the working-tree `CuratePage.test.js` has a stale `We Build the Stag` button name (no such button exists); Task 4 Step 1 restores it to `Create & get link`.
- **Type consistency:** the modal reads `name`, `price`, `duration`, `categories` (string array), `imageUrl`, `description` — all present on `VoteActivityResponse` and, after Task 1, on `VotePoolActivityDTO`. `CuratePage` already spreads pool items (`{...a, id: a.activityId}`), so the new fields reach the card with no extra mapping.
