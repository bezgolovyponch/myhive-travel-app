# myhive-next

Ф0 of the strangler migration (see `docs/superpowers/plans/2026-07-21-nextjs-migration-plan-1-foundation.md`):
a Next.js App Router shell that serves the legacy CRA SPA on every route, on the
current URLs. Later phases add SSR pages for public URLs (the reason this
migration exists — SEO план v3: crawlers currently get an empty JS shell).

## Running locally

Node 20.20 (`.node-version`). The app needs the backend running: every `/api/*`
call and every server-side read in `lib/api.ts` goes to `BACKEND_URL`.

```bash
# 1. Backend on :8080 — H2 in memory, sample data, email disabled
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# 2. Frontend on :3000 — in a second terminal
cd myhive-next
npm install
cp .env.local.example .env.local     # Windows: copy .env.local.example .env.local
npm run dev
```

Open http://localhost:3000 (German: http://localhost:3000/de). `npm run dev`
syncs `legacy-src/` first and then keeps watching `myhive-react-app/src`, so
edits to the CRA sources hot-reload — see the sync model below.

Notes and gotchas:

- **Port 3000 is shared with the standalone CRA dev server** (`myhive-react-app
  && npm start`). Run one or the other, never both.
- **No `.env.local` still boots**: `BACKEND_URL` falls back to
  `http://localhost:8080`. What breaks without it is the contact form (no
  Turnstile sitekey) and the `/admin` login (no Auth0 values).
- **`BACKEND_URL` is read at build time**, so `next dev` picks up a change only
  after restarting the dev server, and `next build` only after a rebuild.
- **API calls 404 or hang?** Check who actually owns port 8080 before blaming
  the app (`netstat -ano | findstr :8080`) — other desktop software squats it.
- **Analytics stays silent on localhost** by design (GTM only initialises on
  `*.trivlu.com`); see [Analytics](#analytics) to force it on.

Production-like check:

```bash
npm run build && npm start          # needs BACKEND_URL set — the build throws without it
node scripts/smoke.mjs http://localhost:3000   # titles, H1s, canonicals, 404s, sitemap, robots
```

## Sync model — don't edit `legacy-src/` or `public/`

Both directories are **generated** (gitignored) by `scripts/sync-legacy.mjs`,
which runs automatically before `dev` and `build`:

- `legacy-src/` ← `../myhive-react-app/src` (the single source of truth)
- `public/` ← `../myhive-react-app/public`, minus `index.html`
  (document shell is `app/layout.tsx`) and `robots.txt` (owned by `app/robots.ts`)

`npm run dev` also watches the CRA sources and re-syncs on change; edits made
while a plain `next dev` is running are invisible until re-synced.

## i18n

English lives on the bare URLs it always had; other locales are path-prefixed
(`/de/...`). Routing is next-intl (`i18n/routing.ts` + `middleware.ts`); the
locale list itself lives in `myhive-react-app/src/i18n/routes.js` so the legacy
link helpers and Next routing share one source.

- **UI strings** live in `myhive-react-app/src/i18n/messages/{en,de}.json`
  (synced into `legacy-src/` like all CRA sources). Legacy components read them
  through a tiny `useT` hook (`src/i18n/index.js`) whose context defaults to
  English — the standalone CRA build and its tests need no provider. The
  `[locale]` layout mounts `LegacyLocaleProvider` to switch languages; missing
  German keys fall back to English on both server and client.
- **Metadata** (titles/descriptions) come from the same files via next-intl's
  `getTranslations` in each page's `generateMetadata`; `lib/seo.ts` emits
  locale-aware canonicals and the hreflang set.
- **Links**: legacy components keep locale-free URLs; `LegacyRouter` strips the
  prefix from the location it feeds react-router and re-adds it on every
  href/navigation. The SPA-owned flows (/vote, /payment, /unsubscribe, ?tab=
  state) run under the prefix too: the CRA `BrowserRouter` takes it as its
  `basename` (see `myhive-react-app/src/App.js`), so `/de/vote/...` mounts the
  SPA in German. /admin stays English (no translations, works under both).
- **Emails**: the SPA sends the page locale when it creates a vote session,
  a trip lead or a booking; the backend stores it and renders the customer
  emails (and the links inside them) in that language.
- **Legal pages** are not translated (`translated: false` in `pageMetadata`):
  `/de/terms` serves the English document with a canonical pointing at
  `/terms` and no hreflang set.
- **Backend content** (destinations, activities, packages, categories, blog)
  is localized by the backend: every read in `lib/api.ts` carries
  `?locale=`, and the response comes back with the translatable fields
  resolved in place (English fallback per field), same shape as before. The
  legacy client fetches (`services/api.js`) do the same, reading the locale
  from the URL prefix. Translations live in a `translations` JSON column per
  content table (`myhive-backend`, see `util/Translations`); the prod fill is
  `myhive-backend/prod-migration-translations-de.sql`.

## Environment

Copy `.env.local.example` → `.env.local`. Notable contracts:

- `BACKEND_URL` (server-only, **build-time**): backend base including its path
  prefix. Production builds fail fast if unset — the localhost fallback is for
  dev only. Changing it requires a rebuild, not a restart.
- Browser-visible values are `NEXT_PUBLIC_*` and bridged to the legacy
  `REACT_APP_*` names in `next.config.ts`. `REACT_APP_API_URL` is pinned to
  same-origin `/api`, served by the rewrite to `BACKEND_URL`.
- `NEXT_PUBLIC_TURNSTILE_SITE_KEY` is required (legacy ContactPage has no
  fallback). `ALLOW_INDEXING=true` (build-time) switches robots.txt from
  Disallow-all to the production rules — only the canonical-domain service
  sets it.

The backend's `CORS_ALLOWED_ORIGINS` must include this app's origin: the
browser sends its own Origin header even through the same-origin `/api`
rewrite, and Spring 403s every write from unknown origins.

## Analytics

GTM (+ CookieYes consent, loaded through the container) runs only on
`*.trivlu.com` hostnames — previews and localhost stay silent by design.
