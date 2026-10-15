import type { NextConfig } from 'next';
import createNextIntlPlugin from 'next-intl/plugin';
import { DESTINATION_HUBS, SUPPORTED_LOCALES, localizePath } from './legacy-src/i18n/routes';

// Server-only backend base, INCLUDING its path prefix: local Spring serves at
// root (http://localhost:8080), prod behind the container that strips /api
// (https://myhive-backend.onrender.com/api). The browser only ever talks
// same-origin /api/* — no public env var carries the backend URL.
// Read at BUILD time: the rewrite destination is serialized into the build
// output, so changing BACKEND_URL requires a rebuild, not just a restart.
// The localhost fallback exists for `next dev` only: a production build that
// forgot the env var would otherwise ship with every /api call proxying to a
// localhost where nothing listens — a fully broken deploy that builds green.
if (!process.env.BACKEND_URL && process.env.NODE_ENV === 'production') {
  throw new Error(
    'BACKEND_URL is not set. Production builds refuse the localhost fallback — ' +
      'set it in the deploy environment (e.g. https://myhive-backend.onrender.com/api).'
  );
}
// Strip trailing slashes: `${url}/:path*` with a trailing slash produces
// double-slash paths that Spring's path matching 404s.
const BACKEND_URL = (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
console.log(`[next.config] /api rewrite target: ${BACKEND_URL}`);

// 308s from the pre-2026-10 URL scheme (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Rules are generated from the same table the links use, scoped to hub
// destinations only, so cities on /destination/{slug} are never touched.
function legacyUrlRedirects() {
  const rules: { source: string; destination: string; permanent: true }[] = [];
  const rule = (source: string, destination: string) => rules.push({ source, destination, permanent: true });
  for (const [slug, hub] of Object.entries(DESTINATION_HUBS) as [string, string][]) {
    for (const locale of SUPPORTED_LOCALES as string[]) {
      rule(localizePath(`/destination/${slug}`, locale), localizePath(hub, locale));
      rule(localizePath(`/destination/${slug}/activity/:slug`, locale), localizePath(`${hub}/activities/:slug`, locale));
      rule(localizePath(`/destination/${slug}/package/:slug`, locale), localizePath(`${hub}/packages/:slug`, locale));
    }
  }
  for (const locale of SUPPORTED_LOCALES as string[]) {
    rule(localizePath('/blog', locale), localizePath('/ideas', locale));
    // The one kept post; its slug is renamed in the admin on deploy day. No
    // generic /blog/:slug rule on purpose: redirects run before middleware and
    // would swallow the 410s (lib/gone.ts).
    rule(localizePath('/blog/how-to-plan-a-stress-free-group-trip', locale), localizePath('/ideas/how-to-plan-a-stag-do', locale));
  }
  return rules;
}

const nextConfig: NextConfig = {
  async redirects() {
    return legacyUrlRedirects();
  },
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

// Binds i18n/request.ts (message loading) into the build; routing itself is
// middleware.ts + the app/[locale] segment.
const withNextIntl = createNextIntlPlugin();

export default withNextIntl(nextConfig);
