# Next.js Migration — Plan 1: Foundation (Ф0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up `myhive-next/` (Next.js App Router) on a Render preview URL where the entire legacy CRA SPA runs unchanged inside Next via a client-only wrapper — admin, vote (incl. `location.state` flow), payment and public pages all work; env contract and `/api` rewrite in place. The production domain stays on CRA.

**Architecture:** New Next.js 15 App Router project in `myhive-next/` beside `myhive-react-app/`. The legacy CRA source is **copied at build time** (`scripts/sync-legacy.mjs`, gitignored destination) into `myhive-next/legacy-src/` and mounted through a `"use client"` shim + `next/dynamic({ssr: false})` under a root optional catch-all route, so every URL serves the legacy SPA client-side. Browser API calls go same-origin to `/api/*` and are rewritten server-side to the Spring backend. Ф1 (plan 2) will replace public routes with real Server Components and narrow the catch-all to admin/vote/payment.

**Tech Stack:** Next.js 15.x (App Router), TypeScript (new code only), React 19.2.x (same major as legacy), Render Node web service, existing Spring backend (unchanged).

**Spec:** `docs/superpowers/specs/2026-07-20-nextjs-migration-design.md` (rev 2.1). Scope note: the spec's Ф0 bullet "layout/header/footer" is delivered here as the root HTML shell (metadata, fonts, GTM/consent, favicons); the visual Header/Footer port to Next components happens in plan 2 together with the public pages that need them — in Ф0 every page is the legacy SPA, which renders its own Header/Footer.

## Global Constraints

- **Branch:** execute on a new branch `feat/nextjs-foundation` off `main` (use superpowers:using-git-worktrees at execution time).
- **Do not modify `myhive-react-app/` in any way** — CRA must remain deployable for rollback. Legacy bug fixes go to `myhive-react-app/src` (outside this plan), never to `myhive-next/legacy-src/`.
- **`myhive-next/legacy-src/` is generated and gitignored.** Never edit or commit it.
- **Hybrid TypeScript:** all new files in `myhive-next/` are TS/TSX with `strict: true`; legacy stays JS (`allowJs: true`, `checkJs: false`). No "100% typed" goal.
- **Versions:** Next `^15.5.0` (the committed `package-lock.json` is the pin — the spec's `ssr:false`-in-client-component reasoning is Next 15 semantics); Node pinned by `.node-version` = `20.20.0`, `engines.node >= 20.9.0`.
- **Env contract (spec §8):** server-only `BACKEND_URL` = backend base **including its path prefix** (local Spring serves at root: `http://localhost:8080`; prod container strips `/api`: `https://myhive-backend.onrender.com/api`). Browser code calls same-origin `/api/*` (rewrite). `NEXT_PUBLIC_*` only for: `SITE_URL`, `OIDC_AUTHORITY`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI`, `OIDC_AUDIENCE`, `OIDC_ROLES_CLAIM`, `TURNSTILE_SITE_KEY`. Legacy `REACT_APP_*` names are bridged in `next.config.ts` `env:` — legacy code is not renamed.
- **Preview must not be indexed:** static `public/robots.txt` with `Disallow: /` until Ф2 cutover replaces it with `app/robots.ts`.
- **Commits:** conventional style with scope, e.g. `feat(next): …`, matching repo history.
- **Secrets:** copy real values from `myhive-react-app/.env` and the Render dashboard; never commit `.env.local`.

---

### Task 1: Scaffold the Next.js skeleton

**Files:**
- Create: `myhive-next/package.json`
- Create: `myhive-next/tsconfig.json`
- Create: `myhive-next/next.config.ts`
- Create: `myhive-next/.gitignore`
- Create: `myhive-next/.node-version`
- Create: `myhive-next/app/layout.tsx`
- Create: `myhive-next/app/[[...slug]]/page.tsx`

**Interfaces:**
- Produces: a runnable `next dev` app where **every** URL (`/`, `/destination/prague`, `/admin`) renders `app/[[...slug]]/page.tsx`. Task 3 replaces this page's body with `<LegacyAppShim />`. Task 4 extends `next.config.ts`.

- [ ] **Step 1: Create `myhive-next/package.json`**

```json
{
  "name": "myhive-next",
  "version": "0.1.0",
  "private": true,
  "engines": {
    "node": ">=20.9.0"
  },
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start"
  },
  "dependencies": {
    "next": "^15.5.0",
    "react": "^19.2.3",
    "react-dom": "^19.2.3"
  },
  "devDependencies": {
    "@types/node": "^20",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "typescript": "^5.7.0"
  }
}
```

- [ ] **Step 2: Create `myhive-next/tsconfig.json`**

`legacy-src` is excluded so the TS server doesn't type-scan generated JS; the bundler still compiles whatever the import graph reaches.

```json
{
  "compilerOptions": {
    "target": "ES2017",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "checkJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": { "@/*": ["./*"] }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules", "legacy-src"]
}
```

- [ ] **Step 3: Create `myhive-next/next.config.ts`** (minimal; Tasks 3–4 extend it)

```ts
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {};

export default nextConfig;
```

- [ ] **Step 4: Create `myhive-next/.gitignore`**

```
node_modules/
.next/
out/
legacy-src/
.env*.local
*.tsbuildinfo
```

- [ ] **Step 5: Create `myhive-next/.node-version`**

```
20.20.0
```

- [ ] **Step 6: Create `myhive-next/app/layout.tsx`** (basic shell; Task 5 brings full head parity)

```tsx
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Trivlu — Group Travel Made Easy',
  description:
    'Turn group travel chaos into epic adventures with zero stress. Trivlu is the first AI trip maker for multi-traveler experiences.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
```

- [ ] **Step 7: Create `myhive-next/app/[[...slug]]/page.tsx`**

```tsx
// Optional catch-all: in Ф0 the legacy SPA owns every route and does its own
// client-side routing. Ф1 adds real Server Component pages for public URLs,
// which take precedence over this catch-all, and narrows it to (legacy) subtrees.
export default function CatchAllPage() {
  return <div>myhive-next skeleton — legacy app mounts here in Task 3</div>;
}
```

- [ ] **Step 8: Install and run**

Run: `cd myhive-next && npm install`
Expected: `package-lock.json` created; `npm ls next react` shows next 15.x and react 19.2.x with no version conflicts.

Run: `npm run dev` (leave running), then in another shell:
`curl -s http://localhost:3000/ | grep -c "legacy app mounts here"` → `1`
`curl -s http://localhost:3000/destination/prague | grep -c "legacy app mounts here"` → `1`
`curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/admin` → `200`

- [ ] **Step 9: Commit**

```bash
git add myhive-next/package.json myhive-next/package-lock.json myhive-next/tsconfig.json \
  myhive-next/next.config.ts myhive-next/.gitignore myhive-next/.node-version \
  myhive-next/app/layout.tsx "myhive-next/app/[[...slug]]/page.tsx" myhive-next/next-env.d.ts
git commit -m "feat(next): scaffold Next.js App Router skeleton with root catch-all"
```

---

### Task 2: Legacy source sync script

**Files:**
- Create: `myhive-next/scripts/sync-legacy.mjs`
- Modify: `myhive-next/package.json` (add `predev`/`prebuild`)

**Interfaces:**
- Produces: `myhive-next/legacy-src/` — a verbatim copy of `myhive-react-app/src/` minus test files, refreshed automatically before every `dev`/`build`. Task 3 imports `legacy-src/App` (default export: the CRA `<App/>` component with its own `BrowserRouter`).

- [ ] **Step 1: Create `myhive-next/scripts/sync-legacy.mjs`**

```js
// Copies the canonical legacy CRA source into this project so the bundler never
// has to resolve modules outside the project root (avoids a second React copy
// being pulled from myhive-react-app/node_modules). legacy-src/ is gitignored:
// the single source of truth stays in myhive-react-app/src.
import { cpSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.resolve(here, '../../myhive-react-app/src');
const dest = path.resolve(here, '../legacy-src');

if (!existsSync(src)) {
  console.error(`sync-legacy: source not found: ${src}`);
  process.exit(1);
}

rmSync(dest, { recursive: true, force: true });
cpSync(src, dest, {
  recursive: true,
  filter: (p) => !p.endsWith('.test.js') && !p.endsWith('setupTests.js'),
});
console.log(`sync-legacy: ${src} -> ${dest}`);
```

- [ ] **Step 2: Wire into `package.json` scripts**

Replace the `scripts` block in `myhive-next/package.json` with:

```json
  "scripts": {
    "sync-legacy": "node scripts/sync-legacy.mjs",
    "predev": "node scripts/sync-legacy.mjs",
    "dev": "next dev",
    "prebuild": "node scripts/sync-legacy.mjs",
    "build": "next build",
    "start": "next start"
  },
```

- [ ] **Step 3: Run and verify**

Run: `cd myhive-next && npm run sync-legacy`
Expected output: `sync-legacy: /…/myhive-react-app/src -> /…/myhive-next/legacy-src`

Run: `ls legacy-src/App.js legacy-src/components/Layout.js legacy-src/context/AuthContext.js`
Expected: all three listed.

Run: `find legacy-src -name "*.test.js" -o -name "setupTests.js" | wc -l` → `0`

Run: `git status --short myhive-next/ | grep legacy-src | wc -l` → `0` (gitignored)

- [ ] **Step 4: Commit**

```bash
git add myhive-next/scripts/sync-legacy.mjs myhive-next/package.json
git commit -m "feat(next): build-time sync of legacy CRA source into legacy-src"
```

---

### Task 3: Mount the legacy SPA inside Next

**Files:**
- Modify: `myhive-next/package.json` (legacy runtime deps)
- Modify: `myhive-next/next.config.ts` (REACT_APP_* env bridge)
- Create: `myhive-next/.env.local.example`
- Create: `myhive-next/.env.local` (from real values; NOT committed)
- Create: `myhive-next/components/LegacyAppShim.tsx`
- Modify: `myhive-next/app/[[...slug]]/page.tsx`

**Interfaces:**
- Consumes: `legacy-src/App` (Task 2) — default-export React component; imports its own CSS (`bootstrap/dist/css/bootstrap.min.css`, `./styles/global.css`) and reads `process.env.REACT_APP_*` at module scope.
- Produces: `LegacyAppShim` (default export, no props) — the only sanctioned way to render legacy code; later plans reuse it for the `(legacy)` route group.

- [ ] **Step 1: Add legacy runtime dependencies**

Replace the `dependencies` block in `myhive-next/package.json` with (versions match `myhive-react-app/package.json`; `web-vitals`/`react-scripts`/testing libs are intentionally absent — nothing in the imported graph reaches them):

```json
  "dependencies": {
    "@dnd-kit/core": "^6.3.1",
    "@dnd-kit/sortable": "^10.0.0",
    "@dnd-kit/utilities": "^3.2.2",
    "bootstrap": "^5.3.8",
    "next": "^15.5.0",
    "oidc-client-ts": "^3.5.0",
    "react": "^19.2.3",
    "react-bootstrap": "^2.10.10",
    "react-day-picker": "^9.14.0",
    "react-dom": "^19.2.3",
    "react-helmet-async": "^3.0.0",
    "react-oidc-context": "^3.3.1",
    "react-router-dom": "^7.10.1"
  },
```

Run: `cd myhive-next && npm install`
Expected: clean install; `npm ls react` shows exactly one react version.

- [ ] **Step 2: Bridge legacy env names in `next.config.ts`**

Replace the whole file with:

```ts
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Legacy CRA code reads process.env.REACT_APP_* (inlined at build). Bridge the
  // Next-side env contract (NEXT_PUBLIC_* for browser-visible values, spec §8)
  // onto those names so legacy-src is never edited. REACT_APP_API_URL is pinned
  // to same-origin '/api' — served by the rewrite added in Task 4.
  env: {
    REACT_APP_API_URL: '/api',
    REACT_APP_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL ?? '',
    REACT_APP_OIDC_AUTHORITY: process.env.NEXT_PUBLIC_OIDC_AUTHORITY ?? '',
    REACT_APP_OIDC_CLIENT_ID: process.env.NEXT_PUBLIC_OIDC_CLIENT_ID ?? '',
    REACT_APP_OIDC_REDIRECT_URI: process.env.NEXT_PUBLIC_OIDC_REDIRECT_URI ?? '',
    REACT_APP_OIDC_AUDIENCE: process.env.NEXT_PUBLIC_OIDC_AUDIENCE ?? '',
    REACT_APP_OIDC_ROLES_CLAIM: process.env.NEXT_PUBLIC_OIDC_ROLES_CLAIM ?? '',
    REACT_APP_TURNSTILE_SITE_KEY: process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? '',
  },
};

export default nextConfig;
```

- [ ] **Step 3: Create `myhive-next/.env.local.example`**

```
# Server-only: backend base URL INCLUDING its path prefix.
# Local Spring serves at root; prod is https://myhive-backend.onrender.com/api
BACKEND_URL=http://localhost:8080

# Browser-visible values (bridged to REACT_APP_* in next.config.ts)
NEXT_PUBLIC_SITE_URL=http://localhost:3000
NEXT_PUBLIC_OIDC_AUTHORITY=https://YOUR-TENANT.auth0.com
NEXT_PUBLIC_OIDC_CLIENT_ID=YOUR_CLIENT_ID
NEXT_PUBLIC_OIDC_REDIRECT_URI=http://localhost:3000/admin
NEXT_PUBLIC_OIDC_AUDIENCE=https://api.trivlu.com
# Optional (legacy code has defaults):
# NEXT_PUBLIC_OIDC_ROLES_CLAIM=
# NEXT_PUBLIC_TURNSTILE_SITE_KEY=
```

Then create the real `myhive-next/.env.local`: copy each value from `myhive-react-app/.env` (`REACT_APP_OIDC_AUTHORITY` → `NEXT_PUBLIC_OIDC_AUTHORITY`, same for CLIENT_ID / REDIRECT_URI / AUDIENCE / TURNSTILE_SITE_KEY), keep `BACKEND_URL=http://localhost:8080` and `NEXT_PUBLIC_SITE_URL=http://localhost:3000`. The redirect URI stays `http://localhost:3000/admin` — Next dev uses the same port CRA did, so Auth0 already allows it.

- [ ] **Step 4: Create `myhive-next/components/LegacyAppShim.tsx`**

```tsx
'use client';

import dynamic from 'next/dynamic';

// The legacy tree touches window/localStorage at module scope (e.g.
// legacy-src/context/AuthContext.js builds its OIDC config from window at
// import time), so it must never be evaluated during server render. In Next 15
// `ssr: false` is only honored inside a Client Component — that is the entire
// reason this shim exists.
const LegacyApp = dynamic(() => import('../legacy-src/App'), {
  ssr: false,
  loading: () => (
    <div style={{ padding: '4rem', textAlign: 'center' }}>Loading…</div>
  ),
});

export default function LegacyAppShim() {
  return <LegacyApp />;
}
```

- [ ] **Step 5: Wire the catch-all to the shim** — replace `myhive-next/app/[[...slug]]/page.tsx` with:

```tsx
import LegacyAppShim from '../../components/LegacyAppShim';

// Optional catch-all: in Ф0 the legacy SPA owns every route and does its own
// client-side routing (BrowserRouter reads the real URL). Ф1 adds Server
// Component pages for public URLs and narrows this to (legacy) subtrees.
export default function CatchAllPage() {
  return <LegacyAppShim />;
}
```

- [ ] **Step 6: Verify in dev**

Run: `npm run dev`, then:
`curl -s http://localhost:3000/ | grep -c "Loading…"` → `1` (server HTML holds only the shim fallback — legacy is client-only, as designed)

Browser checks (manual, http://localhost:3000):
- `/` renders the Trivlu homepage (dark theme, header, footer) after the loading flash; browser console has **no** hydration-mismatch errors and no red errors (a failed `/api` fetch is expected until Task 4 if the backend is down).
- Client-side nav works: header links, `/blog`, `/about`.
- `/admin` shows the admin login screen (Auth0 redirect works with the values from `.env.local`).

- [ ] **Step 7: Commit**

```bash
git add myhive-next/package.json myhive-next/package-lock.json myhive-next/next.config.ts \
  myhive-next/.env.local.example myhive-next/components/LegacyAppShim.tsx \
  "myhive-next/app/[[...slug]]/page.tsx"
git commit -m "feat(next): mount legacy CRA SPA via client-only shim on all routes"
```

---

### Task 4: Same-origin `/api` rewrite to the Spring backend

**Files:**
- Modify: `myhive-next/next.config.ts` (add `rewrites`)

**Interfaces:**
- Consumes: `BACKEND_URL` env (Task 3 `.env.local`).
- Produces: `GET/POST http://localhost:3000/api/<x>` → `${BACKEND_URL}/<x>`. Legacy fetches like `` `${API_BASE_URL}/destinations` `` (`legacy-src/services/api.js`) become `/api/destinations` because `REACT_APP_API_URL='/api'`. Plan 2's Server Components will fetch `${process.env.BACKEND_URL}/<x>` directly — same contract.

- [ ] **Step 1: Add the rewrite** — replace `myhive-next/next.config.ts` with:

```ts
import type { NextConfig } from 'next';

// Server-only backend base, INCLUDING its path prefix: local Spring serves at
// root (http://localhost:8080), prod behind the container that strips /api
// (https://myhive-backend.onrender.com/api). The browser only ever talks
// same-origin /api/* — no public env var carries the backend URL.
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${BACKEND_URL}/:path*`,
      },
    ];
  },
  // Legacy CRA code reads process.env.REACT_APP_* (inlined at build). Bridge the
  // Next-side env contract (NEXT_PUBLIC_* for browser-visible values, spec §8)
  // onto those names so legacy-src is never edited. REACT_APP_API_URL is pinned
  // to same-origin '/api' — served by the rewrite above.
  env: {
    REACT_APP_API_URL: '/api',
    REACT_APP_SITE_URL: process.env.NEXT_PUBLIC_SITE_URL ?? '',
    REACT_APP_OIDC_AUTHORITY: process.env.NEXT_PUBLIC_OIDC_AUTHORITY ?? '',
    REACT_APP_OIDC_CLIENT_ID: process.env.NEXT_PUBLIC_OIDC_CLIENT_ID ?? '',
    REACT_APP_OIDC_REDIRECT_URI: process.env.NEXT_PUBLIC_OIDC_REDIRECT_URI ?? '',
    REACT_APP_OIDC_AUDIENCE: process.env.NEXT_PUBLIC_OIDC_AUDIENCE ?? '',
    REACT_APP_OIDC_ROLES_CLAIM: process.env.NEXT_PUBLIC_OIDC_ROLES_CLAIM ?? '',
    REACT_APP_TURNSTILE_SITE_KEY: process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? '',
  },
};

export default nextConfig;
```

- [ ] **Step 2: Start the local backend**

Run: `cd myhive-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (or however the backend is normally run locally — check `myhive-backend/README.md` if this fails). Wait for Spring startup.

- [ ] **Step 3: Verify the rewrite**

Restart `npm run dev` (config change), then:
`curl -s http://localhost:3000/api/destinations | head -c 200`
Expected: JSON (starts with `[{"id":` …), **not** HTML.

Browser: reload `http://localhost:3000/` — homepage shows real activity cards fetched through `/api`; Network tab shows requests to `localhost:3000/api/...` (no direct `localhost:8080` calls).

- [ ] **Step 4: Commit**

```bash
git add myhive-next/next.config.ts
git commit -m "feat(next): same-origin /api rewrite to Spring backend"
```

---

### Task 5: Head parity — static assets, metadata, GTM/consent, Turnstile

**Files:**
- Create: `myhive-next/public/*` (copied from CRA)
- Create: `myhive-next/public/robots.txt` (preview: disallow all)
- Modify: `myhive-next/app/layout.tsx`

**Interfaces:**
- Consumes: nothing new. Produces: served HTML whose `<head>` matches `myhive-react-app/public/index.html` (fonts, phosphor icons, favicons, manifest, generic OG/Twitter meta, GTM with localhost skip + Consent Mode expectations, Turnstile loader). Plan 2 replaces the generic metadata with per-page `generateMetadata` — the layout keeps only site-wide defaults.

- [ ] **Step 1: Copy CRA public assets**

Run from repo root:

```bash
rsync -a --exclude 'index.html' --exclude 'robots.txt' myhive-react-app/public/ myhive-next/public/
ls myhive-next/public
```

Expected: favicons, `og-image.png`, `manifest.json`, logos — no `index.html`, no `robots.txt`.

- [ ] **Step 2: Create `myhive-next/public/robots.txt`**

```
# TEMPORARY (Ф0): this service runs on a preview URL only — block indexing so
# the preview never competes with the live CRA site. Ф1/Ф2 replace this file
# with app/robots.ts (spec §9 Ф2: explicit disallow list /admin/ /vote/ /payment/).
User-agent: *
Disallow: /
```

- [ ] **Step 3: Replace `myhive-next/app/layout.tsx`**

```tsx
import type { Metadata, Viewport } from 'next';
import Script from 'next/script';

// og/twitter URLs must be absolute on the canonical host — WhatsApp/Telegram
// scrapers refuse redirected og:image URLs (apex 301s to www).
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';

const TITLE = 'Trivlu — Group Travel Made Easy';
const DESCRIPTION =
  'Turn group travel chaos into epic adventures with zero stress. Trivlu is the first AI trip maker for multi-traveler experiences.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  manifest: '/manifest.json',
  icons: {
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/favicon-16.png', sizes: '16x16', type: 'image/png' },
      { url: '/favicon-32.png', sizes: '32x32', type: 'image/png' },
    ],
    apple: '/apple-touch-icon.png',
  },
  openGraph: {
    title: TITLE,
    description: DESCRIPTION,
    type: 'website',
    url: SITE_URL,
    images: [
      { url: `${SITE_URL}/og-image.png`, width: 1000, height: 1000, type: 'image/png' },
    ],
  },
  twitter: {
    card: 'summary',
    images: [`${SITE_URL}/og-image.png`],
  },
};

export const viewport: Viewport = {
  themeColor: '#000000',
};

// GTM is skipped on localhost: the container/CookieYes aren't configured for it
// and throw a cross-origin "Script error." — same guard the CRA index.html used.
// Consent Mode v2 / CookieYes load through the GTM container itself.
const GTM_SNIPPET = `if(location.hostname!=='localhost'&&location.hostname!=='127.0.0.1'&&!location.hostname.endsWith('.localhost')){(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','GTM-KB7BJLDS');}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {/* React 19 hoists these to <head> */}
        <link
          href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600&display=swap"
          rel="stylesheet"
        />
        <link
          rel="stylesheet"
          href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/regular/style.css"
        />
        <Script id="gtm" strategy="afterInteractive">
          {GTM_SNIPPET}
        </Script>
        {/* Turnstile loader (render=explicit) — safe on localhost: ContactForm
            uses Cloudflare's "always passes" test sitekey there. */}
        <Script
          src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
          strategy="afterInteractive"
        />
        <noscript>
          <iframe
            src="https://www.googletagmanager.com/ns.html?id=GTM-KB7BJLDS"
            height="0"
            width="0"
            style={{ display: 'none', visibility: 'hidden' }}
          />
        </noscript>
        {children}
      </body>
    </html>
  );
}
```

- [ ] **Step 4: Verify served HTML**

Run: `npm run dev`, then:
`curl -s http://localhost:3000/ > /tmp/next-home.html`
`grep -c 'og:image' /tmp/next-home.html` → `>= 1` (absolute URL on SITE_URL host)
`grep -c 'favicon.svg' /tmp/next-home.html` → `1`
`grep -c 'manifest.json' /tmp/next-home.html` → `1`
`grep -c 'GTM-KB7BJLDS' /tmp/next-home.html` → `>= 1`
`grep -c 'challenges.cloudflare.com/turnstile' /tmp/next-home.html` → `1`
`curl -s http://localhost:3000/robots.txt | grep -c 'Disallow: /'` → `1`
`curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/og-image.png` → `200`

Browser: fonts/phosphor icons render on the homepage (no missing-icon squares); console still clean on localhost (GTM skipped by the hostname guard).

- [ ] **Step 5: Commit**

```bash
git add myhive-next/public myhive-next/app/layout.tsx
git commit -m "feat(next): head parity with CRA — assets, metadata, GTM/consent, Turnstile; preview robots disallow"
```

---

### Task 6: Production build verification

**Files:** none expected (fix-forward only if the build surfaces an issue).

**Interfaces:**
- Produces: proof that `npm run build && npm run start` — the exact commands Render will run — works.

- [ ] **Step 1: Build**

Run: `cd myhive-next && npm run build`
Expected: `sync-legacy` runs first (prebuild), then `✓ Compiled successfully`; route table lists `/[[...slug]]` and no errors about `window is not defined` (would mean something legacy leaked out of the `ssr:false` boundary — stop and fix the import path, don't work around it).

- [ ] **Step 2: Serve the production build**

Run: `npm run start`, then:
`curl -s http://localhost:3000/ | grep -c "Loading…"` → `1`
`curl -s http://localhost:3000/api/destinations | head -c 50` → JSON (backend from Task 4 still running)
Browser: homepage, `/admin`, `/blog` all behave as in dev.

- [ ] **Step 3: Commit (only if fixes were needed)**

```bash
git add -A myhive-next
git commit -m "fix(next): production build fixes"
```

---

### Task 7: Render web service + external allowlists (ops)

**Files:**
- None in this repo (dashboard work). Record the resulting preview URL in the PR description.

**Interfaces:**
- Produces: a live preview URL (assumed `https://myhive-next.onrender.com` — substitute the real one everywhere below) serving the app; backend/Auth0 aware of the new origin. Task 8 tests against it.

- [ ] **Step 1: Create the Render web service**

Render dashboard → New → Web Service, from this repo:
- Name: `myhive-next`; Runtime: Node
- **Root Directory:** `myhive-next` (the repo is cloned whole, so `../myhive-react-app/src` stays reachable for `sync-legacy`)
- Build command: `npm ci && npm run build`
- Start command: `npm run start`
- Node version comes from `.node-version` (20.20.0)

- [ ] **Step 2: Set service env vars** (Render dashboard, same names as `.env.local.example`)

```
BACKEND_URL=https://myhive-backend.onrender.com/api
NEXT_PUBLIC_SITE_URL=https://myhive-next.onrender.com
NEXT_PUBLIC_OIDC_AUTHORITY=<copy from CRA Render service / myhive-react-app/.env>
NEXT_PUBLIC_OIDC_CLIENT_ID=<copy>
NEXT_PUBLIC_OIDC_REDIRECT_URI=https://myhive-next.onrender.com/admin
NEXT_PUBLIC_OIDC_AUDIENCE=<copy>
NEXT_PUBLIC_TURNSTILE_SITE_KEY=<copy>
```

(`NEXT_PUBLIC_SITE_URL` switches to `https://www.trivlu.com` at Ф2 cutover.)

- [ ] **Step 3: Extend backend CORS/origin allowlist**

On the **backend** Render service set `CORS_ALLOWED_ORIGINS`. ⚠️ The env var **replaces** the default list in `WebConfig.DEFAULT_ALLOWED_ORIGINS` (`myhive-backend/src/main/java/com/myhive/backend/config/WebConfig.java:17-22`), so include everything:

```
CORS_ALLOWED_ORIGINS=https://trivlu.com,https://www.trivlu.com,https://*.trivlu.com,https://myhive-frontend.onrender.com,http://localhost:3000,http://127.0.0.1:3000,https://myhive-next.onrender.com
```

Note: with the `/api` rewrite, browser CORS barely applies (calls are same-origin, proxied by Next) — but `FrontendUrlResolver` uses this same list to honor the `Origin` header for **Stripe return URLs**. Without the preview origin listed, a payment started on preview would return the user to prod CRA.

- [ ] **Step 4: Auth0 application settings** — add to the existing SPA application:
- Allowed Callback URLs: `https://myhive-next.onrender.com/admin`
- Allowed Logout URLs: `https://myhive-next.onrender.com`
- Allowed Web Origins: `https://myhive-next.onrender.com`

- [ ] **Step 5: Stripe** — no change expected: return URLs are backend-computed via `FrontendUrlResolver` (covered by Step 3). Only if the Stripe dashboard enforces a domain allowlist (Payment Element / Apple Pay) add `myhive-next.onrender.com`; hosted Checkout needs nothing.

- [ ] **Step 6: Deploy and verify**

Trigger deploy, then:
`curl -s -o /dev/null -w "%{http_code}" https://myhive-next.onrender.com/` → `200`
`curl -s https://myhive-next.onrender.com/api/destinations | head -c 50` → JSON
`curl -s https://myhive-next.onrender.com/robots.txt` → contains `Disallow: /`

---

### Task 8: Preview smoke tests — revenue flows (browser checklist)

**Files:**
- None (verification task; record results in the PR description).

**Interfaces:**
- Consumes: the live preview URL from Task 7. This is the spec §11 pre-cutover browser-test set, run on the Ф0 preview.

- [ ] **Step 1: Public + hydration.** Open `https://myhive-next.onrender.com/`: homepage renders with live data; DevTools console has no hydration-mismatch or uncaught errors. Repeat for `/destination/prague`, one activity card, `/blog`, `/about`. Direct URL loads (paste in a fresh tab) work — not just client-side nav.
- [ ] **Step 2: Admin.** `/admin` → Auth0 login → dashboard loads; open Activities list; edit-and-save one harmless field (e.g. toggle featured off/on) to prove authenticated API writes work through `/api`.
- [ ] **Step 3: Vote flow (the `location.state`-dependent one).** Add 2+ activities to the trip → start a group vote → quiz (`/vote/new/quiz`) → curate (`/vote/new/curate`) → get share link. Open the share link **in an incognito window** (fresh full-page load through the catch-all): `/vote/<token>/quiz` → swipe/vote activities → waiting → result. Both the state-driven transitions and the cold-load token pages must work.
- [ ] **Step 4: Payment (Stripe test mode).** Start a deposit checkout from the preview; complete with test card `4242 4242 4242 4242`; confirm redirect returns to `https://myhive-next.onrender.com/payment/success` (proves the `FrontendUrlResolver` origin change from Task 7 Step 3). Cancel path lands on `/payment/cancelled`.
- [ ] **Step 5: Contact form.** Submit with Turnstile on the preview host — expect success (production sitekey is host-bound; if Turnstile rejects the onrender host, add the preview hostname in the Cloudflare Turnstile widget settings and retest).
- [ ] **Step 6: Record.** Note pass/fail per flow in the PR description; any failure is a blocker for calling Ф0 done.

---

## Out of scope for this plan (later plans)

- **Plan 2 (Ф1):** public pages as Server Components on current URLs — per-page metadata/canonical/OG, JSON-LD (Organization/Article/BreadcrumbList), breadcrumbs, `notFound()` 404s incl. the city-mismatch guard, `app/sitemap.ts` + `app/robots.ts`, Header/Footer port, ISR.
- **Plan 3 (Ф2):** cutover — DNS, Cloudflare robots.txt un-hijack, canonical host, GSC verification + sitemap submission, live-response acceptance checks.
- **Ф3/Ф4** per spec §9.
