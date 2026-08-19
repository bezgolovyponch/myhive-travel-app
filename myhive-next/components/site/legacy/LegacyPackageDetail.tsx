'use client';

// Client boundary for the canonical CRA package detail page. RouteMatch
// re-declares the CRA route so useParams() resolves destSlug/slug; the package is
// supplied by the server so the record is in the initial HTML.
import PackageDetailPage from '../../../legacy-src/pages/PackageDetailPage';
import RouteMatch from './RouteMatch';

export default function LegacyPackageDetail({ pkg }: { pkg: unknown }) {
  return (
    <RouteMatch pattern="/destination/:destSlug/package/:slug">
      <PackageDetailPage pkg={pkg} />
    </RouteMatch>
  );
}
