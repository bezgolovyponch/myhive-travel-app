import PageHead from '../components/PageHead';
import {useEffect, useState} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';
import {useFetchBySlug} from '../hooks/useFetchBySlug';
import {SITE_URL, WHATSAPP_URL} from '../services/config';
import {capitalizeFirst, DEFAULT_ACTIVITY_IMAGE, formatAmount, formatPrice, hasGroupMin} from '../utils/format';
import {parseDescriptionBlocks} from '../utils/descriptionBlocks';
import ActivityGallery from '../components/ActivityGallery';
import ActivityCard from '../components/ActivityCard';
import {useT} from '../i18n';
import './ActivityDetailPage.css';

const MAX_MORE_ACTIVITIES = 4;

function formatDuration(minutes, t) {
    if (minutes < 60) {
        return t('duration.minutes', {minutes});
    }
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest ? t('duration.hoursMinutes', {hours, rest}) : t('duration.hours', {hours});
}

// `activity` is supplied by the server renderer (Next.js SSR) so the record is
// in the initial HTML; omitted in the SPA, which fetches by slug as before.
function ActivityDetailPage({activity: injectedActivity}) {
    const t = useT('activityDetail');
    const tMeta = useT('meta');
    const {destinationSlug, slug} = useParams();
    const navigate = useNavigate();
    const {state, dispatch} = useTrip();
    const {data: activity, loading, error} = useFetchBySlug(api.getActivityBySlug, slug, injectedActivity);
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
                <div className="activity-detail-loading">{t('loading')}</div>
            </div>
        );
    }

    if (error || !activity) {
        return (
            <div className="activity-detail-page">
                <div className="activity-detail-loading">
                    <p>{t('notFound')}</p>
                    <button className="btn btn--primary" onClick={() => navigate(-1)}>{t('goBack')}</button>
                </div>
            </div>
        );
    }

    const title = activity.name || activity.title;
    const formattedPrice = formatPrice(activity.price);
    const primaryCategory = activity.categories && activity.categories.length > 0
        ? activity.categories[0].name
        : null;
    const category = primaryCategory ? capitalizeFirst(primaryCategory) : t('categoryFallback');
    const isAdded = state.tripItems.some(item => item.id === activity.id);
    const durationText = activity.duration != null ? formatDuration(activity.duration, t) : null;
    const destSlug = destinationSlug || activity.destinationSlug;

    const photos = activity.images && activity.images.length > 0
        ? activity.images
        : [activity.imageUrl || DEFAULT_ACTIVITY_IMAGE];
    const includesItems = (activity.includes || '')
        .split(/[;\n]+/)
        .map(item => item.trim())
        .filter(Boolean);
    const descriptionBlocks = parseDescriptionBlocks(activity.description);

    // Buyer-decision fields (Baymard tours study): meeting point, availability
    // window and cancellation terms. Real values when the activity supplies them,
    // otherwise accurate defaults that hold across our stag activities.
    const meetingPoint = activity.meetingPoint
        || t('facts.meetingPointDefault', {place: activity.destinationName || t('facts.cityCentreFallback')});
    const timeWindow = activity.timeWindow
        || activity.availability
        || t('facts.availabilityDefault');
    const cancellationPolicy = activity.cancellationPolicy
        || t('facts.cancellationDefault');

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
            {isAdded ? t('addedToTrip') : t('addToTrip')}
        </button>
    );

    return (
        <div className="activity-detail-page">
            <PageHead>
                <title>{tMeta('activity.title', {name: title, destination: activity.destinationName})}</title>
                <meta name="description"
                      content={activity.description || tMeta('activity.fallbackDescription', {name: title, destination: activity.destinationName})}/>
                <link rel="canonical" href={`${SITE_URL}/destination/${destSlug}/activity/${activity.slug}`}/>
            </PageHead>

            <nav className="activity-detail-breadcrumbs">
                <Link to="/">{t('breadcrumbHome')}</Link>
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
                                <i className="ph ph-check-circle" aria-hidden="true"/> {t('includedTitle')}
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
                                <i className="ph ph-note" aria-hidden="true"/> {t('aboutTitle')}
                            </h2>
                            {descriptionBlocks.map(block => block.type === 'heading'
                                ? <h3 className="activity-detail-subhead" key={block.text}>{block.text}</h3>
                                : <p className="activity-detail-desc" key={block.text}>{block.text}</p>
                            )}
                        </section>
                    )}
                    <section className="activity-detail-blk">
                        <h2 className="activity-detail-blk-title">
                            <i className="ph ph-info" aria-hidden="true"/> {t('goodToKnowTitle')}
                        </h2>
                        <ul className="activity-detail-facts">
                            <li>
                                <i className="ph ph-map-pin" aria-hidden="true"/>
                                <div>
                                    <span className="activity-detail-fact-label">{t('facts.meetingPointLabel')}</span>
                                    <span className="activity-detail-fact-value">{meetingPoint}</span>
                                </div>
                            </li>
                            <li>
                                <i className="ph ph-clock" aria-hidden="true"/>
                                <div>
                                    <span className="activity-detail-fact-label">{t('facts.availabilityLabel')}</span>
                                    <span className="activity-detail-fact-value">{timeWindow}</span>
                                </div>
                            </li>
                            <li>
                                <i className="ph ph-calendar-x" aria-hidden="true"/>
                                <div>
                                    <span className="activity-detail-fact-label">{t('facts.cancellationLabel')}</span>
                                    <span className="activity-detail-fact-value">
                                        {cancellationPolicy}{' '}
                                        <Link to="/refund-policy">{t('facts.refundPolicyLink')}</Link>
                                    </span>
                                </div>
                            </li>
                        </ul>
                    </section>
                </div>

                <aside className="activity-detail-add-col">
                    <div className="activity-detail-add-panel">
                        <div className="activity-detail-price-line">
                            {hasGroupMin(activity) && <span className="per">{t('from')}{' '}</span>}
                            <span className="amt">{formattedPrice}</span>
                            <span className="per">{t('perPerson')}</span>
                        </div>
                        <span className="activity-detail-price-from">
                            {hasGroupMin(activity)
                                ? t('groupMinimum', {amount: formatAmount(Number(activity.minPrice))})
                                : t('groupPriceFrom')}
                        </span>
                        {addButton}
                        {durationText && (
                            <ul className="activity-detail-panel-meta">
                                <li><i className="ph ph-clock" aria-hidden="true"/> {durationText}</li>
                            </ul>
                        )}
                        <p className="activity-detail-panel-help">
                            {t('help.textBefore')}{' '}
                            <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">{t('help.linkLabel')}</a>
                            {' '}{t('help.textAfter')}
                        </p>
                    </div>
                </aside>
            </div>

            {moreActivities.length > 0 && (
                <section className="activity-detail-more">
                    <h2>{t('moreActivities', {destination: activity.destinationName})}</h2>
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
                    <span className="from">{t('from')}</span>
                    <span className="amt">{formattedPrice} <span>{t('perPerson')}</span></span>
                </div>
                {addButton}
            </div>
        </div>
    );
}

export default ActivityDetailPage;
