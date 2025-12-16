import { useContext } from 'react';
import { AppContext } from '../context/AppContext';
import { useNavigate } from 'react-router-dom';

function DestinationCard({ destination }) {
  const { dispatch } = useContext(AppContext);
  const navigate = useNavigate();

  const handleClick = () => {
    if (destination.clickable) {
      dispatch({ type: 'NAVIGATE', path: `/destination/${destination.id}` });
      navigate(`/destination/${destination.id}`);
    } else {
      alert(`⏳ ${destination.name} is coming soon!\n\nWe're working hard to bring you amazing experiences here.`);
    }
  };

  return (
    <div 
      className={`destination-card ${destination.clickable ? '' : 'disabled'}`}
      onClick={handleClick}
    >
      <img 
        src={destination.image} 
        alt={destination.name} 
        className="destination-image" 
        loading="lazy" 
      />
      <div className={`destination-badge badge-${destination.badge.toLowerCase().replace(' ', '-')}`}>
        {destination.badge}
      </div>
      <div className="destination-content">
        <h3 className="destination-name">{destination.name}</h3>
        <p className="destination-description">{destination.description}</p>
      </div>
    </div>
  );
}

export default DestinationCard;
