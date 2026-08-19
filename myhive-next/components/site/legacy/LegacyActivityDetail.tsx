'use client';

// Client boundary for the canonical CRA activity detail page. RouteMatch
// re-declares the CRA route so useParams() resolves destinationSlug/slug; the
// activity is supplied by the server so the record is in the initial HTML.
import ActivityDetailPage from '../../../legacy-src/pages/ActivityDetailPage';
import RouteMatch from './RouteMatch';

export default function LegacyActivityDetail({ activity }: { activity: unknown }) {
  return (
    <RouteMatch pattern="/destination/:destinationSlug/activity/:slug">
      <ActivityDetailPage activity={activity} />
    </RouteMatch>
  );
}
