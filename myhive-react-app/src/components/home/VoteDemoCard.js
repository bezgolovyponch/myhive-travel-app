import './VoteDemoCard.css';

// Static vote-tally showcase used in the hero and the How It Works section.
const ROWS = [
    {icon: 'ph-beer-stein', name: 'Bar Crawl', num: 8, pct: 89, fill: 'var(--purple-ll)'},
    {icon: 'ph-steering-wheel', name: 'Karting', num: 6, pct: 67, fill: 'var(--purple-l)'},
    {icon: 'ph-target', name: 'Shooting', num: 5, pct: 56, fill: 'var(--purple-l)'},
    {icon: 'ph-boat', name: 'Tiki Boat', num: 4, pct: 44, fill: 'var(--purple-l)'},
];

function VoteDemoCard() {
    return (
        <aside className="vote-card" aria-hidden="true">
            <div className="vc-head">
                <span className="vc-badge"><i className="ph ph-check-square"/></span>
                <span className="vc-title">Vote on activities</span>
            </div>
            <div className="vc-sub">9 of 11 lads voted</div>
            {ROWS.map((row) => (
                <div className="vc-row" key={row.name}>
                    <div className="vc-row-top">
                        <span className="vc-name"><i className={`ph ${row.icon}`}/>{row.name}</span>
                        <span className="vc-num">{row.num}</span>
                    </div>
                    <div className="vc-bar">
                        <div className="vc-fill" style={{width: `${row.pct}%`, background: row.fill}}/>
                    </div>
                </div>
            ))}
        </aside>
    );
}

export default VoteDemoCard;
