import {useNavigate} from 'react-router-dom';
import {useDestinationModal} from '../context/DestinationModalContext';
import {useT} from '../i18n';
import './DestinationCard.css';

function DestinationCard({ destination }) {
  const {dispatch} = useDestinationModal();
  const navigate = useNavigate();
  const t = useT('cards');

  // Map backend data to frontend format
  const imageUrl = destination.imageUrl || `https://images.unsplash.com/photo-1541849546-216549ae216d?w=400&h=300&fit=crop`;
  const hasActivities = destination.activityCount > 0;
  // Only highly-rated destinations get a badge; everything else used to fall
  // through to a misleading "Hot Deal" label.
  const badge = destination.rating >= 4.7 ? 'Popular' : null;

  const handleClick = () => {
    if (hasActivities) {
      navigate(`/destination/${destination.slug || destination.id}`);
    } else {
      dispatch({type: 'OPEN_DESTINATION_MODAL', destination});
    }
  };

  const handleKeyDown = (e) => {
    if (e.target !== e.currentTarget) {
      // Keys on nested interactive elements belong to them.
      return;
    }
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleClick();
    }
  };

  return (
    <div
        className={`card destination-card ${hasActivities ? '' : 'disabled'}`}
        role="button"
        tabIndex={0}
        aria-label={t('viewAria', {name: destination.name})}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
    >
      <img 
        src={imageUrl} 
        alt={destination.name} 
        className="destination-image" 
        loading="lazy" 
      />
      {badge && (
        <div className={`destination-badge badge-${badge.toLowerCase().replace(' ', '-')}`}>
          {t('badgePopular')}
        </div>
      )}
      <div className="destination-content">
        <h3 className="destination-name">{destination.name}</h3>
        <p className="destination-description">{destination.description}</p>
      </div>
    </div>
  );
}

export default DestinationCard;
