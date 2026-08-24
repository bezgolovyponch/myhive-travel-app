'use client';

// CRA components navigate with react-router (<Link to>, useNavigate). The SSR
// public pages have no <Routes> tree, so a real BrowserRouter would "navigate"
// to a blank screen — react-router would own history and find nothing to render.
// This bridges react-router navigation onto real browser navigation: <Link>s
// render correct hrefs and clicking one does a full page load, which is exactly
// what leaving an SSR page should do. Inside the SPA the very same components
// keep using the genuine BrowserRouter from legacy-src/App.
import { Router } from 'react-router-dom';
import type { To } from 'react-router-dom';
import { usePathname } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
// The legacy components think in locale-free URLs ('/destination/prague'), so
// the bridge strips the locale prefix from every location it feeds INTO the
// router and re-adds it to every href/navigation coming OUT. Route patterns,
// useParams and pathname-sniffing (Header breadcrumbs) all keep working on
// /de/... URLs without any per-component change.
import { useLocale } from '../../legacy-src/i18n';
import { localizeHref, splitLocale } from '../../legacy-src/i18n/routes';

function toHref(to: To): string {
  if (typeof to === 'string') return to;
  const { pathname = '', search = '', hash = '' } = to;
  return `${pathname}${search}${hash}`;
}

// CRA's tracked links push cta_click and navigate in the same tick. In the SPA
// that was safe — navigation kept the document alive — but here a cross-document
// load can destroy the queued event before GTM's asynchronous container has
// dispatched it. Yielding briefly lets the already-queued push drain first.
// Imperceptible to the user, and bounded so navigation can never hang on it.
// NOTE: this is a mitigation, not a guarantee. The airtight version is GTM's
// eventCallback on the cta_click itself, which needs the container's tag
// configuration to confirm — see the analytics follow-ups.
const ANALYTICS_FLUSH_MS = 250;

function leaveDocument(go: () => void) {
  setTimeout(go, ANALYTICS_FLUSH_MS);
}

type BridgeLocation = {
  pathname: string;
  search: string;
  hash: string;
  state: unknown;
  key: string;
};

export default function LegacyRouter({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const locale = useLocale();

  // The server has no window, so the first render knows only the pathname.
  // Deliberately not reading search/hash here: usePathname is prerender-safe
  // while useSearchParams would force this subtree out of the static HTML,
  // which is the one thing these pages exist to produce.
  const [location, setLocation] = useState<BridgeLocation>(() => ({
    pathname: splitLocale(pathname).pathname,
    search: '',
    hash: '',
    state: null,
    key: 'default',
  }));

  // Fill in search/hash after hydration. Running in an effect means React never
  // diffs it against the server markup, so this costs no hydration mismatch.
  // Consumers that need it (header breadcrumb tab label, AttributionCapture)
  // re-run on the update.
  useEffect(() => {
    const sync = () => {
      const { search, hash } = window.location;
      const p = splitLocale(window.location.pathname).pathname;
      setLocation((prev) =>
        prev.pathname === p && prev.search === search && prev.hash === hash
          ? prev
          : { pathname: p, search, hash, state: null, key: 'default' }
      );
    };
    sync();
    // Same-document pushes below (a ?tab= switch, say) create history entries
    // that Back/Forward move through without changing the pathname, so
    // usePathname never fires and this effect would not re-run. Without the
    // listener the URL would step back while the rendered tab stayed put.
    window.addEventListener('popstate', sync);
    return () => window.removeEventListener('popstate', sync);
  }, [pathname]);

  const navigator = useMemo(
    () => ({
      createHref: (to: To) => localizeHref(toHref(to), locale),
      go: (delta: number) => window.history.go(delta),

      push: (to: To) => {
        const url = new URL(localizeHref(toHref(to), locale), window.location.origin);
        // Same-document target: don't reload. scrollToHomeSection() calls
        // navigate('/') unconditionally before scrolling, so on '/' a reload
        // here would throw away the smooth scroll it was about to do.
        if (url.pathname === window.location.pathname) {
          window.history.pushState(null, '', url);
          setLocation({
            pathname: splitLocale(url.pathname).pathname,
            search: url.search,
            hash: url.hash,
            state: null,
            key: 'default',
          });
          return;
        }
        leaveDocument(() => window.location.assign(url));
      },

      replace: (to: To) => {
        const url = new URL(localizeHref(toHref(to), locale), window.location.origin);
        if (url.pathname === window.location.pathname) {
          window.history.replaceState(null, '', url);
          setLocation({
            pathname: splitLocale(url.pathname).pathname,
            search: url.search,
            hash: url.hash,
            state: null,
            key: 'default',
          });
          return;
        }
        leaveDocument(() => window.location.replace(url));
      },
    }),
    [locale]
  );

  return (
    <Router location={location} navigator={navigator}>
      {children}
    </Router>
  );
}
