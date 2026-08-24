import {useT} from '../../i18n';
import './VoteDemoCard.css';

// Static vote-tally showcase used in the hero and the How It Works section.
const ROWS = [
    {icon: 'ph-beer-stein', nameKey: 'voteDemo.row1Name', num: 8, pct: 89, fill: 'var(--purple-ll)'},
    {icon: 'ph-steering-wheel', nameKey: 'voteDemo.row2Name', num: 6, pct: 67, fill: 'var(--purple-l)'},
    {icon: 'ph-target', nameKey: 'voteDemo.row3Name', num: 5, pct: 56, fill: 'var(--purple-l)'},
    {icon: 'ph-boat', nameKey: 'voteDemo.row4Name', num: 4, pct: 44, fill: 'var(--purple-l)'},
];

function VoteDemoCard() {
    const t = useT('home');
    return (
        <aside className="vote-card" aria-hidden="true">
            <div className="vc-head">
                <span className="vc-badge"><i className="ph ph-check-square"/></span>
                <span className="vc-title">{t('voteDemo.title')}</span>
                <span className="vc-sub">{t('voteDemo.sub')}</span>
            </div>
            {ROWS.map((row) => (
                <div className="vc-row" key={row.nameKey}>
                    <div className="vc-row-top">
                        <span className="vc-name"><i className={`ph ${row.icon}`}/>{t(row.nameKey)}</span>
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
