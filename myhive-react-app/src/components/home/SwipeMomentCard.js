import {useT} from '../../i18n';
import './SwipeMomentCard.css';

// Full-block "swipe moment" (how-it-works-v12): a swipe deck mid-session —
// progress counter, two ghost cards behind a tilted top card with the activity
// name/price and a LIKE stamp, and skip/like controls underneath.
function SwipeMomentCard({image, alt = ''}) {
    const t = useT('home');
    return (
        <div className="tm2" aria-hidden="true">
            <div className="tm2-count">3 / 20</div>
            <div className="tm2-deck">
                <div className="tm2-ghost tm2-ghost--b"/>
                <div className="tm2-ghost tm2-ghost--a"/>
                <div className="tm2-card">
                    <img src={image} alt={alt} loading="lazy"/>
                    <div className="tm2-info">
                        <div className="tm2-name">{t('swipeMoment.cardName')}</div>
                        <div className="tm2-meta">{t('swipeMoment.cardMeta')}</div>
                    </div>
                    <span className="tm2-stamp">{t('swipeMoment.likeStamp')}</span>
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
