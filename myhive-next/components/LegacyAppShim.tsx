'use client';

import dynamic from 'next/dynamic';

// The legacy tree touches window/localStorage at module scope (e.g.
// legacy-src/context/AuthContext.js builds its OIDC config from window at
// import time), so it must never be evaluated during server render. In Next 15
// `ssr: false` is only honored inside a Client Component — that is the entire
// reason this shim exists.
// A failed chunk load (stale cached HTML requesting an old hashed chunk after
// a redeploy) must not collapse the site to Next's generic error page — same
// defense legacy App.js applies to its AdminApp chunk.
const LegacyApp = dynamic(
  () =>
    import('../legacy-src/App').catch(() => ({
      default: () => (
        <div style={{ padding: '4rem', textAlign: 'center' }}>
          This page failed to load — a new version may have just been deployed.
          Please refresh the page.
        </div>
      ),
    })),
  {
    ssr: false,
    loading: () => (
      <div style={{ padding: '4rem', textAlign: 'center' }}>Loading…</div>
    ),
  }
);

export default function LegacyAppShim() {
  return <LegacyApp />;
}
