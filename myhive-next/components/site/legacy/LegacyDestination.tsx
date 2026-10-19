'use client';

// Client boundary for the canonical CRA destination catalog. RouteMatch
// re-declares the CRA route so useParams() resolves the slug; `initial` seeds
// destination, categories, page 0 of activities and packages so the catalog is in
// the initial HTML. Only the bare catalog URL reaches here; ?tab= and
// ?voteSession= go to the full SPA from the server page.
import { useLocale } from '../../../legacy-src/i18n';
import { DESTINATION_HUBS, routePath } from '../../../legacy-src/i18n/routes';
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

export default function LegacyDestination({
  initial,
  destinationSlug,
}: {
  initial: DestinationInitial;
  destinationSlug: string;
}) {
  const locale = useLocale();
  const hub = DESTINATION_HUBS[destinationSlug];
  // The bridge feeds the PUBLIC path (/jga-prag on DE), so the pattern must be
  // spelled in this locale's segments or <Routes> renders nothing.
  const pattern = routePath(hub ?? '/destination/:slug', locale);
  return (
    <RouteMatch pattern={pattern}>
      <DestinationPage initial={initial} destinationSlug={destinationSlug} />
    </RouteMatch>
  );
}
