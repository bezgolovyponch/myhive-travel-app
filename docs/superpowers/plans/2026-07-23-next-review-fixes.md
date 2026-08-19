# Next.js PR Review Fixes + SEO Gate + Markdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all confirmed review blockers on `feat/nextjs-foundation` (Render build, double chrome, add-to-trip no-op, €NaN, rate-limit/404 semantics, soft 404s, robots/OG/JSON-LD issues) and implement the approved SEO readiness gate consumption + Markdown blog rendering.

**Architecture:** The destination page moves out of the `(public)` route group so its SPA escape hatch can render chrome-less (CRA brings its own header/footer); SSR pages get chrome from a shared `PublicChrome` component. OG metadata and JSON-LD serialization go through two shared helpers in `lib/seo.ts`. Markdown rendering is ONE component living in the CRA repo (`src/components/MarkdownContent.js`), Jest-tested there, and consumed by the Next blog page through the existing `sync-legacy` copy (`legacy-src/`). The `seoIndexable` flag (delivered by the backend PR from `2026-07-23-backend-seo-gate-internal-token.md`) gates the sitemap and emits `noindex, follow`; Next enforces the parent-child rule by fetching the destination on child pages (deduped/cached by Next's fetch cache).

**Tech Stack:** Next 15 App Router (React 19 Server Components), CRA (react-scripts 5, Jest, Testing Library), react-markdown ^9.0.1 + remark-gfm ^4.0.0.

## Global Constraints

- Branch: current `feat/nextjs-foundation`. The backend PR (`feat/seo-gate-internal-token`) must be merged/deployed before production cutover, but this branch builds and runs against the old backend too (missing `seoIndexable` ⇒ treated as `false`).
- Working directory for Next commands: `/Users/olga/PycharmProjects/myhive-travel-app/myhive-next`. For CRA commands: `/Users/olga/PycharmProjects/myhive-travel-app/myhive-react-app`.
- `react-markdown` is `^9.0.1` and `remark-gfm` is `^4.0.0` in BOTH `myhive-next/package.json` and `myhive-react-app/package.json` (Next bundles `legacy-src`, so both node_modules must resolve them).
- Internal token: env var `INTERNAL_API_TOKEN`, header `X-Internal-Token` (must match backend plan exactly).
- Missing/`undefined` `seoIndexable` always means **not indexable**.
- Deep-link params: `add=<activity-slug>`, `addPackage=<package-slug>`, only meaningful with `tab=trip-builder`.
- CRA tests run with `npm test -- --watchAll=false`. Next has no unit-test runner; Next-side changes are verified by `npm run build` + smoke/curl checks in the final task (documented deviation: the required Markdown DOM tests live in CRA Jest against the exact component the Next page renders).
- After any change under `myhive-react-app/src`, run `npm run sync-legacy` in `myhive-next` before building (prebuild also does it automatically).

---

### Task 1: `lib/seo.ts` helpers + static-page metadata (OG merge fix, JSON-LD escaping)

**Files:**
- Modify: `myhive-next/lib/seo.ts`
- Modify: `myhive-next/app/(public)/page.tsx`, `app/(public)/about/page.tsx`, `app/(public)/contact/page.tsx`, `app/(public)/blog/page.tsx`, `app/(public)/terms/page.tsx`, `app/(public)/privacy-policy/page.tsx`, `app/(public)/cookie-policy/page.tsx`, `app/(public)/refund-policy/page.tsx`

**Interfaces:**
- Produces: `pageMetadata({title, description, path, image?, ogType?, noindex?}): Metadata` and `jsonLd(data: unknown): string` — every later task uses these; do not hand-roll `openGraph` or `JSON.stringify` for JSON-LD anywhere after this task.

- [ ] **Step 1: Add the helpers to `lib/seo.ts`**

```ts
import type { Metadata } from 'next';

interface PageMeta {
  title: string;
  description: string;
  /** Absolute-relative path, e.g. '/about'. */
  path: string;
  /** Absolute image URL; falls back to the brand og-image. */
  image?: string;
  ogType?: 'website' | 'article';
  /** Emits robots noindex,follow (per-record SEO gate). */
  noindex?: boolean;
}

/** Uniform metadata: Next merges `openGraph` shallowly (a page-level object
 *  REPLACES the root one), so every page must emit a complete OG set or lose
 *  og:image/og:type. This is the single place that guarantees completeness. */
export function pageMetadata({ title, description, path, image, ogType = 'website', noindex = false }: PageMeta): Metadata {
  const url = canonical(path);
  return {
    title,
    description,
    alternates: { canonical: url },
    ...(noindex ? { robots: { index: false, follow: true } } : {}),
    openGraph: {
      title,
      description,
      url,
      type: ogType,
      images: image
        ? [{ url: image }]
        : [{ url: `${SITE_URL}/og-image.png`, width: 1000, height: 1000, type: 'image/png' }],
    },
  };
}

/** JSON-LD for <script dangerouslySetInnerHTML>: '<' must be escaped so
 *  backend-controlled strings can't close the script tag (XSS). */
export function jsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}
```

(`import type { Metadata }` goes at the top of the file; `canonical`/`SITE_URL` already exist there.)

- [ ] **Step 2: Convert the 8 static pages**

Each currently exports a `metadata` object with `title/description/alternates(/openGraph)`. Replace the whole object with the helper. Example for `about/page.tsx`:

```ts
import { pageMetadata } from '../../../lib/seo';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/about',
});
```

Apply identically with paths `/` (home), `/contact`, `/blog`, `/terms`, `/privacy-policy`, `/cookie-policy`, `/refund-policy`, keeping each file's existing `TITLE`/`DESCRIPTION` constants. Keep other existing imports of `canonical` only where still used. This *adds* complete OG (with fallback image) to the four legal pages, which previously inherited the homepage OG wholesale.

- [ ] **Step 3: Build check**

Run: `cd /Users/olga/PycharmProjects/myhive-travel-app/myhive-next && npm run build`
Expected: build succeeds (backend fetch failures during prerender are acceptable if the local backend is down — check for TypeScript errors specifically).

- [ ] **Step 4: Commit**

```bash
git add lib/seo.ts 'app/(public)/page.tsx' 'app/(public)/about/page.tsx' 'app/(public)/contact/page.tsx' 'app/(public)/blog/page.tsx' 'app/(public)/terms/page.tsx' 'app/(public)/privacy-policy/page.tsx' 'app/(public)/cookie-policy/page.tsx' 'app/(public)/refund-policy/page.tsx'
git commit -m "fix(next): complete OG on every page via pageMetadata helper; jsonLd escaper"
```

---

### Task 2: `lib/api.ts` — internal token header, real package price fields, seoIndexable

**Files:**
- Modify: `myhive-next/lib/api.ts`

**Interfaces:**
- Produces: `TripPackage` now has `originalPrice: number; discountedPrice: number; savings: number; discountPct?: number | null` and NO `price`. `Destination`, `Activity`, `TripPackage`, `BlogPost` gain `seoIndexable?: boolean | null`. Server fetches send `X-Internal-Token` when `INTERNAL_API_TOKEN` is set.

- [ ] **Step 1: Edit `get()`**

```ts
async function get<T>(path: string): Promise<T | null> {
  // Rate-limit exemption for server-to-server traffic (cold ISR fills render
  // the whole catalog from one egress IP). Read per-request like BACKEND.
  const token = process.env.INTERNAL_API_TOKEN;
  const res = await fetch(`${BACKEND}${path}`, {
    next: { revalidate: REVALIDATE_SECONDS },
    ...(token ? { headers: { 'X-Internal-Token': token } } : {}),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Backend ${res.status} on ${path}`);
  return res.json();
}
```

- [ ] **Step 2: Fix the DTOs**

In `TripPackage`, replace `price: number;` with:

```ts
  discountPct?: number | null;
  originalPrice: number;
  discountedPrice: number;
  savings: number;
```

Add to `Destination`, `Activity`, `TripPackage`, and `BlogPost` interfaces:

```ts
  seoIndexable?: boolean | null;
```

- [ ] **Step 3: Typecheck** — `npm run build`. Expected: FAILS in `app/(public)/destination/[slug]/page.tsx` and `.../package/[pslug]/page.tsx` on `pkg.price` — that is the €NaN bug surfacing at compile time; fixed in Task 3. (If the build tool stops at the first error, note both files and proceed.)

- [ ] **Step 4: Commit** — `git add lib/api.ts && git commit -m "fix(next): package DTO matches backend (discountedPrice et al); internal token header; seoIndexable fields"`

---

### Task 3: Render real package prices (catalog card + detail)

**Files:**
- Modify: `myhive-next/app/(public)/destination/[slug]/page.tsx` (packages grid, ~line 149)
- Modify: `myhive-next/app/(public)/destination/[slug]/package/[pslug]/page.tsx` (price card, ~line 145)

**Interfaces:**
- Consumes: `TripPackage.originalPrice/discountedPrice/savings/discountPct` from Task 2.

- [ ] **Step 1: Destination catalog card** — replace `<span className="activity-price">€{Math.round(pkg.price)}</span>` with:

```tsx
<span className="activity-price">€{Math.round(pkg.discountedPrice)}</span>
```

- [ ] **Step 2: Package detail price card** — replace the single `package-detail-discounted` div with legacy parity (`legacy-src/pages/PackageDetailPage.js` renders original + discounted + savings):

```tsx
<div className="package-detail-original">€{Math.round(pkg.originalPrice)}</div>
<div className="package-detail-discounted">€{Math.round(pkg.discountedPrice)}</div>
<div className="package-detail-savings">
  You save €{Math.round(pkg.savings)}
  {pkg.discountPct ? ` (${Math.round(pkg.discountPct)}% off)` : ''}
</div>
```

- [ ] **Step 3: Build** — `npm run build` → succeeds (the Task 2 type errors are gone).
- [ ] **Step 4: Commit** — `git add 'app/(public)/destination/[slug]/page.tsx' 'app/(public)/destination/[slug]/package/[pslug]/page.tsx' && git commit -m "fix(next): package prices use discountedPrice/originalPrice/savings (was €NaN)"`

---

### Task 4: Chrome-less escape hatch + metadata short-circuit (destination page)

**Files:**
- Create: `myhive-next/components/site/PublicChrome.tsx`
- Modify: `myhive-next/app/(public)/layout.tsx`
- Move: `myhive-next/app/(public)/destination/[slug]/page.tsx` → `myhive-next/app/destination/[slug]/page.tsx` (the `(public)/destination/[slug]/activity|package` subtrees STAY where they are — a group folder without `page.tsx` is a pure path segment, no route conflict)

**Interfaces:**
- Produces: `PublicChrome({children})` — same DOM as the old group layout (`.app-container > Header > main > Footer`, global CSS imports). The moved page uses `pageMetadata`/`jsonLd` from Task 1.

- [ ] **Step 1: Create `components/site/PublicChrome.tsx`**

```tsx
// Shared chrome for the SSR public pages: same DOM shape as the legacy SPA's
// Layout.js (.app-container > Header > main > Footer) so global CSS applies.
// A component (not only a route-group layout) so pages with an SPA escape
// hatch can render chrome-less — the CRA tree mounts its own Header/Footer.
import 'bootstrap/dist/css/bootstrap.min.css';
import '../../legacy-src/styles/global.css';
import Header from './Header';
import Footer from './Footer';

export default function PublicChrome({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-container">
      <Header />
      <main>{children}</main>
      <Footer />
    </div>
  );
}
```

- [ ] **Step 2: Reduce `app/(public)/layout.tsx` to**

```tsx
import PublicChrome from '../../components/site/PublicChrome';

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return <PublicChrome>{children}</PublicChrome>;
}
```

- [ ] **Step 3: Move the destination page and rework it**

`git mv 'app/(public)/destination/[slug]/page.tsx' 'app/destination/[slug]/page.tsx'`, then edit the moved file:

1. Import paths drop one level (`../../../../lib/api` → `../../../lib/api`, same for `lib/seo`, `components/...`, `legacy-src/...`).
2. Add `import PublicChrome from '../../../components/site/PublicChrome';`
3. `generateMetadata` short-circuits BEFORE any backend call, and uses the Task 1 helper:

```tsx
export async function generateMetadata({ params, searchParams }: PageParams): Promise<Metadata> {
  const sp = await searchParams;
  // SPA-owned URL state must render even during a backend outage, and the
  // parameterized variants must never be indexed.
  if (sp.tab != null || sp.voteSession != null) {
    return { title: 'Trip Builder | Trivlu', robots: { index: false, follow: true } };
  }
  const { slug } = await params;
  const dest = await api.getDestinationBySlug(slug);
  if (!dest) {
    return { title: 'Destination not found | Trivlu' };
  }
  const isPrague = slug === 'prague';
  return pageMetadata({
    title: isPrague ? PRAGUE_TITLE : `${dest.name} Stag Do — Activities & Packages | Trivlu`,
    description: isPrague
      ? PRAGUE_DESCRIPTION
      : dest.description ||
        `Everything for a ${dest.name} stag do: top activities, prices for the group and instant trip building.`,
    path: `/destination/${slug}`,
    image: dest.imageUrl || undefined,
    noindex: !dest.seoIndexable,
  });
}
```

4. The component's escape-hatch branch returns the shim BARE (no chrome — CRA `App.js` imports bootstrap+global.css and renders its own `<Layout/>`), and the SSR branch wraps in `PublicChrome`:

```tsx
  if (sp.tab != null || sp.voteSession != null) {
    return <LegacyAppShim />;
  }
  // ...fetches unchanged...
  return (
    <PublicChrome>
      <div className="destination-page">
        {/* existing content unchanged, except: */}
      </div>
    </PublicChrome>
  );
```

5. The breadcrumb script uses the escaper: `dangerouslySetInnerHTML={{ __html: jsonLd(breadcrumbLd) }}` (import `jsonLd`, `pageMetadata` from `lib/seo`).

- [ ] **Step 4: Verify**

Run: `npm run build` → succeeds, route list shows `/destination/[slug]` once.
Then `npm run dev` in background, and:
- `curl -s 'http://localhost:3000/destination/prague?tab=trip-builder' | grep -c 'class="app-container"'` → expected `0` (no server-rendered Next chrome; CRA mounts client-side).
- `curl -s 'http://localhost:3000/destination/prague' | grep -c 'class="app-container"'` → expected `1` (needs the local backend running; if unavailable, note and verify in final task).
Stop dev server.

- [ ] **Step 5: Commit**

```bash
git add components/site/PublicChrome.tsx 'app/(public)/layout.tsx' 'app/destination/[slug]/page.tsx'
git commit -m "fix(next): SPA escape hatch renders chrome-less; metadata skips backend on SPA URLs"
# (git mv already staged the deletion of the old path)
```

---

### Task 5: Restrict the catch-all + robots bare-root prefixes

**Files:**
- Modify: `myhive-next/app/[...slug]/page.tsx`
- Modify: `myhive-next/app/robots.ts`

- [ ] **Step 1: Catch-all allowlist** — replace the component:

```tsx
import { notFound } from 'next/navigation';
import LegacyAppShim from '../../components/LegacyAppShim';

// The SPA legitimately owns only the service flows; every public URL has an
// SSR page that wins route resolution. Anything else is a real 404 — mounting
// the SPA unconditionally turned unknown URLs into soft 404s (HTTP 200).
const SPA_PREFIXES = new Set(['admin', 'vote', 'payment']);

export default async function CatchAllPage({
  params,
}: {
  params: Promise<{ slug: string[] }>;
}) {
  const { slug } = await params;
  if (!SPA_PREFIXES.has(slug[0])) {
    notFound();
  }
  return <LegacyAppShim />;
}
```

(Keep the existing explanatory comment about route resolution, merged with the above.)

- [ ] **Step 2: robots.ts** — change the production disallow list to bare prefixes (covers `/admin` AND `/admin/...`):

```ts
    rules: { userAgent: '*', disallow: ['/admin', '/vote', '/payment'] },
```

- [ ] **Step 3: Verify** — `npm run build`; then with `npm run dev`:
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/totally-unknown` → `404`
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/destinations` → `404`
- `curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/vote/new` → `200`

- [ ] **Step 4: Commit** — `git add 'app/[...slug]/page.tsx' app/robots.ts && git commit -m "fix(next): 404 unknown routes (catch-all allowlist); robots covers bare /admin /vote /payment"`

---

### Task 6: Blog error semantics — outage must not 404 valid posts

**Files:**
- Modify: `myhive-next/app/(public)/blog/[slug]/page.tsx`

- [ ] **Step 1: Remove the catch-alls.** In both `generateMetadata` and the page component, change

```ts
const post = await api.getBlogPostBySlug(slug).catch(() => null);
```

to

```ts
const post = await api.getBlogPostBySlug(slug);
```

`get()` returns `null` only on backend 404 (→ `notFound()` stays correct); any 5xx/429 now throws → Next serves the error page / keeps the stale ISR copy on revalidation, instead of a hard 404.

- [ ] **Step 2: Use `jsonLd`** for both `<script type="application/ld+json">` blocks in this file (import from `../../../../lib/seo`), replacing raw `JSON.stringify`.

- [ ] **Step 3: Build** — `npm run build` → succeeds.
- [ ] **Step 4: Commit** — `git add 'app/(public)/blog/[slug]/page.tsx' && git commit -m "fix(next): backend errors on blog posts are 5xx not 404; escape JSON-LD"`

---

### Task 7: seoIndexable consumption — sitemap gate + noindex on catalog pages

**Files:**
- Modify: `myhive-next/app/sitemap.ts`
- Modify: `myhive-next/app/destination/[slug]/page.tsx` (already emits `noindex` via Task 4 — no change; listed for context)
- Modify: `myhive-next/app/(public)/destination/[slug]/activity/[aslug]/page.tsx`
- Modify: `myhive-next/app/(public)/destination/[slug]/package/[pslug]/page.tsx`
- Modify: `myhive-next/app/(public)/blog/[slug]/page.tsx`

**Interfaces:**
- Consumes: `seoIndexable?: boolean | null` fields (Task 2), `pageMetadata`/`jsonLd` (Task 1).
- Rule (from the approved spec): child indexable ⇔ own flag AND destination flag; missing flag = false; non-indexable records render normally but emit `noindex, follow` and are excluded from the sitemap.

- [ ] **Step 1: Sitemap gate.** In `app/sitemap.ts`, inside the destinations loop:

```ts
    for (const dest of destinations) {
      // Per-record SEO gate: unready records are excluded, and a destination
      // that is not indexable excludes ALL of its children (parent rule).
      if (!dest.seoIndexable) continue;
      urls.push({ url: `${SITE_URL}/destination/${dest.slug}`, priority: 0.9 });
      const [activities, packages] = await Promise.all([
        api.getActivities(dest.id).catch(() => null),
        api.getPackages(dest.id).catch(() => null),
      ]);
      for (const a of activities ?? []) {
        if (!a.seoIndexable) continue;
        urls.push({ url: `${SITE_URL}/destination/${dest.slug}/activity/${a.slug}`, priority: 0.7 });
      }
      for (const p of packages ?? []) {
        if (!p.seoIndexable) continue;
        urls.push({ url: `${SITE_URL}/destination/${dest.slug}/package/${p.slug}`, priority: 0.6 });
      }
    }
```

and filter posts: `for (const post of posts.filter((p) => p.seoIndexable)) { ... }`.

- [ ] **Step 2: Activity page.** In `generateMetadata`, fetch the destination alongside the activity (Next memoizes identical fetches within a render pass, and the page's own calls hit the same ISR cache — no extra backend load in steady state):

```tsx
export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { slug, aslug } = await params;
  const [activity, dest] = await Promise.all([
    api.getActivityBySlug(aslug),
    api.getDestinationBySlug(slug),
  ]);
  if (!activity) {
    return { title: 'Activity not found | Trivlu' };
  }
  const destinationName = destinationNameFromSlug(slug);
  const title = `${activity.name} in ${destinationName} | Trivlu`;
  return pageMetadata({
    title,
    description: activity.description
      ? truncate(activity.description)
      : `${activity.name} in ${destinationName}.`,
    path: `/destination/${slug}/activity/${activity.slug}`,
    image: activity.imageUrl || undefined,
    noindex: !(activity.seoIndexable && dest?.seoIndexable),
  });
}
```

Also switch the breadcrumb script in the page body to `jsonLd(breadcrumbLd)`.

- [ ] **Step 3: Package page.** Same shape: `Promise.all([api.getPackageBySlug(pslug), api.getDestinationBySlug(slug)])` in `generateMetadata`, `noindex: !(pkg.seoIndexable && dest?.seoIndexable)`, converted to `pageMetadata` (keep its existing title/description/image expressions), and `jsonLd` for the breadcrumb script.

- [ ] **Step 4: Blog post page.** Convert `generateMetadata` to the helper, adding the gate:

```tsx
  return pageMetadata({
    title,
    description,
    path: `/blog/${post.slug || slug}`,
    image: post.imageUrl || undefined,
    ogType: 'article',
    noindex: !post.seoIndexable,
  });
```

- [ ] **Step 5: Verify** — `npm run build`; with dev server + local backend (old backend without the flag is fine — everything gates to noindex):
`curl -s http://localhost:3000/destination/prague | grep -o '<meta name="robots"[^>]*'` → contains `noindex` (flag absent locally). `curl -s http://localhost:3000/sitemap.xml | grep -c '/destination/'` → `0` with a flagless backend.

- [ ] **Step 6: Commit**

```bash
git add app/sitemap.ts 'app/(public)/destination/[slug]/activity/[aslug]/page.tsx' 'app/(public)/destination/[slug]/package/[pslug]/page.tsx' 'app/(public)/blog/[slug]/page.tsx'
git commit -m "feat(next): per-record SEO gate — sitemap exclusion + noindex,follow with parent rule"
```

---

### Task 8: Shared Markdown renderer (CRA component + Jest tests + Next blog page)

**Files:**
- Modify: `myhive-react-app/package.json` (deps + Jest `transformIgnorePatterns`)
- Create: `myhive-react-app/src/components/MarkdownContent.js`
- Test: create `myhive-react-app/src/components/MarkdownContent.test.js`
- Modify: `myhive-react-app/src/pages/BlogPostPage.js` (legacy parity — in-SPA navigation can still reach blog posts)
- Modify: `myhive-next/package.json` (same deps)
- Modify: `myhive-next/app/(public)/blog/[slug]/page.tsx`

**Interfaces:**
- Produces: `MarkdownContent({children: string})` — GFM markdown → React elements. No raw HTML rendering, react-markdown's default URL sanitizer active. Next imports it as `legacy-src/components/MarkdownContent` (server-component compatible: no hooks, no browser APIs).

- [ ] **Step 1: Install deps in BOTH apps**

```bash
cd /Users/olga/PycharmProjects/myhive-travel-app/myhive-react-app && npm install react-markdown@^9.0.1 remark-gfm@^4.0.0
cd /Users/olga/PycharmProjects/myhive-travel-app/myhive-next && npm install react-markdown@^9.0.1 remark-gfm@^4.0.0
```

- [ ] **Step 2: Write the failing CRA tests** (`src/components/MarkdownContent.test.js`)

```jsx
import {render, screen} from '@testing-library/react';
import MarkdownContent from './MarkdownContent';

test('renders markdown headings', () => {
    render(<MarkdownContent>{'## Getting there\n\nSome text.'}</MarkdownContent>);
    expect(screen.getByRole('heading', {level: 2, name: 'Getting there'})).toBeInTheDocument();
});

test('renders internal links for cross-page SEO linking', () => {
    render(<MarkdownContent>{'See the [Prague guide](/destination/prague).'}</MarkdownContent>);
    expect(screen.getByRole('link', {name: 'Prague guide'})).toHaveAttribute('href', '/destination/prague');
});

test('renders GFM tables', () => {
    render(<MarkdownContent>{'| City | Price |\n| --- | --- |\n| Prague | €50 |'}</MarkdownContent>);
    expect(screen.getByRole('table')).toBeInTheDocument();
});

test('does not render raw HTML from content', () => {
    const {container} = render(<MarkdownContent>{'before <img src=x onerror=alert(1)> after'}</MarkdownContent>);
    expect(container.querySelector('img')).toBeNull();
});

test('neutralizes javascript: URLs', () => {
    const {container} = render(<MarkdownContent>{'[click](javascript:alert(1))'}</MarkdownContent>);
    const link = container.querySelector('a');
    expect(link).not.toBeNull();
    expect(link.getAttribute('href') || '').not.toMatch(/javascript:/i);
});

test('splits plain paragraphs like the legacy renderer (existing posts unchanged)', () => {
    const {container} = render(<MarkdownContent>{'First paragraph.\n\nSecond paragraph.'}</MarkdownContent>);
    expect(container.querySelectorAll('p')).toHaveLength(2);
});
```

- [ ] **Step 3: Run to verify FAIL** — `cd myhive-react-app && npm test -- --watchAll=false MarkdownContent` → module not found.

- [ ] **Step 4: Implement `src/components/MarkdownContent.js`**

```jsx
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

// Single markdown renderer for blog content — consumed by the Next SSR blog
// page (via the legacy-src sync) and by the admin editor preview, so what the
// editor previews is exactly what production serves. Server-component safe:
// no hooks, no browser APIs. Raw HTML in content is NOT rendered as HTML
// (react-markdown ignores html nodes without rehype-raw) and URLs go through
// react-markdown's default sanitizer (http/https/mailto/tel + relative only).
export default function MarkdownContent({children}) {
    return <ReactMarkdown remarkPlugins={[remarkGfm]}>{children || ''}</ReactMarkdown>;
}
```

- [ ] **Step 5: Run tests; fix Jest ESM as needed**

`npm test -- --watchAll=false MarkdownContent`. react-scripts 5 will likely fail with `SyntaxError: Cannot use import statement outside a module` (react-markdown is pure ESM). Add to `myhive-react-app/package.json` (CRA supports this key):

```json
  "jest": {
    "transformIgnorePatterns": [
      "node_modules/(?!(react-markdown|remark-.*|rehype-.*|micromark.*|mdast-.*|unist-.*|unified|bail|is-plain-obj|trough|vfile.*|hast-.*|property-information|html-url-attributes|space-separated-tokens|comma-separated-tokens|devlop|estree-util-is-identifier-name|ccount|escape-string-regexp|markdown-table|trim-lines|decode-named-character-reference|character-entities.*|longest-streak|zwitch)/)"
    ]
  }
```

If Jest still names another untransformed ESM package, append it to the group. Iterate until: all 6 tests PASS.

- [ ] **Step 6: Legacy blog page parity.** In `src/pages/BlogPostPage.js`, replace the `content.split('\n')` → `<p>` mapping with `<MarkdownContent>{post.content}</MarkdownContent>` (import it). Run the full CRA suite: `npm test -- --watchAll=false` → all green (fix any snapshot/test that asserted the old `<p>`-split markup, keeping assertions equivalent).

- [ ] **Step 7: Next blog page.** `cd myhive-next && npm run sync-legacy`. In `app/(public)/blog/[slug]/page.tsx`:
- `import MarkdownContent from '../../../../legacy-src/components/MarkdownContent';`
- Delete the `paragraphs` computation and replace `{paragraphs.map(...)}` with `<MarkdownContent>{post.content}</MarkdownContent>`.
- In `summarize()`, strip markdown syntax so meta descriptions stay clean — final form:

```ts
function summarize(post: BlogPost) {
  if (post.excerpt) return post.excerpt;
  const text = (post.content || '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // keep link text, drop URL
    .replace(/[#*_>|`]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  return text.length > 155 ? `${text.slice(0, 152).trimEnd()}...` : text;
}
```

- Update the file's header comment (it currently documents the plain-text `<p>` split).

- [ ] **Step 8: Build** — `npm run build` → succeeds.
- [ ] **Step 9: Commit**

```bash
cd /Users/olga/PycharmProjects/myhive-travel-app
git add myhive-react-app/package.json myhive-react-app/package-lock.json myhive-react-app/src/components/MarkdownContent.js myhive-react-app/src/components/MarkdownContent.test.js myhive-react-app/src/pages/BlogPostPage.js myhive-next/package.json myhive-next/package-lock.json 'myhive-next/app/(public)/blog/[slug]/page.tsx'
git commit -m "feat(blog): GFM markdown rendering, one sanitized renderer shared by SSR page and admin"
```

---

### Task 9: Admin — markdown preview + seoIndexable switches

**Files:**
- Modify: `myhive-react-app/src/pages/AdminBlog.js`
- Modify: `myhive-react-app/src/pages/AdminActivities.js`
- Modify: `myhive-react-app/src/pages/AdminPackages.js`
- Modify: `myhive-react-app/src/pages/AdminDestinations.js`

**Interfaces:**
- Consumes: `MarkdownContent` (Task 8); backend admin CRUD already accepts `seoIndexable` (backend plan).
- The switch label everywhere: `Indexable by Google (SEO-ready content)`.

- [ ] **Step 1: AdminBlog** —
1. `EMPTY_FORM` gains `seoIndexable: false`; `mapItemToForm` gains `seoIndexable: !!post.seoIndexable`.
2. After the content `<Form.Control as="textarea" ...>` group, add a live preview:

```jsx
{form.content && (
    <div className="border rounded p-3 mb-3">
        <div className="text-secondary small mb-2">Preview (rendered exactly as on the site)</div>
        <MarkdownContent>{form.content}</MarkdownContent>
    </div>
)}
```

(import `MarkdownContent from '../components/MarkdownContent'`). Update the content field's placeholder to `"Markdown supported: ## headings, [links](/destination/prague), lists, tables. Blank line = new paragraph."`
3. Add the switch (copy of the AdminActivities `featured` pattern):

```jsx
<Form.Group className="mb-3">
    <Form.Check
        type="switch"
        id="blog-seo-indexable"
        label="Indexable by Google (SEO-ready content)"
        className="text-white"
        checked={!!form.seoIndexable}
        onChange={e => updateField('seoIndexable', e.target.checked)}
    />
</Form.Group>
```

- [ ] **Step 2: AdminActivities / AdminPackages / AdminDestinations** — in each: add `seoIndexable: false` to the empty-form constant, `seoIndexable: !!item.seoIndexable` to the item→form mapping, and the same `Form.Check type="switch"` block (unique `id` per page: `activity-seo-indexable`, `package-seo-indexable`, `destination-seo-indexable`), placed next to the existing `featured` switch in AdminActivities and in the equivalent form-bottom position in the other two. Follow each file's existing form-state update style (`updateField(...)` vs `setForm({...form, ...})`).

- [ ] **Step 3: Verify** — `cd myhive-react-app && npm test -- --watchAll=false` (all green) and `npx eslint src/pages/AdminBlog.js src/pages/AdminActivities.js src/pages/AdminPackages.js src/pages/AdminDestinations.js` if the repo lints; otherwise `npm start` briefly and eyeball one admin modal if a dev backend is available — else defer to the final task's manual pass.

- [ ] **Step 4: Commit** — `git add myhive-react-app/src/pages/Admin*.js && git commit -m "feat(admin): markdown preview for posts; seoIndexable switch on all four catalogs"`

---

### Task 10: Add-to-trip deep link (Next CTAs → CRA dispatch)

**Files:**
- Create: `myhive-react-app/src/hooks/useTripDeepLink.js`
- Test: create `myhive-react-app/src/hooks/useTripDeepLink.test.js`
- Modify: `myhive-react-app/src/pages/DestinationPage.js`
- Modify: `myhive-next/app/(public)/destination/[slug]/activity/[aslug]/page.tsx` (CTA ~line 182)
- Modify: `myhive-next/app/(public)/destination/[slug]/package/[pslug]/page.tsx` (CTA ~line 148)

**Interfaces:**
- Consumes: `useTrip()` (`{state, dispatch}`), `api.getActivityBySlug/getPackageBySlug` from `src/services/api`, reducer actions `ADD_TO_TRIP {activity}` / `ADD_PACKAGE_TO_TRIP {pkg}` — dispatched NON-silent, exactly like the legacy detail-page buttons (first item opens the setup modal, later items the builder modal — both already-tested states on this page).
- Produces: URL contract `?tab=trip-builder&add=<activity-slug>` / `?tab=trip-builder&addPackage=<package-slug>`; the param is stripped (history replace) after dispatch so refresh/back cannot re-add.

- [ ] **Step 1: Write the failing test** (`src/hooks/useTripDeepLink.test.js`)

```jsx
import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter, useLocation} from 'react-router-dom';
import {TripProvider, useTrip} from '../context/TripContext';
import useTripDeepLink from './useTripDeepLink';
import api from '../services/api';

jest.mock('../services/api');

function Probe() {
    useTripDeepLink();
    const {state} = useTrip();
    const location = useLocation();
    return (
        <>
            <div data-testid="items">{state.tripItems.map(i => i.id).join(',')}</div>
            <div data-testid="search">{location.search}</div>
        </>
    );
}

function renderAt(url) {
    return render(
        <TripProvider>
            <MemoryRouter initialEntries={[url]}>
                <Probe/>
            </MemoryRouter>
        </TripProvider>
    );
}

beforeEach(() => {
    localStorage.clear();
    jest.resetAllMocks();
});

test('adds the activity from ?add= and strips the param', async () => {
    api.getActivityBySlug.mockResolvedValue({id: 'a1', name: 'Karting', price: 50, slug: 'karting'});
    renderAt('/destination/prague?tab=trip-builder&add=karting');
    await waitFor(() => expect(screen.getByTestId('items')).toHaveTextContent('a1'));
    expect(api.getActivityBySlug).toHaveBeenCalledWith('karting');
    expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder');
});

test('adds the package from ?addPackage= and strips the param', async () => {
    api.getPackageBySlug.mockResolvedValue({
        id: 'p1', name: 'Full Weekend', discountPct: 20, destinationSlug: 'prague',
        activities: [{activityId: 'a1', name: 'Karting', price: 50, imageUrl: '', duration: 60}],
    });
    renderAt('/destination/prague?tab=trip-builder&addPackage=full-weekend');
    await waitFor(() => expect(screen.getByTestId('items')).toHaveTextContent('a1'));
    expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder');
});

test('does nothing without the params', () => {
    renderAt('/destination/prague?tab=trip-builder');
    expect(api.getActivityBySlug).not.toHaveBeenCalled();
    expect(screen.getByTestId('items').textContent).toBe('');
});

test('a fetch failure still strips the param', async () => {
    api.getActivityBySlug.mockRejectedValue(new Error('backend down'));
    renderAt('/destination/prague?tab=trip-builder&add=karting');
    await waitFor(() => expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder'));
    expect(screen.getByTestId('items').textContent).toBe('');
});
```

(If `TripProvider` requires being inside the router, swap the nesting — `MemoryRouter` outermost — and keep the Probe unchanged.)

- [ ] **Step 2: Run, verify FAIL** — `npm test -- --watchAll=false useTripDeepLink` → module not found.

- [ ] **Step 3: Implement `src/hooks/useTripDeepLink.js`**

```jsx
import {useEffect, useRef} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';

// Consumes ?add=<activity-slug> / ?addPackage=<package-slug> planted by the
// SSR detail pages' "Add to trip" CTAs: fetches the record and dispatches the
// exact action the in-SPA buttons dispatch (non-silent — modal behavior stays
// identical to a legacy button click), then strips the param via a history
// replace so refresh/back cannot re-add.
export default function useTripDeepLink() {
    const location = useLocation();
    const navigate = useNavigate();
    const {dispatch} = useTrip();
    const handledRef = useRef(false);

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const addSlug = params.get('add');
        const addPackageSlug = params.get('addPackage');
        if (handledRef.current || (!addSlug && !addPackageSlug)) {
            return;
        }
        handledRef.current = true;

        let cancelled = false;
        const strip = () => {
            const next = new URLSearchParams(location.search);
            next.delete('add');
            next.delete('addPackage');
            navigate({pathname: location.pathname, search: next.toString()}, {replace: true});
        };

        (async () => {
            try {
                if (addSlug) {
                    const activity = await api.getActivityBySlug(addSlug);
                    if (!cancelled && activity) {
                        dispatch({type: 'ADD_TO_TRIP', activity});
                    }
                } else {
                    const pkg = await api.getPackageBySlug(addPackageSlug);
                    if (!cancelled && pkg) {
                        dispatch({type: 'ADD_PACKAGE_TO_TRIP', pkg});
                    }
                }
            } catch (e) {
                // Nothing to add (backend hiccup) — still strip below so the
                // URL doesn't keep a dead param.
            } finally {
                if (!cancelled) {
                    strip();
                }
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [location, navigate, dispatch]);
}
```

- [ ] **Step 4: Run, verify PASS** — `npm test -- --watchAll=false useTripDeepLink` → 4 passing.

- [ ] **Step 5: Wire into `DestinationPage.js`** — add `import useTripDeepLink from '../hooks/useTripDeepLink';` and call `useTripDeepLink();` right after the existing `useNavigate()` line. Run the full CRA suite: `npm test -- --watchAll=false` → green.

- [ ] **Step 6: Next CTAs carry the payload.** Activity page (~line 182): `href={`/destination/${slug}?tab=trip-builder&add=${activity.slug}`}`. Package page (~line 148): `href={`/destination/${slug}?tab=trip-builder&addPackage=${pkg.slug}`}`.

- [ ] **Step 7: Sync + build** — `cd myhive-next && npm run build` (prebuild re-syncs legacy-src incl. the new hook) → succeeds.

- [ ] **Step 8: Commit**

```bash
cd /Users/olga/PycharmProjects/myhive-travel-app
git add myhive-react-app/src/hooks/useTripDeepLink.js myhive-react-app/src/hooks/useTripDeepLink.test.js myhive-react-app/src/pages/DestinationPage.js 'myhive-next/app/(public)/destination/[slug]/activity/[aslug]/page.tsx' 'myhive-next/app/(public)/destination/[slug]/package/[pslug]/page.tsx'
git commit -m "fix(trip): Add-to-trip CTAs actually add — deep-link param dispatched by the SPA builder"
```

---

### Task 11: Cutover checklist corrections + repo-root `.node-version`

**Files:**
- Modify: `docs/superpowers/plans/2026-07-23-cutover-checklist.md`
- Create: `/Users/olga/PycharmProjects/myhive-travel-app/.node-version` (content: `20.20.0` — must match `myhive-next/.node-version`; Render reads it from the service root, which is now the repo root)

- [ ] **Step 1: Fix the Render service definition (item A1).** Replace lines "Root directory… / Build… / Node…" with:

```markdown
1. **Render: новый web service для `myhive-next`**
   - Root directory: **пустой (корень репозитория)** — `prebuild` копирует
     `myhive-react-app/src` в `myhive-next/legacy-src`, а Render не даёт
     сервису файлы вне root directory ни на билде, ни в рантайме
   - Build: `cd myhive-next && npm ci && npm run build`
   - Start: `cd myhive-next && npm run start`
   - Node: из `.node-version` **в корне репо** (20.20.0, добавлен в этой ветке)
```

- [ ] **Step 2: Add the internal token env** to the A1 env list:

```markdown
     - `INTERNAL_API_TOKEN=<длинный случайный секрет>` — тот же секрет ставится
       на Render-бэкенде; SSR-трафик идёт с одного egress-IP и без него упирается
       в лимит 100 req/min (шлётся как `X-Internal-Token`)
```

and to item 2 (backend deploy): note that the backend needs branch `feat/seo-gate-internal-token` merged (seoIndexable columns самодобавляются: prod `ddl-auto=update`) and env `INTERNAL_API_TOKEN`.

- [ ] **Step 3: Correct the Cloudflare claim (B5).** Replace the "полностью подменяет" wording with:

```markdown
5. **Cloudflare: отключить managed-блок «content signals»** — блок **добавляется
   сверху** к нашему robots.txt (проверено 2026-07-23: наши правила и Sitemap
   видны ниже блока — это НЕ полный подмен и не блокер индексации). Отключаем,
   потому что блок навязывает свои правила (`Allow: /`, запреты AI-ботам),
   которые мы не выбирали.
```

- [ ] **Step 4: Update cutover semantics (C9, C10).** C9 gets a second sentence: `ALLOW_INDEXING=true` открывает только записи с `seoIndexable=true` (глобальный аварийный выключатель + пер-записный гейт; новые записи по умолчанию не индексируются). C10's robots line becomes `Disallow /admin /vote /payment (без завершающего слэша — покрывает и голый /admin)`. Add C-item: `проставить seoIndexable=true в админке только на редакционно готовые записи (уникальные 100–200-слов описания, нейтральные slug/title)`.

- [ ] **Step 5: Part 2 status table touch-ups.** Row 3 (robots): mention bare prefixes. Row 8 (OG): note per-page OG now always includes og:image/og:type via `pageMetadata` (fixed shallow-merge loss; legal pages included). Add row/note: блог рендерит Markdown (заголовки, внутренние ссылки — требование перелинковки v3 выполнимо контентом).

- [ ] **Step 6: Create `.node-version`** at repo root containing `20.20.0`.

- [ ] **Step 7: Commit**

```bash
cd /Users/olga/PycharmProjects/myhive-travel-app
git add docs/superpowers/plans/2026-07-23-cutover-checklist.md .node-version
git commit -m "docs(migration): checklist — Render root-dir fix, INTERNAL_API_TOKEN, Cloudflare wording, seoIndexable rollout"
```

---

### Task 12: Full verification

**Files:** none (verification only). REQUIRED SUB-SKILL for the finisher: superpowers:verification-before-completion.

- [ ] **Step 1: CRA suite** — `cd myhive-react-app && npm test -- --watchAll=false` → all green.
- [ ] **Step 2: Clean Next build** — `cd myhive-next && rm -rf legacy-src public .next && npm run build` → succeeds (proves sync-legacy + bundling of MarkdownContent/hook).
- [ ] **Step 3: Runtime pass** (needs the docker/local backend on :8080; if it has the seoIndexable columns, flag one record true to see the sitemap include it):
  - `npm run start` (background), then:
  - `node scripts/smoke.mjs http://localhost:3000` → passes.
  - `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/totally-unknown http://localhost:3000/destinations` → `404` twice.
  - `curl -s 'http://localhost:3000/destination/prague?tab=trip-builder' | grep -c 'app-container'` → `0`.
  - `curl -s http://localhost:3000/destination/prague | grep -c 'app-container'` → `1`.
  - View-source a package page: prices show real euros, no `NaN`.
  - Browser: open an activity, click "Add to trip" → lands in the builder WITH the activity added, single header/footer, URL param stripped.
  - Blog post with markdown content: headings/links render; `curl` the page and confirm `<h2>` in source.
- [ ] **Step 4: Report** — summarize results honestly (any check that couldn't run — e.g. backend without flag columns — gets listed as deferred to preview smoke, per the checklist).
