import {useEffect, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';
import {useFetchBySlug} from '../hooks/useFetchBySlug';
import {SITE_URL, WHATSAPP_URL} from '../services/config';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatPrice} from '../utils/format';
import {parseDescriptionBlocks} from '../utils/descriptionBlocks';
import ActivityGallery from '../components/ActivityGallery';
import ActivityCard from '../components/ActivityCard';
import './ActivityDetailPage.css';

const MAX_MORE_ACTIVITIES = 4;

function formatDuration(minutes) {
    if (minutes < 60) {
        return `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest ? `${hours}h ${rest}m` : `${hours}h`;
}

function ActivityDetailPage() {
    const {destinationSlug, slug} = useParams();
    const navigate = useNavigate();
    const {state, dispatch} = useTrip();
    const {data: activity, loading, error} = useFetchBySlug(api.getActivityBySlug, slug);
    const [moreActivities, setMoreActivities] = useState([]);

    const destinationId = activity?.destinationId;
    const activityId = activity?.id;

    useEffect(() => {
        if (!destinationId) {
            return;
        }
        let cancelled = false;
        api.getActivities(destinationId)
            .then(list => {
                if (!cancelled) {
                    setMoreActivities(
                        (list || []).filter(a => a.id !== activityId).slice(0, MAX_MORE_ACTIVITIES)
                    );
                }
            })
            .catch(() => {
                // The "More Activities" strip is optional; hide it on fetch failure.
            });
        return () => {
            cancelled = true;
        };
    }, [destinationId, activityId]);

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

    const title = activity.name || activity.title;
    const formattedPrice = formatPrice(activity.price);
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;
    const category = primaryCategory ? capitalizeFirst(primaryCategory) : 'Activity';
    const isAdded = state.tripItems.some(item => item.id === activity.id);
    const durationText = activity.duration != null ? formatDuration(activity.duration) : null;
    const destSlug = destinationSlug || activity.destinationSlug;

    const photos = activity.images && activity.images.length > 0
        ? activity.images
        : [activity.imageUrl || DEFAULT_ACTIVITY_IMAGE];
    const includesItems = (activity.includes || '')
        .split(/[,;\n]+/)
        .map(item => item.trim())
        .filter(Boolean);
    const descriptionBlocks = parseDescriptionBlocks(activity.description);

    const handleAddToTrip = () => {
        dispatch({type: 'ADD_TO_TRIP', activity});
    };

    const addButton = (
        <button
            className="activity-detail-add-btn"
            onClick={handleAddToTrip}
            disabled={isAdded}
        >
            <i className={`ph ${isAdded ? 'ph-check-circle' : 'ph-plus-circle'}`} aria-hidden="true"/>
            {isAdded ? 'Added to trip' : 'Add to trip'}
        </button>
    );

    return (
        <div className="activity-detail-page">
            <Helmet>
                <title>{title} — Trivlu</title>
                <meta name="description" content={activity.description || `${title} in ${activity.destinationName}`}/>
                <link rel="canonical" href={`${SITE_URL}/destination/${destSlug}/activity/${activity.slug}`}/>
            </Helmet>

            <nav className="activity-detail-breadcrumbs">
                <Link to="/">Home</Link>
                <span className="sep">&rsaquo;</span>
                <Link to={`/destination/${destSlug}?tab=activities`}>
                    {activity.destinationName}
                </Link>
                <span className="sep">&rsaquo;</span>
                <span>{title}</span>
            </nav>

            <div className="activity-detail-title-block">
                <h1>{title}</h1>
                <div className="activity-detail-meta-line">
                    <span className="activity-detail-chip">
                        <i className="ph ph-tag" aria-hidden="true"/> {category}
                    </span>
                    {durationText && (
                        <span className="activity-detail-chip">
                            <i className="ph ph-clock" aria-hidden="true"/> {durationText}
                        </span>
                    )}
                </div>
            </div>

            <ActivityGallery images={photos} title={title}/>

            <div className="activity-detail-layout">
                <div className="activity-detail-content">
                    {includesItems.length > 0 && (
                        <section className="activity-detail-blk">
                            <h2 className="activity-detail-blk-title">
                                <i className="ph ph-check-circle" aria-hidden="true"/> What's included
                            </h2>
                            <ul className="activity-detail-inc-list">
                                {includesItems.map(item => (
                                    <li key={item}>{item}</li>
                                ))}
                            </ul>
                        </section>
                    )}
                    {descriptionBlocks.length > 0 && (
                        <section className="activity-detail-blk">
                            <h2 className="activity-detail-blk-title">
                                <i className="ph ph-note" aria-hidden="true"/> About this activity
                            </h2>
                            {descriptionBlocks.map(block => block.type === 'heading'
                                ? <h3 className="activity-detail-subhead" key={block.text}>{block.text}</h3>
                                : <p className="activity-detail-desc" key={block.text}>{block.text}</p>
                            )}
                        </section>
                    )}
                </div>

                <aside className="activity-detail-add-col">
                    <div className="activity-detail-add-panel">
                        <div className="activity-detail-price-line">
                            <span className="amt">{formattedPrice}</span>
                            <span className="per">/ person</span>
                        </div>
                        <span className="activity-detail-price-from">Group price · from</span>
                        {addButton}
                        {durationText && (
                            <ul className="activity-detail-panel-meta">
                                <li><i className="ph ph-clock" aria-hidden="true"/> {durationText}</li>
                            </ul>
                        )}
                        <p className="activity-detail-panel-help">
                            Not sure yet?{' '}
                            <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">Chat with our team</a>
                            {' '}on WhatsApp.
                        </p>
                    </div>
                </aside>
            </div>

            {moreActivities.length > 0 && (
                <section className="activity-detail-more">
                    <h2>More Activities in {activity.destinationName}</h2>
                    <div className="activity-detail-more-grid">
                        {moreActivities.map(other => (
                            <ActivityCard
                                key={other.id}
                                activity={other}
                                isAdded={state.tripItems.some(item => item.id === other.id)}
                            />
                        ))}
                    </div>
                </section>
            )}

            {/* Sticky bottom add bar, shown on mobile only (CSS). */}
            <div className="activity-detail-add-bar">
                <div className="activity-detail-add-bar-price">
                    <span className="from">from</span>
                    <span className="amt">{formattedPrice} <span>/ person</span></span>
                </div>
                {addButton}
            </div>
        </div>
    );
}

export default ActivityDetailPage;
