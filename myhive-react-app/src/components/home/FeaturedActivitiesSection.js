import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import api from '../../services/api';
import {useCatalog} from '../../context/CatalogContext';
import {useTrip} from '../../context/TripContext';
import {getDefaultDestination} from '../../utils/defaultDestination';
import {pushEvent} from '../../utils/analytics';
import ActivityCard from '../ActivityCard';
import {useT} from '../../i18n';
import './FeaturedActivitiesSection.css';

const MAX_FEATURED = 12;

// `injected` lets a server renderer supply the grid so it reaches the initial
// HTML — the fetch below runs in an effect, which a crawler never sees. Omitted
// in the SPA, where the effect stays the only source.
function FeaturedActivitiesSection({activities: injected}) {
    const t = useT('home');
    const {state: catalog} = useCatalog();
    const {state: trip} = useTrip();
    const [activities, setActivities] = useState(injected ?? []);

    useEffect(() => {
        if (injected) {
            return undefined;
        }
        // The fallback must be scoped to the default destination, so wait for
        // the catalog instead of fetching on mount.
        if (catalog.loading) {
            return undefined;
        }
        const defaultDestinationId = getDefaultDestination(catalog.destinations)?.id ?? null;
        let cancelled = false;
        api.getFeaturedActivities()
            // Nothing curated as featured (or the fetch failed): fall back to the
            // default destination's activities so the homepage grid is never
            // empty — and never shows another destination's activities.
            .catch(() => null)
            .then(featured => (featured && featured.length > 0
                ? featured
                : api.getActivities(defaultDestinationId)))
            .then(data => {
                if (!cancelled) {
                    setActivities((data || []).slice(0, MAX_FEATURED));
                }
            })
            .catch(() => {
                // Both fetches failed; keep the section hidden.
            });
        return () => {
            cancelled = true;
        };
    }, [injected, catalog.loading, catalog.destinations]);

    if (activities.length === 0) {
        return null;
    }

    const mainDestination = getDefaultDestination(catalog.destinations);

    return (
        <section className="featured-activities" id="activities">
            <h2 className="section-title">{t('featured.heading')}</h2>
            <p className="section-subtitle">
                {t('featured.subtitle')}
            </p>
            <div className="featured-activities-grid">
                {activities.map(activity => (
                    <ActivityCard
                        key={activity.id}
                        activity={activity}
                        isAdded={trip.tripItems.some(item => item.id === activity.id)}
                    />
                ))}
            </div>
            {mainDestination && (
                <Link
                    to={`/destination/${mainDestination.slug}`}
                    className="btn btn--lg featured-activities-cta"
                    onClick={() => pushEvent('cta_click', {cta_label: 'View All Activities', block: 'activities'})}
                >
                    {t('featured.viewAllCta')}
                </Link>
            )}
        </section>
    );
}

export default FeaturedActivitiesSection;
