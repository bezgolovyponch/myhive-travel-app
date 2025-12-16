import { useContext, useState } from 'react';
import { AppContext } from '../context/AppContext';
import ActivityCard from '../components/ActivityCard';
import TripBuilder from '../components/TripBuilder';
import { useParams } from 'react-router-dom';
import PackageCard from '../components/PackageCard'; // Added import statement for PackageCard

function DestinationPage() {
  const { id } = useParams();
  const { state } = useContext(AppContext);
  const [currentFilter, setCurrentFilter] = useState('all');
  
  const filteredActivities = currentFilter === 'all' 
    ? state.activities 
    : state.activities.filter(a => a.category === currentFilter);

  return (
    <div className="destination-page">
      <div className="destination-header">
        <h1>Destination: {id}</h1>
        <p>Volcanic adventures and beach parties</p>
      </div>
      
      <nav className="tab-nav">
        <button 
          className={`tab-btn ${state.currentTab === 'activities' ? 'active' : ''}`}
          onClick={() => setCurrentFilter('all')}
        >
          Activities
        </button>
        <button 
          className={`tab-btn ${state.currentTab === 'packages' ? 'active' : ''}`}
          onClick={() => state.currentTab = 'packages'}
        >
          Packages
        </button>
        <button 
          className={`tab-btn ${state.currentTab === 'trip-builder' ? 'active' : ''}`}
          onClick={() => state.currentTab = 'trip-builder'}
        >
          Trip Builder
        </button>
      </nav>

      <div className="tab-content">
        <div className="tab-header">
          <h2>Activities</h2>
          <div className="category-filters">
            {['all', 'nightlife', 'adventure', 'daytime'].map(filter => (
              <button 
                key={filter}
                className={`filter-btn ${currentFilter === filter ? 'active' : ''}`}
                onClick={() => setCurrentFilter(filter)}
              >
                {filter === 'all' ? 'All' : filter.charAt(0).toUpperCase() + filter.slice(1)}
              </button>
            ))}
          </div>
        </div>

        <div className="activities-grid">
          {filteredActivities.map(activity => (
            <ActivityCard 
              key={activity.id} 
              activity={activity}
              isAdded={state.tripItems.some(item => item.id === activity.id)}
            />
          ))}
        </div>
      </div>

      {/* Packages Tab */}
      <div id="packages-tab" className="tab-content" style={{ display: state.currentTab === 'packages' ? 'block' : 'none' }}>
        <div className="packages-grid">
          {state.packages.map(pkg => (
            <PackageCard key={pkg.id} pkg={pkg} />
          ))}
        </div>
      </div>

      {/* Trip Builder Tab */}
      <div id="trip-builder-tab" className="tab-content" style={{ display: state.currentTab === 'trip-builder' ? 'block' : 'none' }}>
        <TripBuilder />
      </div>
    </div>
  );
}

export default DestinationPage;
