'use client';

// Client boundary for the canonical CRA activity detail page. RouteMatch
// re-declares the CRA route so useParams() resolves destinationSlug/slug; the
// activity is supplied by the server so the record is in the initial HTML.
import { useLocale } from '../../../legacy-src/i18n';
import { DESTINATION_HUBS, routePath } from '../../../legacy-src/i18n/routes';
import ActivityDetailPage from '../../../legacy-src/pages/ActivityDetailPage';
import RouteMatch from './RouteMatch';

export default function LegacyActivityDetail({
  activity,
  destinationSlug,
}: {
  activity: unknown;
  destinationSlug: string;
}) {
  const locale = useLocale();
  const hub = DESTINATION_HUBS[destinationSlug];
  const pattern = routePath(
    hub ? `${hub}/activities/:slug` : '/destination/:destinationSlug/activity/:slug',
    locale
  );
  return (
    <RouteMatch pattern={pattern}>
      <ActivityDetailPage activity={activity} destinationSlug={destinationSlug} />
    </RouteMatch>
  );
}
