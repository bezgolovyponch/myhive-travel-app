'use client';

// The provider half of LegacyChrome, without the chrome. Pages that bring their
// own header, footer and stylesheet — the marketing landings — still need the
// SPA's state: one TripProvider shared by a landing's cart badge, its add
// buttons, the setup modal and the cart dropdown, so all four agree and the cart
// survives the trip to /destination/<slug>.
//
// Deliberately NO bootstrap / global.css import here. Those belong to the
// chrome (LegacyChrome imports them), and a landing that loaded them would have
// its mockup restyled by main's resets, typography and .btn rules.
//
// LegacyRouter is included because the components mounted inside — the cart
// dropdown above all — call useNavigate. Its push is a full document load,
// which is the correct way to leave a landing anyway.
import { HelmetProvider } from 'react-helmet-async';
import { PageHeadEnabledContext } from '../../legacy-src/components/PageHead';
import { CatalogProvider } from '../../legacy-src/context/CatalogContext';
import { TripProvider } from '../../legacy-src/context/TripContext';
import { DestinationModalProvider } from '../../legacy-src/context/DestinationModalContext';
import AttributionCapture from '../../legacy-src/components/AttributionCapture';
import LegacyRouter from './LegacyRouter';

export interface LegacyDestination {
  id: string;
  slug: string;
  name: string;
}

export default function LegacyProviders({
  children,
  destinations,
}: {
  children: React.ReactNode;
  destinations: LegacyDestination[];
}) {
  return (
    // Next's per-route `metadata` owns the head, so the CRA pages' PageHead
    // blocks stay silent — see LegacyChrome for why HelmetProvider is still
    // mounted underneath it.
    <PageHeadEnabledContext.Provider value={false}>
      <HelmetProvider>
        <LegacyRouter>
          <CatalogProvider initialDestinations={destinations}>
            <TripProvider>
              <DestinationModalProvider>
                <AttributionCapture />
                {children}
              </DestinationModalProvider>
            </TripProvider>
          </CatalogProvider>
        </LegacyRouter>
      </HelmetProvider>
    </PageHeadEnabledContext.Provider>
  );
}
