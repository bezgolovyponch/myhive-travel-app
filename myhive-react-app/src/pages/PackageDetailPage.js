import PageHead from '../components/PageHead';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';
import {useFetchBySlug} from '../hooks/useFetchBySlug';
import {SITE_URL} from '../services/config';
import {DEFAULT_ACTIVITY_IMAGE, formatAmount} from '../utils/format';
import {useT} from '../i18n';
import './PackageDetailPage.css';

// `pkg` is supplied by the server renderer (Next.js SSR) so the record is in the
// initial HTML; omitted in the SPA, which fetches by slug as before.
function PackageDetailPage({pkg: injectedPkg}) {
    const t = useT('packageDetail');
    const {destSlug, slug} = useParams();
    const navigate = useNavigate();
    const {dispatch} = useTrip();
    const {data: pkg, loading, error} = useFetchBySlug(api.getPackageBySlug, slug, injectedPkg);

    if (loading) {
        return (
            <div className="package-detail-page">
                <div className="package-detail-loading">{t('loading')}</div>
            </div>
        );
    }

    if (error || !pkg) {
        return (
            <div className="package-detail-page">
                <div className="package-detail-loading">
                    <p>{t('notFound')}</p>
                    <button className="btn btn--primary" onClick={() => navigate(-1)}>{t('goBack')}</button>
                </div>
            </div>
        );
    }

    const imageUrl = pkg.imageUrl || DEFAULT_ACTIVITY_IMAGE;
    const metaDescription = pkg.description
        ? pkg.description.slice(0, 160)
        : `${pkg.name} package in ${pkg.destinationName}`;
    const sortedActivities = pkg.activities
        ? [...pkg.activities].sort((a, b) => a.position - b.position)
        : [];
    const resolvedDestSlug = destSlug || pkg.destinationSlug;

    const handleAddToTrip = () => {
        dispatch({type: 'ADD_PACKAGE_TO_TRIP', pkg});
    };

    return (
        <div className="package-detail-page">
            <PageHead>
                <title>{pkg.name} — {pkg.destinationName} Package | Trivlu</title>
                <meta name="description" content={metaDescription}/>
                <link rel="canonical" href={`${SITE_URL}/destination/${resolvedDestSlug}/package/${pkg.slug}`}/>
            </PageHead>

            <nav className="package-detail-breadcrumbs">
                <Link to="/">{t('breadcrumbHome')}</Link>
                <span>&rsaquo;</span>
                <Link to={`/destination/${resolvedDestSlug}`}>{pkg.destinationName}</Link>
                <span>&rsaquo;</span>
                <span>{pkg.name}</span>
            </nav>

            <div className="package-detail-hero">
                <img src={imageUrl} alt={pkg.name} className="package-detail-hero-image"/>
                <div className="package-detail-hero-overlay">
                    <h1 className="package-detail-hero-title">{pkg.name}</h1>
                </div>
            </div>

            <div className="package-detail-grid">
                <div className="package-detail-main">
                    {pkg.description && (
                        <p className="package-detail-description">{pkg.description}</p>
                    )}

                    {sortedActivities.length > 0 && (
                        <section className="package-detail-activities-section">
                            <h2 className="package-detail-section-title">{t('includedTitle')}</h2>
                            <div className="package-detail-activities">
                                {sortedActivities.map(activity => (
                                    <Link
                                        key={activity.activityId}
                                        to={`/destination/${resolvedDestSlug}/activity/${activity.slug}`}
                                        className="package-detail-activity"
                                    >
                                        <img
                                            src={activity.imageUrl || DEFAULT_ACTIVITY_IMAGE}
                                            alt={activity.name}
                                            className="package-detail-activity-image"
                                        />
                                        <div className="package-detail-activity-info">
                                            <span className="package-detail-activity-name">{activity.name}</span>
                                            <span className="package-detail-activity-price">{formatAmount(activity.price)}</span>
                                        </div>
                                    </Link>
                                ))}
                            </div>
                        </section>
                    )}

                    {pkg.includes && (
                        <div className="package-detail-includes">
                            <strong>{t('includesLabel')}</strong> {pkg.includes}
                        </div>
                    )}
                </div>

                <aside className="package-detail-price-card">
                    <div className="package-detail-price-card-inner">
                        <div className="package-detail-original">{formatAmount(pkg.originalPrice)}</div>
                        <div className="package-detail-discounted">{formatAmount(pkg.discountedPrice)}</div>
                        <div className="package-detail-savings">
                            {t('youSave', {amount: formatAmount(pkg.savings)})}
                            {pkg.discountPct ? ` ${t('percentOff', {pct: pkg.discountPct})}` : ''}
                        </div>
                        <button className="add-to-trip-btn package-detail-add-btn" onClick={handleAddToTrip}>
                            {t('addToTrip')}
                        </button>
                    </div>
                </aside>
            </div>
        </div>
    );
}

export default PackageDetailPage;
