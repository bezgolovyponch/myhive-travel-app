import './TrustBar.css';

const TRUST_ITEMS = [
    {icon: '🏆', title: 'Stag Do Specialists', text: "We've done this thousands of times"},
    {icon: '🗳️', title: 'Group Voted Itinerary', text: 'Built on what your mates actually want'},
    {icon: '✅', title: 'We Handle Everything', text: 'Booking, logistics, support'},
    {icon: '💬', title: 'Real Human Support', text: 'WhatsApp & chat, 7 days a week'},
];

function TrustBar() {
    return (
        <section className="trust-bar">
            {TRUST_ITEMS.map(item => (
                <div key={item.title} className="trust-item">
                    <span className="trust-icon" aria-hidden="true">{item.icon}</span>
                    <h3 className="trust-title">{item.title}</h3>
                    <p className="trust-text">{item.text}</p>
                </div>
            ))}
        </section>
    );
}

export default TrustBar;
