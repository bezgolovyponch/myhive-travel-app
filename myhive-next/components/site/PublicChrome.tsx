// Shared chrome for the SSR public pages. A thin server shell: it fetches the
// destinations the CRA chrome needs (header breadcrumbs, the homepage catalog
// CTA) and hands them to LegacyChrome, which renders the canonical CRA
// Header/Footer/WhatsAppWidget.
//
// Seeding the catalog server-side is what keeps that markup in the initial HTML:
// CatalogContext otherwise loads in a useEffect, which no crawler runs.
//
// A component (not only a route-group layout) so pages with an SPA escape hatch
// can render chrome-less — the CRA tree mounts its own Header/Footer.
import { api } from '../../lib/api';
import LegacyChrome, { type LegacyDestination } from './LegacyChrome';

async function loadDestinations(): Promise<LegacyDestination[]> {
  try {
    return (await api.getDestinations()) ?? [];
  } catch {
    // Backend unreachable: render the chrome without breadcrumbs rather than
    // 500 the page. The client-side fetch never runs (the catalog is seeded),
    // so this degrades to the same empty state the SPA shows on a failed load.
    return [];
  }
}

export default async function PublicChrome({ children }: { children: React.ReactNode }) {
  const destinations = await loadDestinations();

  return <LegacyChrome destinations={destinations}>{children}</LegacyChrome>;
}
