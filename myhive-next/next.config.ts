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
