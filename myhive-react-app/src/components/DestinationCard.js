import {useContext} from 'react';
import {AppContext} from '../context/AppContext';
import {useNavigate} from 'react-router-dom';

function DestinationCard({ destination }) {
  const { dispatch } = useContext(AppContext);
  const navigate = useNavigate();

  // Map backend data to frontend format
  const imageUrl = destination.imageUrl || `https://images.unsplash.com/photo-1541849546-216549ae216d?w=400&h=300&fit=crop`;
  const isClickable = destination.id === 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'; // Tenerife UUID
  const badge = destination.rating >= 4.7 ? 'Popular' : 'Hot Deal';

  const handleClick = () => {
    if (isClickable) {
      dispatch({ type: 'NAVIGATE', path: `/destination/${destination.id}` });
      navigate(`/destination/${destination.id}`);
    } else {
      // Show coming soon modal instead of alert
      dispatch({type: 'OPEN_DESTINATION_MODAL', destination});
    }
  };

  return (
    <div 
      className={`destination-card ${isClickable ? '' : 'disabled'}`}
      onClick={handleClick}
    >
      <img 
        src={imageUrl} 
        alt={destination.name} 
        className="destination-image" 
        loading="lazy" 
      />
      {badge && (
        <div className={`destination-badge badge-${badge.toLowerCase().replace(' ', '-')}`}>
          {badge}
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
