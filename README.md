# Trivlu Travel

Travel booking platform — Spring Boot 4.0 backend + React 19 frontend.

## Quick Start

```bash
# Backend (port 8080)
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (port 3000) — canonical Next.js app
cd myhive-next
npm install
cp .env.local.example .env.local     # Windows: copy .env.local.example .env.local
npm run dev
```

Open http://localhost:3000 (German: `/de`). Details, env contract and gotchas:
[`myhive-next/README.md`](myhive-next/README.md#running-locally). The standalone
CRA dev server (`cd myhive-react-app && npm install && npm start`) serves the same
UI English-only and is kept as a rollback — it takes the same port 3000, so run
one or the other.

Dev uses H2 in-memory database with sample data (5 destinations, activities, bookings).

## Project Structure

```
myhive-backend/          Spring Boot 4.0, Java 25, Gradle 9.3.1
myhive-react-app/        React 19, CRA, BrowserRouter, Bootstrap 5
```

### Backend Packages

| Package       | Purpose                                                                                         |
|---------------|-------------------------------------------------------------------------------------------------|
| `controller/` | REST endpoints (Destination, Activity, Category, Package, Booking, Blog, Contact, Vote, VoteSession, UserInfo, Admin, Sitemap, Home) |
| `service/`    | Business logic + EmailService                                                                   |
| `entity/`     | JPA entities (UUID PKs; slug field on Destination, Activity, Category, BlogPost, Package): Destination, Activity, Category, Booking, BookingItem, BlogPost, Package, PackageActivity, plus vote/quiz entities (VoteSession, VoteSessionActivity, VoteActivityLike, QuizQuestion, QuizAnswer, …) |
| `dto/`        | Request/response objects                                                                        |
| `config/`     | Security (Auth0 OIDC), CORS, rate limiting, R2, email templates, async email executor (`AsyncConfig`) |
| `util/`       | SlugUtils (slug generation via Slugify + ICU4J transliteration)                                 |

### API Endpoints

**Public** (no auth):

- `GET /destinations`, `GET /destinations/{id}`, `GET /destinations/slug/{slug}`
- `GET /activities`, `GET /activities/{id}`, `GET /activities/slug/{slug}`, `GET /activities/paged` (filters:
  `?categorySlug=<slug>`, `?featured=true` — homepage grid, admin-managed via the `featured` flag)
- `GET /categories`, `GET /categories/{id}`, `GET /categories/slug/{slug}`
- `GET /packages`, `GET /packages/{id}`, `GET /packages/slug/{slug}`
- `GET /blog`, `GET /blog/{id}`, `GET /blog/slug/{slug}`
- `POST /bookings`, `POST /bookings/trip` (PATCH `/bookings/{id}/status` requires ADMIN). The public
  read endpoints (`GET /bookings/{id}`, `GET /bookings?email=`) were removed — booking detail is served
  only from the JWT-gated `/admin/bookings/**`.
- `POST /contact`
- `GET /sitemap.xml` — XML sitemap (1h cache)
- **Vote sessions** (share-token based, no auth): `POST /vote/sessions`, `GET /vote/sessions/{shareToken}`
  (+ `/activities`, `/votes`, `/votes/batch`, `/participant-count`, `/close`, `/result`, `/quiz`),
  `GET /vote/destinations/{destinationId}/quiz`, `POST /vote/pool`
  - `POST /vote/sessions/cart` — create a cart-seeded vote session (no quiz); body
    `{destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds}`.
  - `GET /vote/sessions/{shareToken}/tally?voterToken=&managerToken=` — live tally for CART sessions;
    requires having voted (`voterToken`) or the `managerToken`.
  - Sessions carry a `voteMode`: `QUIZ` (default) runs the existing quiz + score-cutoff + budget-knapsack
    flow; `CART` is an advisory swipe vote (right = like, left = skip) over the traveler's own cart, ranked
    by like count with cart-order ties (skips are recorded but never affect the ranking), with no score
    cutoff and no budget knapsack — results annotate the Trip Builder itinerary and never replace the cart.
  - A closed `QUIZ` session does the opposite: its ballot *was* the organizer's cart, so landing on
    `/destination/{slug}?tab=trip-builder&voteSession={shareToken}` **replaces** the cart with the winners
    (`SET_TRIP_ITEMS_FROM_VOTE`), dropping whatever the group voted down. Package items are kept — they were
    never on the ballot. The replacement is one-shot per browser (`myhive-vote-applied-{shareToken}`) because
    the URL param outlives that first load, and it waits for the saved cart to be restored first.
  - The Create-vote modal asks for the organizer's address on a second step and only then creates the session,
    so the invite link is never shown without one — `initiatorEmail` stays optional on the API but is present on
    every session started from the UI, and storing it stamps `email_captured_at`.
  - Organizer progress emails ride the same 5-minute tick as the expiry sweep (`VoteSessionScheduler` →
    `VoteProgressNotifier`): "N of M have voted" once half the group has voted, and "N people have not voted yet"
    (with ready-to-paste chat text) once the session is within 12 hours of its deadline. Each is claimed with a
    conditional UPDATE on `halfway_email_sent_at` / `reminder_email_sent_at`, so it sends at most once per session
    and a session closed mid-send is never resurrected. Kill switch: `VOTE_ORGANIZER_EMAILS_ENABLED`.
- **Payments** (Stripe; public but gated per-endpoint): `POST /payments/deposit-session` (vote deposit —
  requires `X-Vote-Share-Token` + `X-Manager-Token`), `POST /payments/trip-deposit-session` (Trip Builder
  deposit — Turnstile-gated via `X-Turnstile-Token`), `POST /payments/consultation-lead`,
  `POST /payments/webhook` (Stripe-signature authenticated, rate-limit-exempt with a 512 KB body cap).
  Both deposit endpoints read the `Origin` header to build same-origin Stripe return URLs (validated
  against `CORS_ALLOWED_ORIGINS`, falls back to `FRONTEND_URL`).
- `GET /auth/me` — current user info from JWT (permitAll; returns roles when a token is present)
- **Trip lead reminders**: `POST /leads` (create/dedup by normalized email), `PATCH /leads/{id}` (debounced sync
  of travelers/dates/budget/quiz answers/cart items — requires the lead's `restoreToken`), `GET /leads/restore/{token}`
  (cross-device cart restore), `POST /leads/unsubscribe` + `POST /leads/unsubscribe/one-click?token=` (RFC 8058
  one-click, headers only emitted when `API_PUBLIC_URL` is set). Reminder emails sent by `TripLeadReminderScheduler`
  on a 10-minute tick (QUIZ 1h/24h/72h, VOTE 24h/72h from last activity); stops on booking, new vote, or suppression;
  leads deleted 30 days after last touch. Kill switch: `REMINDERS_ENABLED`.

**Admin** (Auth0 JWT, ADMIN/MANAGER role; categories require ADMIN):

- `/admin/bookings/**`, `/admin/destinations/**`, `/admin/activities/**`, `/admin/categories/**`, `/admin/blog/**`,
  `/admin/upload`
- `POST /admin/bookings/{id}/payment-link` — ADMIN/MANAGER creates a Stripe Payment Link for an editable
  amount (balance/add-on) on any non-CANCELLED booking; single-use (deactivated after one completed payment)
- Paged list endpoints: `/admin/*/paged?page=0&size=10`

## Services

| Service | Provider      | Purpose                              |
|---------|---------------|--------------------------------------|
| Hosting | Render.com    | Backend + frontend static site       |
| DB      | Render        | PostgreSQL 18 (Basic-256mb); schema evolves via Hibernate `ddl-auto=update` plus Flyway versioned migrations (`src/main/resources/db/migration`, prod-only, baseline 0) for DDL Hibernate can't do, e.g. dropping NOT NULL. Requires the `spring-boot-flyway` module (Boot 4 ships Flyway auto-configuration separately) and is incompatible with `spring.jpa.defer-datasource-initialization` |
| CDN/DNS | Cloudflare    | Proxy, DDoS protection, caching, SSL |
| Email (send)    | Resend    | SMTP relay for transactional email (noreply@trivlu.com); itinerary/vote emails sent asynchronously off the request thread via a bounded pool, contact-form notification stays synchronous |
| Email (receive) | Zoho Mail | Inbound mailboxes: info@ / support@ / bookings@         |
| Images  | Cloudflare R2 | S3-compatible object storage         |
| Auth    | Auth0         | OIDC/OAuth2, roles: ADMIN, MANAGER   |
| Payments | Stripe       | Checkout Sessions (30% deposits) + Payment Links (admin balance/add-on); webhook-driven fulfilment |
| Domain  | Namecheap     | Registrar (DNS hosted on Cloudflare) |

## Environment Variables

### Backend

| Variable                 | Required    | Default                  |
|--------------------------|-------------|--------------------------|
| `SPRING_PROFILES_ACTIVE` | yes         | `dev`                    |
| `DATABASE_URL`           | prod        | H2 in dev                |
| `DB_USERNAME`            | prod        | -                        |
| `DB_PASSWORD`            | prod        | -                        |
| `PORT`                   | no          | `8080`                   |
| `RESEND_API_KEY`         | for email   | -                        |
| `EMAIL_FROM`             | for email   | `noreply@trivlu.com`     |
| `EMAIL_CONTACT_TO`       | for email   | `info@trivlu.com`        |
| `EMAIL_ENABLED`          | no          | `false` (dev) / `true` (prod) |
| `CORS_ALLOWED_ORIGINS`   | yes         | `https://trivlu.com,https://www.trivlu.com,https://*.trivlu.com,https://myhive-frontend.onrender.com,http://localhost:3000,http://127.0.0.1:3000` (also drives Stripe return-URL origin validation) |
| `TURNSTILE_SECRET_KEY`   | for contact + Trip Builder deposit | -       |
| `STRIPE_SECRET_KEY`      | for payments (blank fails fast in prod) | -  |
| `STRIPE_WEBHOOK_SECRET`  | for payments (blank fails fast in prod) | -  |
| `STRIPE_CURRENCY`        | no          | `eur`                    |
| `PAYMENT_DEPOSIT_PCT`    | no          | `30` (must be 1–99)      |
| `R2_ACCESS_KEY`          | for uploads | -                        |
| `R2_SECRET_KEY`          | for uploads | -                        |
| `R2_BUCKET_NAME`         | for uploads | -                        |
| `R2_ENDPOINT`            | for uploads | -                        |
| `R2_PUBLIC_URL`          | for uploads | -                        |
| `AUTH0_ISSUER_URI`       | yes         | -                        |
| `AUTH0_AUDIENCE`         | yes         | `https://api.trivlu.com` |
| `AUTH0_ROLES_CLAIM`      | no          | `https://trivlu.com/roles` |
| `FRONTEND_URL`           | for sitemap | `https://trivlu.com` (also the Stripe return-URL fallback when the request Origin is absent/untrusted) |
| `REMINDERS_ENABLED`      | no          | `true` (kill switch for the trip-lead reminder scheduler) |
| `VOTE_ORGANIZER_EMAILS_ENABLED` | no   | `true` (kill switch for the organizer halfway/reminder emails; independent of `REMINDERS_ENABLED`, and both no-op when `EMAIL_ENABLED` is false) |
| `API_PUBLIC_URL`         | no          | empty — set to the backend's public base URL **including the prod context path**, e.g. `https://<backend-host>/api`, to enable RFC 8058 `List-Unsubscribe`/`List-Unsubscribe-Post` headers on reminder emails. Left empty, reminder emails still send but ship **without** those one-click headers, which Gmail/Yahoo require of bulk senders. |

### Frontend (build-time `REACT_APP_*`)

| Variable                       | Required | Default                       |
|--------------------------------|----------|-------------------------------|
| `REACT_APP_API_URL`            | yes      | `http://localhost:8080`       |
| `REACT_APP_SITE_URL`           | no       | `https://trivlu.com`          |
| `REACT_APP_OIDC_AUTHORITY`     | yes      | -                             |
| `REACT_APP_OIDC_CLIENT_ID`     | yes      | -                             |
| `REACT_APP_OIDC_AUDIENCE`      | yes      | -                             |
| `REACT_APP_OIDC_REDIRECT_URI`  | no       | `<origin>/admin`              |
| `REACT_APP_OIDC_ROLES_CLAIM`   | no       | `https://trivlu.com/roles`    |
| `REACT_APP_TURNSTILE_SITE_KEY` | for contact | -                          |

Compile-time flags live in `src/services/config.js`: `DESTINATION_PICKER_ENABLED` (destination choice in the
vote flow — off while Prague is the only live destination), `DEFAULT_DESTINATION_SLUG` (`prague`), and
placeholder `WHATSAPP_URL` / `MESSENGER_URL` support links used on the homepage.

## Testing

```bash
cd myhive-backend
./gradlew test                                    # all tests
./gradlew test --tests '*ContactControllerTest'   # single class
```

Tests use JUnit 5 + Spring Boot Test + H2. Auth0 JwtDecoder is mocked via `TestSecurityConfig`.

## Docker

```bash
# Dev
cd myhive-backend
docker build --build-arg SPRING_PROFILE=dev -t myhive-backend:dev .
docker run -d -p 8080:8080 myhive-backend:dev

# Prod (used by Render.com)
docker build -t myhive-backend:prod .
docker run -d -p 8080:8080 --env-file .env myhive-backend:prod
```

Dev docker-compose available: `docker-compose -f docker-compose.dev.yml up`

## SEO

- **Slug-based URLs**: `/destination/prague`, `/destination/tenerife/activity/sunset-boat-party`,
  `/blog/top-5-group-travel-destinations-for-2026`
- **Slug generation**: Slugify library + ICU4J for full unicode transliteration (Cyrillic, CJK, accents, German ß)
- **Custom slugs**: Admins can set custom slugs via the admin panel; leave blank to auto-generate from name/title
- **Meta tags**: react-helmet-async for per-page `<title>`, `<meta description>`, `<link canonical>`
- **Sitemap**: `GET /sitemap.xml` — auto-generated, 1h HTTP cache, Cloudflare CDN cache
- **Open Graph**: Default OG tags in index.html
- **robots.txt**: Disallow `/admin/`, Sitemap directive

## Analytics

- **Google Tag Manager**: container `GTM-KB7BJLDS`, both snippets hardcoded in `myhive-react-app/public/index.html`
  (loader as the first element in `<head>`, noscript iframe right after `<body>`). Because the frontend is a single
  CRA SPA, this one install covers every client-side route — marketing site, Trip Builder, voting (`/vote/*`) and
  in-app booking/payment screens. The build copies `index.html` → `404.html`, so the SPA-fallback page is tagged too.
- The backend CSP (`SecurityHeadersFilter`) rides only on `/api` JSON responses, not the separately-served frontend,
  so it does not block GTM.

### Event layer & attribution (dataLayer → GTM)

The SPA emits a consent-gated `dataLayer` event layer that GTM routes to GA4 / Meta Pixel / Microsoft Clarity
(tags configured in GTM — no vendor snippet in the repo):

- `src/utils/analytics.js` — stateless `pushEvent(event, params)`; the only code that touches `window.dataLayer`.
  Attaches a uuid `event_id` to every event (server-dedup/CAPI seed) and drops empty params (keeps `false`/`0`).
- 22 funnel events (homepage CTAs, Trip Builder, quiz, vote flow, organizer email screen, payment, booking) — e.g.
  `cta_click`, `tb_group_submitted` (email passed for Meta Advanced Matching only with `ad_storage` consent),
  `vote_launched`, `checkout_viewed`, and `booking_submitted` (→ Meta `Lead` / GA4 `generate_lead`, the
  campaign-optimisation conversion).
- The Create-vote email screen emits `organizer_voted` → `email_screen_view` → `contact_captured` → `link_revealed`
  (plus `email_invalid_attempt` with `reason: empty|format`). `link_revealed / email_screen_view` is the drop-off
  ratio the screen is judged on: below 0.69 after a month, the design doc calls for adding a skip option.
- A `trip_id` threads the whole funnel: the vote `shareToken` for the vote flow, a client-minted UUID for the
  direct-book flow (`TripContext` + `localStorage['myhive-trip-id']`, survives cancel). `user_role` (`src/utils/userRole.js`)
  distinguishes organizer vs participant.
- `src/utils/attribution.js` + `components/AttributionCapture.js` — captures `utm_*`/`gclid`/`fbclid`/referrer
  (90-day, last-non-direct-click) and the viral-loop `ref=invite`, on first visit and on SPA navigation.
- Privacy: PII (email) reaches the dataLayer only with `ad_storage` consent (`src/utils/consent.js`, deny-by-default);
  per-vendor consent gating is done by the GTM tags (CookieYes Consent Mode v2).
- **Backend**: `POST /bookings/trip` accepts and persists `tripId` + the attribution fields on `Booking`
  (`utm_*`, `ref`, `gclid`, `fbclid`, `referrer`), so campaigns tie to bookings (`utm → trip_id → money`); the
  confirmation email shows `tripId` as a booking reference, and participant invite links carry `?ref=invite`.
- Pending (out of repo): GA4/Meta/Clarity tag creation in GTM, and the payment-funnel events (Phase 2 — no payment
  system exists yet). Full design: `docs/superpowers/plans/2026-06-17-analytics-tracking-integration.md`.

## Cookie consent (CookieYes + Consent Mode v2)

- Consent is handled by the **CookieYes CMP**, loaded **through GTM** via the official CookieYes CMP Community
  Template (no CookieYes banner snippet in the repo). The tag fires on **Consent Initialization – All Pages** and
  sets Google **Consent Mode v2** defaults to **denied** for `analytics_storage`, `ad_storage`, `ad_user_data`,
  `ad_personalization` (Necessary/Functional granted). The CookieYes dashboard has Consent Mode, cookieless pings,
  Microsoft UET and Clarity Consent API enabled; banner is English with equal Accept/Reject buttons.
- **Policy pages** are static React routes — `/cookie-policy` holds the generated Cookie Policy text inline
  (`CookiePolicyPage.js`); `/privacy-policy` is a placeholder until generated. The CookieYes policy *embed* is not
  used: it renders on the `window` `load` event, which never fires on an SPA route entered client-side, so dynamic
  injection leaves it blank.
- The footer "Legal" group links both policies and exposes a `.cky-banner-element` button ("Cookie settings") that
  CookieYes auto-binds to reopen the preference center. The previous homegrown cookie banner was removed.
- Not yet wired: GA4 / Meta Pixel / Microsoft Clarity tags (to be created in GTM and gated via tag Consent Settings).

## Security

- Auth0 OIDC (OAuth2 Resource Server), stateless sessions
- Rate limiting: 100 req/min per IP
- Security headers: CSP, X-Frame-Options, X-Content-Type-Options
- CORS restricted to configured origins
- `/api` context path in prod
