import './TrustBar.css';

const TRUST_ITEMS = [
    {icon: 'ph-certificate', title: 'Stag Do Specialists', text: "We've done this thousands of times"},
    {icon: 'ph-list-heart', title: 'Group-Voted Plan', text: 'Built on what your mates actually want'},
    {icon: 'ph-kanban', title: 'We Handle Everything', text: 'Booking, logistics, support'},
    {icon: 'ph-headset', title: 'Real Human Support', text: 'WhatsApp & chat, 7 days a week'},
];

function TrustBar() {
    return (
        <section className="trust-bar">
            <h2 className="trust-bar-title">Why Plan Your Prague Stag Weekend with Trivlu</h2>
            <div className="trust-bar-grid">
                {TRUST_ITEMS.map(item => (
                    <div key={item.title} className="trust-item">
                        <span className="trust-icon" aria-hidden="true">
                            <i className={`ph ${item.icon}`}/>
                        </span>
                        <h3 className="trust-title">{item.title}</h3>
                        <p className="trust-text">{item.text}</p>
                    </div>
                ))}
            </div>
        </section>
    );
}

export default TrustBar;
