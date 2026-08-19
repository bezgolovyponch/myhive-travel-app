// Wraps only the legacy-SPA catch-all: these routes are client-only, so the
// no-JS notice lives here — the SSR public pages render content without JS.
export default function LegacyLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <noscript>You need to enable JavaScript to run this app.</noscript>
      {children}
    </>
  );
}
