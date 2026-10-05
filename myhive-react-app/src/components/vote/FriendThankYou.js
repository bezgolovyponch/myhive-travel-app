import {useT} from '../../i18n';
import './FriendVote.css';

// The only screen a friend sees once their vote is in, or once voting closed
// before they voted: no tally, no link, no dashboard.
function FriendThankYou({closed = false}) {
    const t = useT('vote');
    return (
        <div className="fv-screen fv-thanks" role="status">
            <div className="fv-thanks-mark" aria-hidden="true">{closed ? '⏱' : '✓'}</div>
            <h1 className="fv-thanks-title">{closed ? t('thanks.closedTitle') : t('thanks.title')}</h1>
            <p className="fv-thanks-text">{closed ? t('thanks.closedText') : t('thanks.text')}</p>
        </div>
    );
}

export default FriendThankYou;
