'use client';

// The provider stack the CRA components need, without the chrome that normally
// carries it. LegacyChrome wraps this around a Header/Footer; the landings —
// which render their own header — wrap it around themselves so their cart is
// the real one: the same TripProvider, so the header badge, the cart panel and
// the trip builder all read one list, persisted in localStorage.
//
// Deliberately no global stylesheets here. LegacyChrome imports global.css and
// bootstrap; the landings must not, since global.css's *, html and body rules
// would repaint them. Components mounted outside the chrome bring their own CSS
// instead (see TripBuilderDropdown.js).
import { CatalogProvider } from '../../legacy-src/context/CatalogContext';
import { TripProvider } from '../../legacy-src/context/TripContext';
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
    // LegacyRouter is not optional scaffolding: useStartGroupVote (reached from
    // the cart panel) calls useNavigate, and the panel's Continue navigates to
    // the trip builder. The bridge turns both into real page loads.
    <LegacyRouter>
      <CatalogProvider initialDestinations={destinations}>
        <TripProvider>{children}</TripProvider>
      </CatalogProvider>
    </LegacyRouter>
  );
}
