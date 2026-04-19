import {useContext} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import './PackageCard.css';

function PackageCard({ pkg }) {
  const { dispatch } = useContext(AppContext);
  const location = useLocation();
  const navigate = useNavigate();

  const handleSelectPackage = () => {
    dispatch({ type: 'SELECT_PACKAGE', pkg });
    const params = new URLSearchParams(location.search);
    params.set('tab', 'trip-builder');
    navigate({pathname: location.pathname, search: params.toString()});
  };

  return (
      <div className="card package-card">
      <img 
        src={pkg.image} 
        alt={pkg.title} 
        className="package-image" 
        loading="lazy" 
      />
      <div className="package-content">
        <h3 className="package-title">{pkg.title}</h3>
        <p className="package-description">{pkg.description}</p>
        <div className="package-footer">
          <span className="package-price">{pkg.price}</span>
          <button 
            className="select-package-btn"
            onClick={handleSelectPackage}
          >
            Select Package
          </button>
        </div>
      </div>
    </div>
  );
}

export default PackageCard;
