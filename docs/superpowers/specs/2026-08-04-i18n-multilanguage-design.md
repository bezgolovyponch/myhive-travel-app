# Multi-Language (i18n) Support — Design

**Date:** 2026-08-04
**Status:** Approved (design), pending implementation plan

## Goal

Serve the public site in 5 languages — English (base) plus German (de), Spanish (es), French (fr), Czech (cs) — covering both UI strings and catalog content stored in the DB, with language-prefixed URLs for SEO. Fast to ship, reliable, and built to survive the ongoing CRA → Next.js migration (PR #5).

## Scope

**Translated:**
- All user-visible UI strings on public pages/components (header, footer, home, destination, activity, package, blog, contact, trip builder, vote flows, checkout, quiz, legal-page chrome).
- DB content fields:
  - `Activity`: name, description, includes, duration
  - `Destination`: name, description
  - `Category`: name
  - `Package`: name, description, includes
  - `BlogPost`: title, excerpt, content
  - `QuizQuestion`: prompt; `QuizAnswer`: label
- Customer-facing emails (itinerary confirmation, payment received, vote created, vote result, trip reminder, consultation lead ack).
- Sitemap with hreflang alternates.

**Not translated (out of scope):**
- Admin UI (internal tool, stays English).
- Internal notification emails to info@ (contact form, booking notification).
- Slugs — one language-neutral slug per entity, shared across locales (`/de/destination/prague-stag-do`).
- No browser-language auto-redirect and no language-suggestion banner — language changes only via the header switcher or an explicit URL.
- No RTL support, no currency conversion (EUR everywhere), no `BookingItem` snapshot changes.

## Frontend (CRA, React 19)

- **Library:** `react-i18next` + `i18next`. Dictionaries at `src/locales/{en,de,es,fr,cs}/translation.json`. `en` is the fallback locale. Chosen because it also works in Next.js, so dictionaries and `t()` calls survive the migration.
- **Routing:** optional locale prefix segment for `de|es|fr|cs`; English keeps today's unprefixed URLs (zero churn to existing indexed URLs). All locale logic lives in a single wrapper component (route wrapper + `LocaleContext`): it validates the prefix, initializes i18next, sets `<html lang>`, and provides a `localizedPath(path)` helper used by all internal links. During the Next.js migration only this wrapper is replaced by Next.js routing.
- **Language switcher** in the header (and footer): rewrites the current path to the target locale prefix, persists the choice in `localStorage['myhive-locale']`. The stored value is only used to pre-select the switcher and keep client-side navigation in the chosen locale — never to redirect a landing URL.
- **Data fetching:** `api.js` catalog calls append `?lang=<locale>` for non-English locales. Components keep reading the same DTO fields — translations are resolved server-side.
- **Formatting:** dates and numbers via `Intl.*` with the active locale; `formatAmount` gains a locale argument (EUR stays the only currency).

## Backend (Spring Boot)

### Storage: one generic translation table

`content_translations`:

| column | type | notes |
|---|---|---|
| id | UUID PK | |
| entity_type | varchar | enum: ACTIVITY, DESTINATION, CATEGORY, PACKAGE, BLOG_POST, QUIZ_QUESTION, QUIZ_ANSWER |
| entity_id | UUID | no FK (polymorphic); cleaned up in service layer on entity delete |
| locale | varchar(5) | de, es, fr, cs |
| field | varchar(40) | e.g. name, description, includes |
| value | text | |
| source | varchar(10) | MACHINE or MANUAL |
| updated_at | timestamp | |

Unique index on `(entity_type, entity_id, locale, field)`. Shipped as a Flyway versioned migration (prod); dev/test H2 keeps getting schema from Hibernate as today.

### Resolution

- `ContentTranslationService` with `resolveAll(entityType, ids, locale)` — one query per list endpoint (no N+1), returning `Map<entityId, Map<field, value>>`.
- Public read endpoints (`/activities`, `/destinations`, `/categories`, `/packages`, `/blog`, quiz endpoints, slug endpoints) accept an optional `lang` query param. Validated against the supported set; anything else → `en`. For `en` no lookup runs at all — existing behavior is untouched. Missing translations fall back to the English field value per-field.
- DTO shape does not change: localized values are written into the existing DTO fields during mapping.

### Locale capture

New nullable `locale` column (default `en`) on `Booking`, `TripLead`, `VoteSession`, populated from an optional `locale` field in the create-request DTOs (validated against the supported set, invalid/absent → `en`). Used for email rendering.

## Machine translation pipeline

- **Provider:** DeepL REST API (`DEEPL_API_KEY` env var). DE/ES/FR/CS are DeepL's strongest languages; free tier (500k chars/month) covers the catalog comfortably.
- **Kill switch:** `app.i18n.machine-translation-enabled` (env `MACHINE_TRANSLATION_ENABLED`), same pattern as payments/reminders. Disabled → saves succeed, no translations are produced.
- **Trigger:** on admin create/update of a translatable entity, if any English source field changed, an async task (small dedicated bounded executor, mirroring the email pool) translates the changed fields into the 4 locales and upserts rows with `source=MACHINE`. Rows with `source=MANUAL` are never overwritten by the pipeline.
- **Admin editing:** a "Translations" tab in the existing admin edit forms — per-locale inputs for each translatable field; saving an edit stores `source=MANUAL`. A per-locale "Re-translate" action deletes the manual rows for that locale and re-runs machine translation.
- **Backfill:** one-shot idempotent `POST /admin/i18n/backfill` (ADMIN role) that machine-translates every existing entity, skipping fields that already have a row.
- **Failure handling:** DeepL errors are logged and the task retries on the next entity save or backfill run; the entity save itself never fails because of translation (fire-and-forget, like async email).

## Emails

- Strings in the customer-facing Thymeleaf templates move to `messages_{locale}.properties` bundles (Spring `MessageSource`); templates use `#{key}` lookups. One template per email type, five bundles.
- Rendering locale comes from the stored `locale` on Booking/TripLead/VoteSession; missing → `en`.
- Activity/package names inside emails are resolved through `ContentTranslationService` at send time, falling back to the stored snapshot (`BookingItem.activityName` etc.).
- Internal notifications (contact form, booking notification to info@) stay English.

## SEO

- `SitemapController` emits every public URL once per locale (`/de/...` etc.) with `xhtml:link rel="alternate" hreflang` entries for all 5 locales plus `x-default` → English. Sitemap is server-rendered, so hreflang works without SSR.
- Client sets `<html lang>` and `<link rel="alternate" hreflang>` / canonical (with locale prefix) per page, consistent with the sitemap.
- Localized page titles/meta descriptions come from the i18next dictionaries (UI pages) and translated content fields (catalog pages).

## Testing

- **Backend:** unit tests for `ContentTranslationService` (resolve, per-field fallback, batch), controller tests for the `lang` param (valid, invalid, absent), `MachineTranslationService` with mocked HTTP (success, DeepL error, kill switch off, MANUAL protection), email locale selection and fallback, sitemap hreflang output.
- **Frontend:** tests for the locale route wrapper (prefix parsing, invalid prefix → 404/EN), language switcher path rewriting, i18next fallback, `localizedPath` helper.

## Rollout (each step backward-compatible)

1. Backend ships with optional `lang` param + schema migration — old frontend keeps working unchanged.
2. Set `DEEPL_API_KEY` + `MACHINE_TRANSLATION_ENABLED=true` on Render; run `POST /admin/i18n/backfill`; spot-check translations in the admin Translations tab.
3. Frontend ships with locale routing, switcher, and dictionaries.
4. Google picks up the updated sitemap with hreflang alternates.

## Next.js migration coordination

The translation table, `lang` API param, and locale dictionaries are framework-agnostic and carry over unchanged. Only the locale route wrapper is CRA-specific and gets replaced by Next.js locale routing. Add a note to the migration plan (PR #5) so the colleague accounts for the locale prefix in the v3 URL scheme.
