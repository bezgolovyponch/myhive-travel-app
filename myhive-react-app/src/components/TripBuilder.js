import {useContext, useEffect, useState} from 'react';
import {AppContext} from '../context/AppContext';
import api from '../services/api';
import {capitalizeFirst, formatDate, formatPricePerPerson} from '../utils/format';
import ContactForm from './ContactForm';
import SuccessModal from './SuccessModal';
import './TripBuilder.css';

const VISIBLE_CATEGORY_COUNT = 12;

function TripBuilder({ destinationId }) {
  const { state, dispatch } = useContext(AppContext);
  const [browseFilter, setBrowseFilter] = useState('all');
  const [categories, setCategories] = useState([]);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [showContactForm, setShowContactForm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [successContactData, setSuccessContactData] = useState(null);

  useEffect(() => {
    if (!destinationId) return;
    api.getCategoriesForDestination(destinationId).then(setCategories).catch(() => {});
  }, [destinationId]);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
      dispatch({type: 'ADD_TO_TRIP', activity, silent: true});
  };

  const handleConfirmTrip = () => {
    setShowContactForm(true);
  };

  const handleContactSubmit = async (contactData) => {
    if (state.tripItems.length === 0) {
      alert('Please add some activities to your trip before submitting.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const bookingData = {
        tripName: 'Booking',
        userEmail: contactData.email,
        customerName: contactData.fullName,
        phone: contactData.phone,
        numberOfTravelers: parseInt(contactData.numberOfTravelers, 10) || 1,
        destinations: [{
          destinationName: 'Custom Travel Package',
          country: 'Not specified',
          duration: contactData.startDate && contactData.endDate ?
              Math.ceil((new Date(contactData.endDate) - new Date(contactData.startDate)) / (1000 * 60 * 60 * 24)) + 1 : 1,
          startDate: contactData.startDate,
          endDate: contactData.endDate,
          activities: state.tripItems.map(item => ({
            activityId: item.id,
            activityName: item.name,
            category: (item.categories && item.categories.length > 0)
                ? item.categories.map(c => c.name).join(', ')
                : 'General',
            description: item.description || '',
            price: item.price || 0,
            duration: item.duration || 0,
            timeOfDay: item.timeOfDay || 'Any',
            packageId: item.packageId || null,
            packageName: item.packageName || null,
            packageDiscountPct: item.packageDiscountPct || null,
          }))
        }],
        notes: `Full Name: ${contactData.fullName} | Special requirements: ${contactData.specialRequirements || 'None'} | Contact method: ${contactData.contactMethod} | Number of travelers: ${contactData.numberOfTravelers}`
      };

      await api.createBookingFromTrip(bookingData);

      setShowContactForm(false);
      setSuccessContactData(contactData);
      setShowSuccessModal(true);
    } catch (error) {
      console.error('Booking submission error:', error);
      setSubmitError(error.message || 'Failed to submit booking. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const travelers = state.tripTravelers || 1;

  const standalone = state.tripItems.filter(i => !i.packageId);
  const packageGroups = state.tripItems.reduce((acc, item) => {
      if (!item.packageId) {
          return acc;
      }
      if (!acc[item.packageId]) {
          acc[item.packageId] = {
              packageId: item.packageId,
              packageName: item.packageName,
              packageDiscountPct: Number(item.packageDiscountPct) || 0,
              items: [],
          };
      }
      acc[item.packageId].items.push(item);
      return acc;
  }, {});
  const groupsArray = Object.values(packageGroups);

  const totalPrice = (() => {
      let total = 0;
      standalone.forEach(it => {
          total += (Number(it.price) || 0) * travelers;
      });
      groupsArray.forEach(g => {
          const sub = g.items.reduce((s, it) => s + (Number(it.price) || 0) * travelers, 0);
          total += sub * (100 - g.packageDiscountPct) / 100;
      });
      return Math.round(total * 100) / 100;
  })();

  const filteredBrowseActivities = browseFilter === 'all'
      ? state.activities
      : state.activities.filter(a => (a.categories || []).some(c => c.slug === browseFilter));

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-left">
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} {state.tripItems.length === 1 ? 'activity' : 'activities'} selected</p>
          {state.tripItems.length > 0 && (
              <div className="itinerary-trip-info">
                <div className="trip-info-row">
                  <label>Travelers:</label>
                  <input
                      type="number"
                      className="trip-info-input"
                      value={travelers}
                      onChange={e => dispatch({
                        type: 'UPDATE_TRIP_TRAVELERS',
                        travelers: Math.max(1, parseInt(e.target.value, 10) || 1)
                      })}
                      min="1"
                      max="20"
                  />
                </div>
                {(state.tripStartDate || state.tripEndDate) && (
                    <div className="trip-info-row">
                      <label>Dates:</label>
                      <span>{formatDate(state.tripStartDate)} — {formatDate(state.tripEndDate)}</span>
                    </div>
                )}
              </div>
          )}
        </div>
        <div className="itinerary-list">
          {state.tripItems.length > 0 ? (
            <>
              {groupsArray.map(group => (
                <div key={group.packageId} className="package-group">
                  <div className="package-group-header">
                    <span className="package-group-name">{group.packageName}</span>
                    {group.packageDiscountPct > 0 && (
                      <span className="package-group-discount">{group.packageDiscountPct}% off</span>
                    )}
                    <button
                      className="remove-item-btn"
                      onClick={() => dispatch({type: 'REMOVE_PACKAGE_FROM_TRIP', packageId: group.packageId})}
                    >
                      ×
                    </button>
                  </div>
                  <div className="package-group-items">
                    {group.items.map(item => (
                      <div key={item.id} className="itinerary-item package-group-activity">
                        <img src={item.imageUrl} alt={item.name}
                             className="itinerary-item-image" loading="lazy"/>
                        <div className="itinerary-item-content">
                          <div className="itinerary-item-title">{item.name}</div>
                          <div className="itinerary-item-price">
                            {travelers > 1
                                ? `€${item.price} × ${travelers} = €${item.price * travelers}`
                                : formatPricePerPerson(item.price)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
              {standalone.map(item => (
                <div key={item.id} className="itinerary-item">
                  <img src={item.imageUrl} alt={item.name} className="itinerary-item-image"
                       loading="lazy"/>
                  <div className="itinerary-item-content">
                    <div className="itinerary-item-title">{item.name}</div>
                    <div className="itinerary-item-price">
                      {travelers > 1
                          ? `€${item.price} × ${travelers} = €${item.price * travelers}`
                          : formatPricePerPerson(item.price)}
                    </div>
                  </div>
                  <button
                    className="remove-item-btn"
                    onClick={() => handleRemoveActivity(item.id)}
                  >
                    ×
                  </button>
                </div>
              ))}
            </>
          ) : (
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
            </div>
          )}
        </div>
        {state.tripItems.length > 0 && (
            <div className="trip-actions">
              <div className="itinerary-total">
                <span>Total</span>
                <span className="itinerary-total-price">€{totalPrice}</span>
              </div>
            <button className="btn btn--primary btn--full-width confirm-btn" onClick={handleConfirmTrip}>
              Complete Booking
            </button>
              {submitError && (
                  <div className="export-error">
                    <p>{submitError}</p>
                  </div>
              )}
            </div>
        )}
      </div>
      <div className="trip-builder-right">
        <div className="browse-header">
          <h3>Browse More Activities</h3>
          <div className="filter-group">
            <div className="browse-filters">
              <button
                  key="all"
                  className={`filter-btn ${browseFilter === 'all' ? 'active' : ''}`}
                  onClick={() => setBrowseFilter('all')}
              >
                All
              </button>
              {(showAllCategories ? categories : categories.slice(0, VISIBLE_CATEGORY_COUNT)).map(category => (
                  <button
                      key={category.slug}
                      className={`filter-btn ${browseFilter === category.slug ? 'active' : ''}`}
                      onClick={() => setBrowseFilter(category.slug)}
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
        <div className="browse-activities">
          {filteredBrowseActivities.map(activity => {
            const isAdded = state.tripItems.some(item => item.id === activity.id);
            return (
                <div key={activity.id} className="browse-activity-item">
                  <img src={activity.imageUrl} alt={activity.name}
                       className="browse-activity-image" loading="lazy"/>
                  <div className="browse-activity-content">
                    <div className="browse-activity-title">{activity.name}</div>
                    <div className="browse-activity-price">{formatPricePerPerson(activity.price)}</div>
                  </div>
                  <button
                      className="browse-add-btn"
                      onClick={() => handleAddActivity(activity)}
                      disabled={isAdded}
                  >
                    {isAdded ? 'Added' : 'Add'}
                  </button>
                </div>
            );
          })}
        </div>
      </div>

      <ContactForm
          isOpen={showContactForm}
          onClose={() => setShowContactForm(false)}
          onSubmit={handleContactSubmit}
          tripData={{tripItems: state.tripItems, travelers}}
          initialValues={{
            numberOfTravelers: travelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate
          }}
          isSubmitting={isSubmitting}
          submitError={submitError}
      />

      <SuccessModal
          isOpen={showSuccessModal}
          onClose={() => setShowSuccessModal(false)}
          userName={successContactData?.fullName || 'Traveler'}
          userEmail={successContactData?.email || ''}
      />
    </div>
  );
}

export default TripBuilder;
