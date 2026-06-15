# Frontend Phase 3 (Quality Batch) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out the phase-3 quality backlog from the 2026-06-11 frontend review: shared modal/error/clipboard infrastructure, formatter consolidation, delete-flow dedup, remaining lint warnings, and auth/API-layer test coverage.

**Architecture:** Three new shared units — `AppModal` (owns `useModalA11y` + dialog scaffold for all five overlays), `services/httpError.js` (one `parseApiError` used by both api.js and adminApi.js), `utils/clipboard.js` (safe `copyToClipboard`). `useAdminCrud` gains a `mapDeleteError` option so AdminActivities' hand-rolled delete copy dies.

**Out of scope (phase 4):** CRA→Vite migration (build + test-runner + env-var migration; standalone effort). AppContext split. Admin inline-validation UX redesign. VotePageShell (5-line wrappers; extraction saves nothing). PackageCard keyboard access (already a `<Link>` — verified).

**Conventions:** no wildcard imports; braces always; commit per task; `npm test -- --watchAll=false` from `myhive-react-app/`.

---

### Task 1: Shared `parseApiError` (`services/httpError.js`)

`api.js:parseError` is a near-verbatim copy of the non-401 branch of `adminApi.js:handleError`. One copy.

- [ ] Create `src/services/httpError.js`:

```js
// One error shape for both API layers: prefer the backend's message, always
// attach status and parsed body.
export async function parseApiError(response, fallbackMessage) {
    let body = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        try {
            body = await response.json();
        } catch (e) {
            body = null; // non-JSON error page — keep the fallback message
        }
    }
    const err = new Error((body && body.message) || fallbackMessage);
    err.status = response.status;
    err.body = body;
    return err;
}
```

- [ ] Write `src/services/httpError.test.js`: JSON body message wins; non-JSON keeps fallback; status/body attached. Use `new Response(...)` or a stub `{status, headers: {get}, json}`.
- [ ] `api.js`: delete local `parseError`, `import {parseApiError} from './httpError';`, rename call sites.
- [ ] `adminApi.js` `handleError`: keep the 401/403 short-circuit, replace the rest of the body with `throw await parseApiError(response, fallbackMessage);`.
- [ ] Full suite + commit.

### Task 2: Shared `AppModal` component

Five hand-rolled copies of `div.app-modal[role=dialog] > div.app-modal-content[ref] > header(h2 + ×)`.

- [ ] Create `src/components/AppModal.js`:

```jsx
import {useId} from 'react';
import {useModalA11y} from '../hooks/useModalA11y';

// Shared dialog scaffold: focus trap/Escape (useModalA11y), aria wiring and
// the standard header with a labelled close button.
function AppModal({isOpen, onClose, title, children, footer, contentClassName = '', overlayClassName = '', closeOnBackdrop = false}) {
    const titleId = useId();
    const contentRef = useModalA11y(isOpen, onClose);
    if (!isOpen) {
        return null;
    }
    return (
        <div
            className={`app-modal ${overlayClassName}`}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            onClick={closeOnBackdrop ? onClose : undefined}
        >
            <div
                className={`app-modal-content ${contentClassName}`}
                ref={contentRef}
                onClick={closeOnBackdrop ? (e) => e.stopPropagation() : undefined}
            >
                <div className="app-modal-header">
                    <h2 id={titleId}>{title}</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>
                <div className="app-modal-body">{children}</div>
                {footer && <div className="app-modal-footer">{footer}</div>}
            </div>
        </div>
    );
}

export default AppModal;
```

- [ ] Adopt in `ActivityPreviewModal` (title=`activity.name`, `overlayClassName="activity-preview-modal"`, `closeOnBackdrop`, footer = link block; drop its direct `useModalA11y` use) — its test must stay green.
- [ ] Adopt in `TripSetupModal` (footer = Cancel + submit button with `form={formId}`), `ContactForm` (guarded onClose, `contentClassName="contact-form-modal"`), `SuccessModal`, `Layout` Coming Soon modal.
- [ ] Full suite + commit.

### Task 3: `utils/clipboard.js` + fix SwipeCard's copy feedback

- [ ] Create util:

```js
// Safe clipboard write: resolves false instead of throwing when the API is
// unavailable (insecure context) or the write is rejected.
export async function copyToClipboard(text) {
    if (!navigator.clipboard) {
        return false;
    }
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch (e) {
        return false;
    }
}
```

- [ ] Test: resolves true on success; false when clipboard undefined; false on rejection.
- [ ] `SwipeCard.handleCopy` (line 12-16 — shows "Copied!" even on failure) and `VoteWaitingPage.handleCopy` both switch to `copyToClipboard(...).then(ok => { if (ok) {...} })`.
- [ ] Full suite + commit.

### Task 4: Consolidate price formatters

`formatPrice` renders `€12.5`, `formatAmount` renders `€12.50` — same price differs across views.

- [ ] `utils/format.js`: `formatPrice` delegates for numbers:

```js
export function formatPrice(price) {
    if (typeof price === 'number') return formatAmount(price);
    return price;
}
```

- [ ] Run suite; update any test assertion that pinned the old `€12.5` shape.
- [ ] Commit. (Raw `€{...}` interpolations in TripBuilder line items stay — phase 4.)

### Task 5: `mapDeleteError` option on `useAdminCrud`; kill AdminActivities' delete copy

- [ ] Hook: accept `mapDeleteError` option; in `handleDelete`'s catch (after `handleAuthError`): `setError(mapDeleteError ? mapDeleteError(err) : (err.message || 'Failed to delete.'));`
- [ ] Hook test: rejected delete with `mapDeleteError` produces the mapped message.
- [ ] `AdminActivities`: delete `customHandleDelete`, pass `mapDeleteError` (409 + `body.packageNames` → "Cannot delete: used in packages: ..."), `DeleteConfirmModal onConfirm={handleDelete}`, restore `handleDelete` in the destructuring, drop now-unused `setSaving`/`useAuthErrorHandler` import.
- [ ] Full suite + commit. (AdminCategories' usage-preview flow is genuinely different — stays.)

### Task 6: ContactForm — native validation via form attribute; regex escapes

- [ ] `const formId = useId();` on the form; footer Submit becomes `type="submit" form={formId}` (no onClick) so native `min/max` on travelers runs; Cancel keeps `disabled={isSubmitting}`.
- [ ] Fix `/^\+?[\d\s\-\(\)]+$/` → `/^\+?[\d\s\-()]+$/` (no-useless-escape).
- [ ] Full suite + commit.

### Task 7: Remaining lint warnings

- [ ] `useAdminCrud.js`: add `pageSize` to `fetchData` deps (a stable number — no behavior change).
- [ ] `CuratePage.js`: `const responses = useMemo(() => location.state?.responses ?? [], [location.state]);`
- [ ] `ActivityVotePage.js`: add `navigate` to the effect deps (stable in react-router 7 while the path doesn't change).
- [ ] `npm run build` — zero eslint warnings expected now. Commit.

### Task 8: Auth/API test coverage

- [ ] `src/hooks/useAuthErrorHandler.test.js`: mock `useAuth` → logout called for status 401 and 403, returns true; not called for 500/undefined, returns false.
- [ ] `src/services/adminApi.test.js`: `createAdminApi` with stubbed `global.fetch` — 401 rejects with `err.status === 401`; 409 with JSON body rejects with backend message + `err.body`; success path resolves JSON and sends `Authorization: Bearer <token>`.
- [ ] Full suite + commit.

## Final verification
- [x] Full suite green (120 tests); `npm run build` clean (no warnings).
- [x] Multi-angle code review of the branch diff (3 independent angles: correctness, React/a11y, test quality); findings fixed — ContactForm Cancel now respects the submit guard, plus added coverage for SwipeCard copy feedback, ContactForm footer-submit + phone regex, AppModal closeOnBackdrop, and formatPricePerPerson passthrough.

## Phase 4 (next)
CRA→Vite migration (envPrefix to keep REACT_APP_ vars, Vitest with jest-compatible globals or codemod, Render build output check); AppContext split; admin inline validation; raw `€{...}` interpolations in TripBuilder.
