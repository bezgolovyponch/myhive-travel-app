import {useCallback, useEffect, useRef, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {useTrip} from '../context/TripContext';
import ActivityCard from '../components/ActivityCard';
import TripBuilder from '../components/TripBuilder';
import {useLocation, useNavigate, useParams} from 'react-router-dom';
import PackageCard from '../components/PackageCard';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {capitalizeFirst} from '../utils/format';
import './DestinationPage.css';

const PAGE_SIZE = 12;
const VISIBLE_CATEGORY_COUNT = 12;

function DestinationPage() {
    const {slug} = useParams();
    const {state: trip} = useTrip();
  const location = useLocation();
  const navigate = useNavigate();
    const currentTab = new URLSearchParams(location.search).get('tab') || 'activities';
    // Mount TripBuilder only once its tab has been opened — it fetches the
    // destination's activity catalog on mount, which is wasted on visitors
    // who never leave the Activities tab. Once activated it stays mounted
    // (hidden via CSS) so its state survives tab switches.
    const [tripBuilderActivated, setTripBuilderActivated] = useState(currentTab === 'trip-builder');
    useEffect(() => {
        if (currentTab === 'trip-builder') {
            setTripBuilderActivated(true);
        }
    }, [currentTab]);
  const [currentFilter, setCurrentFilter] = useState('all');
    const [showAllCategories, setShowAllCategories] = useState(false);
  const [destination, setDestination] = useState(null);
  const [activities, setActivities] = useState([]);
    const [categories, setCategories] = useState([]);
    const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [totalElements, setTotalElements] = useState(0);
    // Errors from filter/pagination must not replace the whole loaded page.
    const [listError, setListError] = useState(false);
    const [filterLoading, setFilterLoading] = useState(false);
    const requestSeqRef = useRef(0);

    const fetchActivitiesPage = useCallback(async (destinationId, pageNum, categorySlug, reset = false) => {
        const seq = ++requestSeqRef.current;
        const categoryParam = categorySlug === 'all' ? null : categorySlug;
        let data;
        try {
            data = await api.getActivitiesPaged(destinationId, {
                page: pageNum,
                size: PAGE_SIZE,
                categorySlug: categoryParam
            });
        } catch (e) {
            if (seq !== requestSeqRef.current) {
                // A stale request failing must not disturb the newer one's UI state.
                return;
            }
            throw e;
        }
        if (seq !== requestSeqRef.current) {
            // A newer filter/page request started while this one was in flight.
            return;
        }
        setTotalElements(data.totalElements);
        setHasMore(!data.last);
        setPage(pageNum);
        if (reset) {
            setActivities(data.content);
        } else {
            setActivities(prev => [...prev, ...data.content]);
        }
    }, []);

  useEffect(() => {
    let cancelled = false;
    const fetchDestinationData = async () => {
      try {
        setLoading(true);
          setError(false);
          setListError(false);
          setCurrentFilter('all');
          const destData = await api.getDestinationBySlug(slug);
          const categoriesData = await api.getCategoriesForDestination(destData.id);
          if (cancelled) {
              return;
          }
        setDestination(destData);
          setCategories(categoriesData);
          await fetchActivitiesPage(destData.id, 0, 'all', true);
          try {
              const pkgData = await api.getPackagesByDestination(destData.id);
              if (!cancelled) {
                  setPackages(pkgData);
              }
          } catch {
              if (!cancelled) {
                  setPackages([]);
              }
          }
      } catch {
          if (!cancelled) {
              setError(true);
          }
      } finally {
          if (!cancelled) {
              setLoading(false);
          }
      }
    };

    fetchDestinationData();
    return () => {
        cancelled = true;
    };
  }, [slug, fetchActivitiesPage]);

  const handleTabChange = (tabName) => {
    const params = new URLSearchParams(location.search);
    params.set('tab', tabName);
    navigate({pathname: location.pathname, search: params.toString()});
  };

    const handleFilterChange = async (filter) => {
        setCurrentFilter(filter);
        setListError(false);
        try {
            setFilterLoading(true);
            await fetchActivitiesPage(destination.id, 0, filter, true);
        } catch {
            // Clear the old filter's list so a later Show More cannot append
            // the new filter's page 1 onto the previous filter's items.
            setActivities([]);
            setHasMore(false);
            setListError(true);
        } finally {
            setFilterLoading(false);
        }
    };

    const handleLoadMore = async () => {
        setListError(false);
        try {
            setLoadingMore(true);
            await fetchActivitiesPage(destination.id, page + 1, currentFilter);
        } catch {
            setListError(true);
        } finally {
            setLoadingMore(false);
        }
    };

  if (loading) {
    return (
      <div className="destination-page">
          <div className="page-hero destination-header">
          <h1>Loading...</h1>
        </div>
      </div>
    );
  }

    if (error || !destination) {
        return (
            <div className="destination-page">
                <div className="page-hero destination-header">
                    <h1>Destination not found</h1>
                    <button className="btn btn--primary" onClick={() => navigate('/')}>Back to Home</button>
                </div>
            </div>
        );
    }

  return (
    <div className="destination-page">
        <Helmet>
            <title>{destination.name} — Trivlu</title>
            <meta name="description"
                  content={destination.description || `Explore activities and experiences in ${destination.name} with Trivlu.`}/>
            <link rel="canonical" href={`${SITE_URL}/destination/${destination.slug}`}/>
        </Helmet>
        <div className="page-hero destination-header">
        <h1>{destination.name}</h1>
        <p>{destination.description || ''}</p>
      </div>
      
      <nav className="tab-nav">
          <button
              className={`tab-btn ${currentTab === 'activities' ? 'active' : ''}`}
          onClick={() => handleTabChange('activities')}
        >
          Activities
        </button>
          {packages.length > 0 && (
              <button
                  className={`tab-btn ${currentTab === 'packages' ? 'active' : ''}`}
                  onClick={() => handleTabChange('packages')}
              >
                  Packages
              </button>
          )}
          <button
              className={`tab-btn ${currentTab === 'trip-builder' ? 'active' : ''}`}
          onClick={() => handleTabChange('trip-builder')}
        >
          Trip Builder
        </button>
      </nav>

        <div className="tab-content" style={{display: currentTab === 'activities' ? 'flex' : 'none'}}>
        <div className="tab-header">
          <h2>Activities</h2>
            <div className="filter-group">
                <div className="category-filters">
              <button
                  key="all"
                  className={`filter-btn ${currentFilter === 'all' ? 'active' : ''}`}
                  onClick={() => handleFilterChange('all')}
              >
                  All
              </button>
                    {(showAllCategories ? categories : categories.slice(0, VISIBLE_CATEGORY_COUNT)).map(category => (
                <button
                    key={category.slug}
                    className={`filter-btn ${currentFilter === category.slug ? 'active' : ''}`}
                    onClick={() => handleFilterChange(category.slug)}
                >
                    {capitalizeFirst(category.name)}
                </button>
                    ))}
                </div>
                {categories.length > VISIBLE_CATEGORY_COUNT && (
                    <button
                        type="button"
                        className="filter-toggle"
                        onClick={() => setShowAllCategories(!showAllCategories)}
                    >
                        {showAllCategories ? 'Show less' : `Show all (${categories.length})`}
                    </button>
                )}
          </div>
        </div>

        {listError && (
            <p className="text-error">Couldn't load activities. Please try again.</p>
        )}
        {filterLoading && (
            <p>Loading activities...</p>
        )}
        <div className="activities-grid">
            {activities.map(activity => (
                <ActivityCard
                    key={activity.id}
              activity={activity}
              isAdded={trip.tripItems.some(item => item.id === activity.id)}
            />
          ))}
        </div>

            {hasMore && (
                <div className="load-more-container">
                    <button
                        className="load-more-btn"
                        onClick={handleLoadMore}
                        disabled={loadingMore}
                    >
                        {loadingMore ? 'Loading...' : `Show More (${activities.length} of ${totalElements})`}
                    </button>
                </div>
            )}
      </div>

      {/* Packages Tab */}
        <div id="packages-tab" className="tab-content"
             style={{display: currentTab === 'packages' ? 'flex' : 'none'}}>
        <div className="packages-grid">
          {packages.map(pkg => (
            <PackageCard key={pkg.id} pkg={pkg} />
          ))}
        </div>
      </div>

      {/* Trip Builder Tab */}
        <div id="trip-builder-tab" className="tab-content"
             style={{display: currentTab === 'trip-builder' ? 'flex' : 'none'}}>
        {tripBuilderActivated && <TripBuilder destinationId={destination?.id} />}
      </div>
    </div>
  );
}

export default DestinationPage;
