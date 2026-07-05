import { formatPricePerPerson } from '../../utils/format';
import './VoteTallyCard.css';

// Ranked vote tally, visually derived from the homepage hero ".vote-card"
// (name + count + progress bar). Used on the waiting screen (live) and the
// result screen (frozen, with prices).
function VoteTallyCard({ title = 'Vote results', participantCount, rows, showPrices = false }) {
    const denominator = Math.max(1, participantCount);
    return (
        <div className="vote-tally-card">
            <div className="vote-tally-head">
                <span className="vote-tally-title">{title}</span>
                <span className="vote-tally-sub">
                    {participantCount} {participantCount === 1 ? 'mate has' : 'mates have'} voted
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
