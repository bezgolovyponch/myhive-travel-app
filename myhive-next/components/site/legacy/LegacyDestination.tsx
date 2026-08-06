'use client';

// Client boundary for the canonical CRA destination catalog. RouteMatch
// re-declares the CRA route so useParams() resolves the slug; `initial` seeds
// destination, categories, page 0 of activities and packages so the catalog is in
// the initial HTML. Only the bare /destination/:slug URL reaches here — ?tab= and
// ?voteSession= are handed to the full SPA by the server page.
import DestinationPage from '../../../legacy-src/pages/DestinationPage';
import RouteMatch from './RouteMatch';

export interface DestinationInitial {
  destination: unknown;
  categories: unknown[];
  activities: unknown[];
  packages: unknown[];
  totalElements: number;
  last: boolean;
}

export default function LegacyDestination({ initial }: { initial: DestinationInitial }) {
  return (
    <RouteMatch pattern="/destination/:slug">
      <DestinationPage initial={initial} />
    </RouteMatch>
  );
}
