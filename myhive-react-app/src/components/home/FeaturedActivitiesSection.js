import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import api from '../../services/api';
import {useCatalog} from '../../context/CatalogContext';
import {useTrip} from '../../context/TripContext';
import {getDefaultDestination} from '../../utils/defaultDestination';
import ActivityCard from '../ActivityCard';
import './FeaturedActivitiesSection.css';

const MAX_FEATURED = 12;

function FeaturedActivitiesSection() {
    const {state: catalog} = useCatalog();
    const {state: trip} = useTrip();
    const [activities, setActivities] = useState([]);

    useEffect(() => {
        let cancelled = false;
        api.getFeaturedActivities()
            .then(data => {
                if (!cancelled) {
                    setActivities((data || []).slice(0, MAX_FEATURED));
                }
            })
            .catch(() => {
                // The featured grid is optional on the homepage; keep it hidden on fetch failure.
            });
        return () => {
            cancelled = true;
        };
    }, []);

    if (activities.length === 0) {
        return null;
    }

    const mainDestination = getDefaultDestination(catalog.destinations);

    return (
        <section className="featured-activities" id="activities">
            <h2 className="section-title">70+ Activities. Something for Every Group.</h2>
            <p className="section-subtitle">
                From tank driving to strip clubs and spa — we've got every stag style covered.
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
                <Link to={`/destination/${mainDestination.slug}`} className="btn btn--primary btn--lg">
                    View All Activities
                </Link>
            )}
        </section>
    );
}

export default FeaturedActivitiesSection;
