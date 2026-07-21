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
