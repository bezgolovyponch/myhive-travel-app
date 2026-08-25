import PageHead from '../components/PageHead';
import {useCallback, useEffect, useRef, useState} from 'react';
import {useTrip} from '../context/TripContext';
import ActivityCard from '../components/ActivityCard';
import TripBuilder from '../components/TripBuilder';
import {useLocation, useNavigate, useParams} from 'react-router-dom';
import PackageCard from '../components/PackageCard';
import api from '../services/api';
import {SITE_URL} from '../services/config';
import {capitalizeFirst} from '../utils/format';
import useTripDeepLink from '../hooks/useTripDeepLink';
import {useT} from '../i18n';
import './DestinationPage.css';

const PAGE_SIZE = 12;
const VISIBLE_CATEGORY_COUNT = 12;

// `initial` lets a server renderer seed the whole first paint — destination,
// categories, page 0 of activities (with its pagination metadata) and packages —
// so the catalog reaches the initial HTML instead of arriving in the effect
// below, which no crawler runs. Filtering and Show More still page on the client
// from there. Omitted in the SPA, which fetches on mount exactly as before.
function DestinationPage({initial}) {
    const t = useT('destination');
    const tMeta = useT('meta');
    const {slug} = useParams();
    const {state: trip} = useTrip();
  const location = useLocation();
  const navigate = useNavigate();
    useTripDeepLink();
    const currentTab = new URLSearchParams(location.search).get('tab') || 'activities';
    // Deep-linked category filter (?category=<slug>) — how the landing pages'
    // category rows open "the category page". Applied by an effect below once
    // the categories list is known, so an unknown slug degrades to 'all'.
    const categoryParam = new URLSearchParams(location.search).get('category');
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
  const [destination, setDestination] = useState(initial?.destination ?? null);
  const [activities, setActivities] = useState(initial?.activities ?? []);
    const [categories, setCategories] = useState(initial?.categories ?? []);
    const [packages, setPackages] = useState(initial?.packages ?? []);
  const [loading, setLoading] = useState(!initial);
    const [error, setError] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [page, setPage] = useState(0);
    const [hasMore, setHasMore] = useState(initial ? !initial.last : true);
    const [totalElements, setTotalElements] = useState(initial?.totalElements ?? 0);
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
    if (initial) {
        // Server already supplied destination, categories, page 0 and packages.
        return undefined;
    }
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
  }, [slug, fetchActivitiesPage, initial]);

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

    // Apply the deep-linked category once the destination and its categories
    // have loaded (both the SPA fetch and the server-seeded first paint).
    // Guarded by the categories list so a stale/foreign slug falls back to the
    // full list instead of fetching an empty page. Later manual filter clicks
    // don't refire this — the deps only change with the URL or a new load.
    useEffect(() => {
        if (!destination || !categoryParam) {
            return;
        }
        if (!categories.some((c) => c.slug === categoryParam)) {
            return;
        }
        handleFilterChange(categoryParam);
        // handleFilterChange is recreated per render but only reads stable refs.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [categoryParam, destination, categories]);

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
          <div className="page-hero">
          <h1>{t('loading')}</h1>
        </div>
      </div>
    );
  }

    if (error || !destination) {
        return (
            <div className="destination-page">
                <div className="page-hero">
                    <h1>{t('notFound')}</h1>
                    <button className="btn btn--primary" onClick={() => navigate('/')}>{t('backToHome')}</button>
                </div>
            </div>
        );
    }

  return (
    <div className={`destination-page${trip.checkoutOpen ? ' destination-page--checkout' : ''}`}>
        <PageHead>
            <title>{destination.name} — Trivlu</title>
            <meta name="description"
                  content={destination.description || tMeta('destination.description', {name: destination.name})}/>
            <link rel="canonical" href={`${SITE_URL}/destination/${destination.slug}`}/>
        </PageHead>
      {/* The catalog carried no h1 outside its loading/error states, which the SSR
          smoke checks flag (exactly one per page) and which cost the highest-
          traffic organic page its main heading. Hidden on the Trip Builder tab and
          during checkout, like the tab bar below, so the focused flows are
          unchanged. */}
      {!trip.checkoutOpen && currentTab !== 'trip-builder' && (
          <div
              className="page-hero destination-header"
              style={destination.imageUrl ? {
                  backgroundImage: `linear-gradient(rgba(0,0,0,0.45), rgba(0,0,0,0.45)), url(${destination.imageUrl})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center',
              } : undefined}
          >
              <h1>{t('heroTitle', {name: destination.name})}</h1>
              {destination.description && <p>{destination.description}</p>}
          </div>
      )}
      {/* Tab bar hides on the Trip Builder tab (and during checkout) so nothing
          pulls the user away from completing the booking; breadcrumbs + Browse
          More Activities still lead back to the catalog. */}
      <nav
          className="tab-nav"
          style={{display: (trip.checkoutOpen || currentTab === 'trip-builder') ? 'none' : undefined}}
      >
          <button
              className={`tab-btn ${currentTab === 'activities' ? 'active' : ''}`}
          onClick={() => handleTabChange('activities')}
        >
          {t('tabActivities')}
        </button>
          {packages.length > 0 && (
              <button
                  className={`tab-btn ${currentTab === 'packages' ? 'active' : ''}`}
                  onClick={() => handleTabChange('packages')}
              >
                  {t('tabPackages')}
              </button>
          )}
          <button
              className={`tab-btn ${currentTab === 'trip-builder' ? 'active' : ''}`}
          onClick={() => handleTabChange('trip-builder')}
        >
          {t('tabTripBuilder')}
        </button>
      </nav>

        <div className="tab-content" style={{display: currentTab === 'activities' ? 'flex' : 'none'}}>
        <div className="tab-header">
            <div className="filter-group">
                <div className="category-filters">
              <button
                  key="all"
                  className={`filter-btn ${currentFilter === 'all' ? 'active' : ''}`}
                  onClick={() => handleFilterChange('all')}
              >
                  {t('filterAll')}
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
                        {showAllCategories ? t('showLess') : t('showAll', {count: categories.length})}
                    </button>
                )}
          </div>
        </div>

        {listError && (
            <p className="text-error">{t('listError')}</p>
        )}
        {filterLoading && (
            <p>{t('loadingActivities')}</p>
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
                        {loadingMore ? t('loading') : t('showMore', {shown: activities.length, total: totalElements})}
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

      {/* Trip Builder Tab — tabs are hidden here, so this panel reserves the
          header/breadcrumbs clearance itself. */}
        <div id="trip-builder-tab" className="tab-content tab-content--trip"
             style={{display: currentTab === 'trip-builder' ? 'flex' : 'none'}}>
        {tripBuilderActivated && <TripBuilder destinationId={destination?.id} destinationSlug={destination?.slug} destinationName={destination?.name} />}
      </div>
    </div>
  );
}

export default DestinationPage;
