import { useContext } from 'react';
import { AppContext } from '../context/AppContext';

function PackageCard({ pkg }) {
  const { dispatch } = useContext(AppContext);

  const handleSelectPackage = () => {
    dispatch({ type: 'SELECT_PACKAGE', pkg });
    dispatch({ type: 'SWITCH_TAB', tab: 'trip-builder' });
  };

  return (
    <div className="package-card">
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
