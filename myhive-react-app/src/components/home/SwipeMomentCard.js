import './SwipeMomentCard.css';

// Full-block "swipe moment" (how-it-works-v12): a swipe deck mid-session —
// progress counter, two ghost cards behind a tilted top card with the activity
// name/price and a LIKE stamp, and skip/like controls underneath.
function SwipeMomentCard({image, alt = ''}) {
    return (
        <div className="tm2" aria-hidden="true">
            <div className="tm2-count">3 / 20</div>
            <div className="tm2-deck">
                <div className="tm2-ghost tm2-ghost--b"/>
                <div className="tm2-ghost tm2-ghost--a"/>
                <div className="tm2-card">
                    <img src={image} alt={alt} loading="lazy"/>
                    <div className="tm2-info">
                        <div className="tm2-name">Steak &amp; Strip</div>
                        <div className="tm2-meta">1.5h &middot; &euro;45 / person</div>
                    </div>
                    <span className="tm2-stamp">LIKE</span>
                </div>
            </div>
            <div className="tm2-actions">
                <span className="tm2-btn tm2-btn--skip"><i className="ph ph-x"/></span>
                <span className="tm2-btn tm2-btn--like"><i className="ph ph-heart"/></span>
            </div>
        </div>
    );
}

export default SwipeMomentCard;
