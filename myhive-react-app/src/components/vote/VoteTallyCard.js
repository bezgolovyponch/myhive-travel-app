import { formatPricePerPerson } from '../../utils/format';
import { useT } from '../../i18n';
import './VoteTallyCard.css';

// Ranked vote tally, visually derived from the homepage hero ".vote-card"
// (name + count + progress bar). Used on the waiting screen (live) and the
// result screen (frozen, with prices).
function VoteTallyCard({ title, participantCount, rows, showPrices = false }) {
    const t = useT('voteComponents');
    const denominator = Math.max(1, participantCount);
    return (
        <div className="vote-tally-card">
            <div className="vote-tally-head">
                <span className="vote-tally-title">{title ?? t('tally.title')}</span>
                <span className="vote-tally-sub">
                    {participantCount === 1
                        ? t('tally.votedOne', { count: participantCount })
                        : t('tally.votedOther', { count: participantCount })}
                </span>
            </div>
            <ul className="vote-tally-list">
                {rows.map(row => (
                    <li key={row.activityId} className="vote-tally-row">
                        <div className="vote-tally-row-top">
                            <span className="vote-tally-name">{row.name}</span>
                            {showPrices && (
                                <span className="vote-tally-price">{formatPricePerPerson(row.price)}</span>
                            )}
                            <span className="vote-tally-num">{row.likeCount}</span>
                        </div>
                        <span className="vote-tally-bar">
                            <span
                                className="vote-tally-fill"
                                style={{ width: `${Math.min(100, (row.likeCount / denominator) * 100)}%` }}
                            />
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default VoteTallyCard;
