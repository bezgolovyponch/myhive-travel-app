import './TinderMomentCard.css';

// Full-block "swipe moment": photo styled as the top card of a swipe deck,
// with a visible LIKE stamp and like/skip controls so the choice mechanic
// reads instantly.
function TinderMomentCard({image, alt = ''}) {
    return (
        <div className="tinder-moment" aria-hidden="true">
            <div className="tinder-moment-stack"/>
            <div className="tinder-moment-card">
                <img src={image} alt={alt} loading="lazy"/>
                <span className="tinder-moment-stamp">LIKE</span>
                <div className="tinder-moment-actions">
                    <span className="tinder-moment-btn tinder-moment-btn--skip">
                        <i className="ph ph-x"/>
                    </span>
                    <span className="tinder-moment-btn tinder-moment-btn--like">
                        <i className="ph ph-heart"/>
                    </span>
                </div>
            </div>
        </div>
    );
}

export default TinderMomentCard;
