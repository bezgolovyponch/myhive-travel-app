import {useCallback, useContext, useEffect, useState} from 'react';
import {Helmet} from 'react-helmet-async';
import {AppContext} from '../context/AppContext';
import ActivityCard from '../components/ActivityCard';
import TripBuilder from '../components/TripBuilder';
import {useLocation, useNavigate, useParams} from 'react-router-dom';
import PackageCard from '../components/PackageCard';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {capitalizeFirst} from '../utils/format';
import './DestinationPage.css';

const PAGE_SIZE = 12;

function DestinationPage() {
    const {slug} = useParams();
    const {state} = useContext(AppContext);
  const location = useLocation();
  const navigate = useNavigate();
    const currentTab = new URLSearchParams(location.search).get('tab') || 'activities';
  const [currentFilter, setCurrentFilter] = useState('all');
  const [destination, setDestination] = useState(null);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [totalElements, setTotalElements] = useState(0);

    const fetchActivitiesPage = useCallback(async (destinationId, pageNum, category, reset = false) => {
        const categoryParam = category === 'all' ? null : category;
        const data = await api.getActivitiesPaged(destinationId, {
            page: pageNum,
            size: PAGE_SIZE,
            category: categoryParam
        });
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
    const fetchDestinationData = async () => {
      try {
        setLoading(true);
          setCurrentFilter('all');
          const destData = await api.getDestinationBySlug(slug);
        setDestination(destData);
          await fetchActivitiesPage(destData.id, 0, 'all', true);
      } catch {
          setError(true);
      } finally {
        setLoading(false);
      }
    };

    fetchDestinationData();
  }, [slug, fetchActivitiesPage]);

  const handleTabChange = (tabName) => {
    const params = new URLSearchParams(location.search);
    params.set('tab', tabName);
    navigate({pathname: location.pathname, search: params.toString()});
  };

    const handleFilterChange = async (filter) => {
        setCurrentFilter(filter);
        try {
            setLoading(true);
            await fetchActivitiesPage(destination.id, 0, filter, true);
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    };

    const handleLoadMore = async () => {
        try {
            setLoadingMore(true);
            await fetchActivitiesPage(destination.id, page + 1, currentFilter);
        } catch {
            setError(true);
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
        <h1>{destination?.name || 'Destination'}</h1>
        <p>{destination?.description || ''}</p>
      </div>
      
      <nav className="tab-nav">
          <button
              className={`tab-btn ${currentTab === 'activities' ? 'active' : ''}`}
          onClick={() => handleTabChange('activities')}
        >
          Activities
        </button>
          <button
              className={`tab-btn ${currentTab === 'packages' ? 'active' : ''}`}
          onClick={() => handleTabChange('packages')}
        >
          Packages
        </button>
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
          <div className="category-filters">
            {['all', 'nightlife', 'adventure', 'daytime'].map(filter => (
                <button
                key={filter}
                className={`filter-btn ${currentFilter === filter ? 'active' : ''}`}
                onClick={() => handleFilterChange(filter)}
              >
                    {filter === 'all' ? 'All' : capitalizeFirst(filter)}
              </button>
            ))}
          </div>
        </div>

        <div className="activities-grid">
            {activities.map(activity => (
                <ActivityCard
                    key={activity.id}
              activity={activity}
              isAdded={state.tripItems.some(item => item.id === activity.id)}
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
          {state.packages.map(pkg => (
            <PackageCard key={pkg.id} pkg={pkg} />
          ))}
        </div>
      </div>

      {/* Trip Builder Tab */}
        <div id="trip-builder-tab" className="tab-content"
             style={{display: currentTab === 'trip-builder' ? 'flex' : 'none'}}>
        <TripBuilder />
      </div>
    </div>
  );
}

export default DestinationPage;
