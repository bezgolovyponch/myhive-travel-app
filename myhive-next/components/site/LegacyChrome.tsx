'use client';

// The canonical CRA chrome, rendered for the SSR public pages. Mirrors
// legacy-src/components/Layout.js minus its <Routes> (Next owns routing here)
// and minus ScrollToTop (there are no client-side route changes to scroll for).
//
// 'use client' means "hydrates", not "skipped on the server": everything below
// still server-renders into the HTML crawlers get. The exception is anything
// gated behind useEffect, which is why the catalog is seeded from the server —
// see CatalogProvider's initialDestinations.
//
// One provider tree wraps Header, the page and Footer together. Separate islands
// would each get their own TripProvider, so adding to the cart from the page
// would not update the header badge.
//
// AuthContext is deliberately absent: it builds its OIDC config from window at
// module scope, which is the sole reason LegacyAppShim needs ssr:false. Keeping
// it out of this import graph is what makes server rendering possible at all.
import { HelmetProvider } from 'react-helmet-async';
import { PageHeadEnabledContext } from '../../legacy-src/components/PageHead';
import { CatalogProvider } from '../../legacy-src/context/CatalogContext';
import { TripProvider } from '../../legacy-src/context/TripContext';
import {
  DestinationModalProvider,
  useDestinationModal,
} from '../../legacy-src/context/DestinationModalContext';
import AttributionCapture from '../../legacy-src/components/AttributionCapture';
import Header from '../../legacy-src/components/Header';
import Footer from '../../legacy-src/components/Footer';
import WhatsAppWidget from '../../legacy-src/components/WhatsAppWidget';
import AppModal from '../../legacy-src/components/AppModal';
import { useT } from '../../legacy-src/i18n';
import { useTripLeadSync } from '../../legacy-src/hooks/useTripLeadSync';
import LegacyRouter from './LegacyRouter';
import 'bootstrap/dist/css/bootstrap.min.css';
import '../../legacy-src/styles/global.css';

export interface LegacyDestination {
  id: string;
  slug: string;
  name: string;
}

// Hooks that must run inside the providers, so they cannot live in the root.
function ChromeInner({ children }: { children: React.ReactNode }) {
  useTripLeadSync();
  const t = useT('chrome');
  const { state, dispatch } = useDestinationModal();
  const closeDestinationModal = () => dispatch({ type: 'CLOSE_DESTINATION_MODAL' });

  return (
    <div className="app-container">
      <Header />
      <main>{children}</main>
      <Footer />
      <WhatsAppWidget />
      <AppModal
        isOpen={state.destinationModalOpen}
        onClose={closeDestinationModal}
        title={t('comingSoonTitle')}
        // Layout.js omits this; AppModal renders `{footer && ...}`, so null is
        // runtime-identical and satisfies the inferred prop type.
        footer={null}
      >
        <div className="empty-trip-state">
          <h3>
            {t('comingSoonHeading', {
              name: state.selectedDestination?.name || t('fallbackDestination'),
            })}
          </h3>
          <p>{t('comingSoonBody')}</p>
          <button className="btn btn--primary" onClick={closeDestinationModal}>
            {t('gotIt')}
          </button>
        </div>
      </AppModal>
    </div>
  );
}

export default function LegacyChrome({
  children,
  destinations,
}: {
  children: React.ReactNode;
  destinations: LegacyDestination[];
}) {
  return (
    // Next's per-route `metadata` owns the head here, so the CRA pages' PageHead
    // blocks stay silent — otherwise react-helmet-async 3's React19Dispatcher
    // renders a second title/description/canonical into the tree for React to
    // hoist. HelmetProvider is still mounted because the SPA components inside
    // this tree may reach for it.
    <PageHeadEnabledContext.Provider value={false}>
    <HelmetProvider>
      <LegacyRouter>
        <CatalogProvider initialDestinations={destinations}>
          <TripProvider>
            <DestinationModalProvider>
              <AttributionCapture />
              <ChromeInner>{children}</ChromeInner>
            </DestinationModalProvider>
          </TripProvider>
        </CatalogProvider>
      </LegacyRouter>
    </HelmetProvider>
    </PageHeadEnabledContext.Provider>
  );
}
