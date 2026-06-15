# Trivlu Travel

Travel booking platform — Spring Boot 4.0 backend + React 19 frontend.

## Quick Start

```bash
# Backend (port 8080)
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (port 3000)
cd myhive-react-app
npm install && npm start
```

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
- `POST /bookings`, `POST /bookings/trip`, `GET /bookings/{id}`, `GET /bookings?email=` (PATCH `/bookings/{id}/status` requires ADMIN)
- `POST /contact`
- `GET /sitemap.xml` — XML sitemap (1h cache)
- **Vote sessions** (share-token based, no auth): `POST /vote/sessions`, `GET /vote/sessions/{shareToken}`
  (+ `/activities`, `/votes`, `/votes/batch`, `/participant-count`, `/close`, `/result`, `/quiz`),
  `GET /vote/destinations/{destinationId}/quiz`, `POST /vote/pool`
- `GET /auth/me` — current user info from JWT (permitAll; returns roles when a token is present)

**Admin** (Auth0 JWT, ADMIN/MANAGER role; categories require ADMIN):

- `/admin/bookings/**`, `/admin/destinations/**`, `/admin/activities/**`, `/admin/categories/**`, `/admin/blog/**`,
  `/admin/upload`
- Paged list endpoints: `/admin/*/paged?page=0&size=10`

## Services

| Service | Provider      | Purpose                              |
|---------|---------------|--------------------------------------|
| Hosting | Render.com    | Backend + frontend static site       |
| DB      | Render        | PostgreSQL 18 (Basic-256mb)          |
| CDN/DNS | Cloudflare    | Proxy, DDoS protection, caching, SSL |
| Email (send)    | Resend    | SMTP relay for transactional email (noreply@trivlu.com); itinerary/vote emails sent asynchronously off the request thread via a bounded pool, contact-form notification stays synchronous |
| Email (receive) | Zoho Mail | Inbound mailboxes: info@ / support@ / bookings@         |
| Images  | Cloudflare R2 | S3-compatible object storage         |
| Auth    | Auth0         | OIDC/OAuth2, roles: ADMIN, MANAGER   |
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
| `CORS_ALLOWED_ORIGINS`   | yes         | `https://trivlu.com,https://www.trivlu.com,https://myhive-frontend.onrender.com,http://localhost:3000,http://127.0.0.1:3000` |
| `TURNSTILE_SECRET_KEY`   | for contact | -                        |
| `R2_ACCESS_KEY`          | for uploads | -                        |
| `R2_SECRET_KEY`          | for uploads | -                        |
| `R2_BUCKET_NAME`         | for uploads | -                        |
| `R2_ENDPOINT`            | for uploads | -                        |
| `R2_PUBLIC_URL`          | for uploads | -                        |
| `AUTH0_ISSUER_URI`       | yes         | -                        |
| `AUTH0_AUDIENCE`         | yes         | `https://api.trivlu.com` |
| `AUTH0_ROLES_CLAIM`      | no          | `https://trivlu.com/roles` |
| `FRONTEND_URL`           | for sitemap | `https://trivlu.com`     |

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
