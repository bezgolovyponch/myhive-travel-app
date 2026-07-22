// Server-renderable activity card mirroring legacy-src/components/ActivityCard.js
// markup/classes. The whole card is a link to the activity page; interactive
// Add-to-trip / preview live in the SPA surfaces (Ф4 ports them as islands).
import Link from 'next/link';
import type { Activity } from '../../lib/api';
import '../../legacy-src/components/ActivityCard.css';

const DEFAULT_ACTIVITY_IMAGE =
  'https://images.unsplash.com/photo-1541849546-216549ae216d?w=800&h=600&fit=crop';

function capitalizeFirst(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export default function ActivityCardStatic({ activity }: { activity: Activity }) {
  const destSlug = activity.destinationSlug || activity.destinationId;
  const href = `/destination/${destSlug}/activity/${activity.slug || activity.id}`;
  const first = activity.categories?.[0];
  const primaryCategory = typeof first === 'string' ? first : first?.name;
  const hasGroupMin = activity.minPrice != null && activity.minPrice > 0;
  const formattedPrice = `${hasGroupMin ? 'from ' : ''}€${Math.round(activity.price)} / person`;

  return (
    <Link href={href} className="card activity-card" aria-label={`View ${activity.name}`}>
      <img
        src={activity.imageUrl || DEFAULT_ACTIVITY_IMAGE}
        alt={activity.name}
        className="activity-image"
        loading="lazy"
      />
      <div className="activity-content">
        <span className="activity-category">
          {primaryCategory ? capitalizeFirst(primaryCategory) : 'Activity'}
        </span>
        <h3 className="activity-title">{activity.name}</h3>
        <div className="activity-footer">
          <span className="activity-price">{formattedPrice}</span>
        </div>
      </div>
    </Link>
  );
}
