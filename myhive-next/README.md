# myhive-next

Ф0 of the strangler migration (see `docs/superpowers/plans/2026-07-21-nextjs-migration-plan-1-foundation.md`):
a Next.js App Router shell that serves the legacy CRA SPA on every route, on the
current URLs. Later phases add SSR pages for public URLs (the reason this
migration exists — SEO план v3: crawlers currently get an empty JS shell).

## Sync model — don't edit `legacy-src/` or `public/`

Both directories are **generated** (gitignored) by `scripts/sync-legacy.mjs`,
which runs automatically before `dev` and `build`:

- `legacy-src/` ← `../myhive-react-app/src` (the single source of truth)
- `public/` ← `../myhive-react-app/public`, minus `index.html`
  (document shell is `app/layout.tsx`) and `robots.txt` (owned by `app/robots.ts`)

`npm run dev` also watches the CRA sources and re-syncs on change; edits made
while a plain `next dev` is running are invisible until re-synced.

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
