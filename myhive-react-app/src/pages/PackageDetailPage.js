import {useContext, useEffect, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {DEFAULT_ACTIVITY_IMAGE, formatAmount} from '../utils/format';
import './PackageDetailPage.css';

function PackageDetailPage() {
    const {destSlug, slug} = useParams();
    const navigate = useNavigate();
    const {dispatch} = useContext(AppContext);
    const [pkg, setPkg] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        setLoading(true);
        api.getPackageBySlug(slug)
            .then(data => setPkg(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [slug]);

    if (loading) {
        return (
            <div className="package-detail-page">
                <div className="package-detail-loading">Loading...</div>
            </div>
        );
    }

    if (error || !pkg) {
        return (
            <div className="package-detail-page">
                <div className="package-detail-loading">
                    <p>Package not found.</p>
                    <button className="btn btn--primary" onClick={() => navigate(-1)}>Go Back</button>
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
            <Helmet>
                <title>{pkg.name} — {pkg.destinationName} Package | Trivlu</title>
                <meta name="description" content={metaDescription}/>
                <link rel="canonical" href={`${SITE_URL}/destination/${resolvedDestSlug}/package/${pkg.slug}`}/>
            </Helmet>

            <nav className="package-detail-breadcrumbs">
                <Link to="/">Home</Link>
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
                            <h2 className="package-detail-section-title">What's Included</h2>
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
                            <strong>Includes:</strong> {pkg.includes}
                        </div>
                    )}
                </div>

                <aside className="package-detail-price-card">
                    <div className="package-detail-price-card-inner">
                        <div className="package-detail-original">{formatAmount(pkg.originalPrice)}</div>
                        <div className="package-detail-discounted">{formatAmount(pkg.discountedPrice)}</div>
                        <div className="package-detail-savings">
                            You save {formatAmount(pkg.savings)}
                            {pkg.discountPct ? ` (${pkg.discountPct}% off)` : ''}
                        </div>
                        <button className="add-to-trip-btn package-detail-add-btn" onClick={handleAddToTrip}>
                            Add to Trip
                        </button>
                    </div>
                </aside>
            </div>
        </div>
    );
}

export default PackageDetailPage;
