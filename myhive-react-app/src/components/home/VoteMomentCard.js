import {useT} from '../../i18n';
import './VoteMomentCard.css';

// Full-block "vote moment" (how-it-works-v12): a live vote in progress —
// status header, ranked tally rows, and the share link that step 2 is about.
const ROWS = [
    {icon: 'ph-fork-knife', nameKey: 'voteMoment.row1Name', pct: 89},
    {icon: 'ph-jeep', nameKey: 'voteMoment.row2Name', pct: 78},
    {icon: 'ph-beer-stein', nameKey: 'voteMoment.row3Name', pct: 67},
    {icon: 'ph-bathtub', nameKey: 'voteMoment.row4Name', pct: 56},
    {icon: 'ph-steering-wheel', nameKey: 'voteMoment.row5Name', pct: 44},
    {icon: 'ph-crosshair', nameKey: 'voteMoment.row6Name', pct: 33},
];

function VoteMomentCard() {
    const t = useT('home');
    return (
        <div className="vm" aria-hidden="true">
            <div className="vm-head">
                <div>
                    <div className="vm-title">{t('voteMoment.title')}</div>
                    <div className="vm-sub">{t('voteMoment.sub')}</div>
                </div>
            </div>
            <div className="vm-list">
                {ROWS.map((row, index) => (
                    <div className="vm-row" key={row.nameKey}>
                        <span className="vm-rank">{index + 1}</span>
                        <i className={`ph ${row.icon}`}/>
                        <span className="vm-name">{t(row.nameKey)}</span>
                        <span className="vm-bar"><span className="vm-fill" style={{width: `${row.pct}%`}}/></span>
                        <span className="vm-num">{row.pct}%</span>
                    </div>
                ))}
            </div>
            <div className="vm-link">
                <i className="ph ph-link-simple"/>
                <span>trivlu.com/vote/7aeda422</span>
            </div>
        </div>
    );
}

export default VoteMomentCard;
