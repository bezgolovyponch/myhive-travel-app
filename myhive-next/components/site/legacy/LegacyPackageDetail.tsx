'use client';

// Client boundary for the canonical CRA package detail page. RouteMatch
// re-declares the CRA route so useParams() resolves destSlug/slug; the package is
// supplied by the server so the record is in the initial HTML.
import { useLocale } from '../../../legacy-src/i18n';
import { DESTINATION_HUBS, routePath } from '../../../legacy-src/i18n/routes';
import PackageDetailPage from '../../../legacy-src/pages/PackageDetailPage';
import RouteMatch from './RouteMatch';

export default function LegacyPackageDetail({
  pkg,
  destinationSlug,
}: {
  pkg: unknown;
  destinationSlug: string;
}) {
  const locale = useLocale();
  const hub = DESTINATION_HUBS[destinationSlug];
  const pattern = routePath(
    hub ? `${hub}/packages/:slug` : '/destination/:destSlug/package/:slug',
    locale
  );
  return (
    <RouteMatch pattern={pattern}>
      <PackageDetailPage pkg={pkg} destinationSlug={destinationSlug} />
    </RouteMatch>
  );
}
