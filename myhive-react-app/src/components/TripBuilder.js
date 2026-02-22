import {useContext, useState} from 'react';
import {AppContext} from '../context/AppContext';
import GoogleSheetsService from '../services/googleSheetsService';
import ContactForm from './ContactForm';
import SuccessModal from './SuccessModal';
import './TripBuilder.css';

function TripBuilder() {
  const { state, dispatch } = useContext(AppContext);
  const [browseFilter, setBrowseFilter] = useState('all');
  const [showContactForm, setShowContactForm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [successContactData, setSuccessContactData] = useState(null);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
    dispatch({type: 'ADD_TO_TRIP', activity});
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
      // Check if Google Sheets is configured
      const status = await GoogleSheetsService.getStatus();
      if (!status.configured) {
        setSubmitError('Google Sheets integration is not configured. Please contact support.');
        return;
      }

      // Prepare comprehensive booking data for export
      const bookingData = {
        tripName: 'Booking',
        userEmail: contactData.email,
        destinations: [{
          destinationName: 'Custom Travel Package',
          country: 'Not specified',
          duration: contactData.startDate && contactData.endDate ?
              Math.ceil((new Date(contactData.endDate) - new Date(contactData.startDate)) / (1000 * 60 * 60 * 24)) + 1 : 1,
          startDate: contactData.startDate,
          endDate: contactData.endDate,
          activities: state.tripItems.map(item => ({
            activityName: item.name || item.title,
            category: item.category || 'General',
            description: item.description || '',
            price: item.price || 0,
            duration: item.duration || 0,
            timeOfDay: item.timeOfDay || 'Any'
          }))
        }],
        notes: `Full Name: ${contactData.fullName} | Special requirements: ${contactData.specialRequirements || 'None'} | Contact method: ${contactData.contactMethod} | Number of travelers: ${contactData.numberOfTravelers}`,
        // Additional contact information for the sheet
        contactInfo: {
          fullName: contactData.fullName,
          phone: contactData.phone,
          numberOfTravelers: contactData.numberOfTravelers,
          contactMethod: contactData.contactMethod,
          specialRequirements: contactData.specialRequirements,
          hearAboutUs: contactData.hearAboutUs
        }
      };

      const result = await GoogleSheetsService.exportTrip(bookingData);

      if (result.success) {
        // Close the contact form
        setShowContactForm(false);

        // Store contact data and show success modal
        setSuccessContactData(contactData);
        setShowSuccessModal(true);

        // Optionally clear the trip
        // dispatch({ type: 'CLEAR_TRIP' });
      } else {
        setSubmitError(result.message || 'Failed to submit booking');
      }
    } catch (error) {
      console.error('Booking submission error:', error);
      setSubmitError(error.message || 'Failed to submit booking. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredBrowseActivities = browseFilter === 'all'
      ? state.activities
      : state.activities.filter(a => a.category === browseFilter);

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-left">
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} {state.tripItems.length === 1 ? 'activity' : 'activities'} selected</p>
        </div>
        <div className="itinerary-list">
          {state.tripItems.length > 0 ? (
            state.tripItems.map(item => (
              <div key={item.id} className="itinerary-item">
                <img src={item.imageUrl || item.image} alt={item.name || item.title} className="itinerary-item-image"
                     loading="lazy"/>
                <div className="itinerary-item-content">
                  <div className="itinerary-item-title">{item.name || item.title}</div>
                  <div
                      className="itinerary-item-price">{typeof item.price === 'number' ? `€${item.price}` : item.price}</div>
                </div>
                <button 
                  className="remove-item-btn"
                  onClick={() => handleRemoveActivity(item.id)}
                >
                  ×
                </button>
              </div>
            ))
          ) : (
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
            </div>
          )}
        </div>
        {state.tripItems.length > 0 && (
            <div className="trip-actions">
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
          <div className="browse-filters">
            {['all', 'nightlife', 'adventure', 'daytime'].map(filter => (
                <button
                    key={filter}
                    className={`filter-btn ${browseFilter === filter ? 'active' : ''}`}
                    onClick={() => setBrowseFilter(filter)}
                >
                  {filter === 'all' ? 'All' : filter.charAt(0).toUpperCase() + filter.slice(1)}
                </button>
            ))}
          </div>
        </div>
        <div className="browse-activities">
          {filteredBrowseActivities.map(activity => {
            const isAdded = state.tripItems.some(item => item.id === activity.id);
            return (
                <div key={activity.id} className="browse-activity-item">
                  <img src={activity.imageUrl || activity.image} alt={activity.name || activity.title}
                       className="browse-activity-image" loading="lazy"/>
                  <div className="browse-activity-content">
                    <div className="browse-activity-title">{activity.name || activity.title}</div>
                    <div
                        className="browse-activity-price">{typeof activity.price === 'number' ? `€${activity.price}` : activity.price}</div>
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
          tripData={{tripItems: state.tripItems}}
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
