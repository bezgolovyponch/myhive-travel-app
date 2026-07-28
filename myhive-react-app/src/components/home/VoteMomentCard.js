import './VoteMomentCard.css';

// Full-block "vote moment" (how-it-works-v12): a live vote in progress —
// status header, ranked tally rows, and the share link that step 2 is about.
const ROWS = [
    {icon: 'ph-fork-knife', name: 'Steak & Strip', pct: 89},
    {icon: 'ph-jeep', name: 'Tank driving', pct: 78},
    {icon: 'ph-beer-stein', name: 'Bar crawl', pct: 67},
    {icon: 'ph-bathtub', name: 'Beer spa', pct: 56},
    {icon: 'ph-steering-wheel', name: 'Karting', pct: 44},
    {icon: 'ph-crosshair', name: 'Shooting', pct: 33},
];

function VoteMomentCard() {
    return (
        <div className="vm" aria-hidden="true">
            <div className="vm-head">
                <div>
                    <div className="vm-title">Voting is open</div>
                    <div className="vm-sub">9 of 11 lads voted &middot; 4h left</div>
                </div>
            </div>
            <div className="vm-list">
                {ROWS.map((row, index) => (
                    <div className="vm-row" key={row.name}>
                        <span className="vm-rank">{index + 1}</span>
                        <i className={`ph ${row.icon}`}/>
                        <span className="vm-name">{row.name}</span>
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
