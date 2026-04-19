import {useContext, useEffect, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPricePerPerson} from '../utils/format';
import './ActivityDetailPage.css';

function ActivityDetailPage() {
    const {destinationSlug, slug} = useParams();
    const navigate = useNavigate();
    const {state, dispatch} = useContext(AppContext);
    const [activity, setActivity] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        setLoading(true);
        api.getActivityBySlug(slug)
            .then(data => setActivity(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [slug]);

    if (loading) {
        return (
            <div className="activity-detail-page">
                <div className="activity-detail-loading">Loading...</div>
            </div>
        );
    }

    if (error || !activity) {
        return (
            <div className="activity-detail-page">
                <div className="activity-detail-loading">
                    <p>Activity not found.</p>
                    <button className="btn btn--primary" onClick={() => navigate(-1)}>Go Back</button>
                </div>
            </div>
        );
    }

    const imageUrl = activity.imageUrl || DEFAULT_ACTIVITY_IMAGE;
    const title = activity.name || activity.title;
    const formattedPrice = formatPricePerPerson(activity.price);
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;
    const category = primaryCategory ? capitalizeFirst(primaryCategory) : 'Activity';
    const isAdded = state.tripItems.some(item => item.id === activity.id);

    const handleAddToTrip = () => {
        dispatch({type: 'ADD_TO_TRIP', activity});
    };

    return (
        <div className="activity-detail-page">
            <Helmet>
                <title>{title} — Trivlu</title>
                <meta name="description" content={activity.description || `${title} in ${activity.destinationName}`}/>
                <link rel="canonical"
                      href={`${SITE_URL}/destination/${destinationSlug || activity.destinationSlug}/activity/${activity.slug}`}/>
            </Helmet>
            <nav className="activity-detail-breadcrumbs">
                <Link to="/">Home</Link>
                <span>&rsaquo;</span>
                <Link to={`/destination/${destinationSlug || activity.destinationSlug}?tab=activities`}>
                    {activity.destinationName}
                </Link>
                <span>&rsaquo;</span>
                <span>{title}</span>
            </nav>

            <div className="activity-detail-container">
                <div className="activity-detail-image-section">
                    <img src={imageUrl} alt={title} className="activity-detail-image"/>
                </div>

                <div className="activity-detail-info">
                    <span className="activity-category">{category}</span>
                    <h1 className="activity-detail-title">{title}</h1>

                    <div className="activity-detail-meta">
                        <span className="activity-detail-price">{formattedPrice}</span>
                    </div>

                    <p className="activity-detail-description">{activity.description}</p>

                    {activity.includes && (
                        <div className="activity-detail-includes">
                            <strong>Includes:</strong> {activity.includes}
                        </div>
                    )}

                    <button
                        className="add-to-trip-btn activity-detail-add-btn"
                        onClick={handleAddToTrip}
                        disabled={isAdded}
                    >
                        {isAdded ? 'Added to Trip' : 'Add to Trip'}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default ActivityDetailPage;
