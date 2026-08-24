import {useT} from '../../i18n';
import './TrustBar.css';

const TRUST_ITEMS = [
    {icon: 'ph-certificate', titleKey: 'trust.item1Title', textKey: 'trust.item1Text'},
    {icon: 'ph-list-heart', titleKey: 'trust.item2Title', textKey: 'trust.item2Text'},
    {icon: 'ph-kanban', titleKey: 'trust.item3Title', textKey: 'trust.item3Text'},
    {icon: 'ph-headset', titleKey: 'trust.item4Title', textKey: 'trust.item4Text'},
];

function TrustBar() {
    const t = useT('home');
    return (
        <section className="trust-bar">
            <h2 className="trust-bar-title">{t('trust.heading')}</h2>
            <div className="trust-bar-grid">
                {TRUST_ITEMS.map(item => (
                    <div key={item.titleKey} className="trust-item">
                        <span className="trust-icon" aria-hidden="true">
                            <i className={`ph ${item.icon}`}/>
                        </span>
                        <h3 className="trust-title">{t(item.titleKey)}</h3>
                        <p className="trust-text">{t(item.textKey)}</p>
                    </div>
                ))}
            </div>
        </section>
    );
}

export default TrustBar;
