import {Link} from 'react-router-dom';
import {formatAmount} from '../utils/format';
import './PackageCard.css';

function PackageCard({pkg}) {
    const savings = Number(pkg.savings) || 0;
    return (
        <Link to={`/destination/${pkg.destinationSlug}/package/${pkg.slug}`} className="card package-card">
            {pkg.imageUrl && (
                <img src={pkg.imageUrl} alt={pkg.name} className="package-image" loading="lazy"/>
            )}
            <div className="package-content">
                <h3 className="package-title">{pkg.name}</h3>
                {pkg.description && (
                    <p className="package-description">{pkg.description}</p>
                )}
                <div className="package-pricing">
                    <span className="package-original">{formatAmount(pkg.originalPrice)}</span>
                    <span className="package-discounted">{formatAmount(pkg.discountedPrice)}</span>
                    {savings > 0 && (
                        <span className="package-savings">Save {formatAmount(savings)}</span>
                    )}
                </div>
            </div>
        </Link>
    );
}

export default PackageCard;
