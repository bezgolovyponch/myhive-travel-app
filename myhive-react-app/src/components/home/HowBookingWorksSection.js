import {MESSENGER_URL, WHATSAPP_URL} from '../../services/config';
import './HowBookingWorksSection.css';

const BOOKING_STEPS = [
    {icon: '🗳️', title: 'Vote & Confirm', text: 'your group votes on activities via Trip Builder'},
    {icon: '📝', title: 'Tweak the List', text: 'add or remove activities to fit your budget'},
    {icon: '🔒', title: 'Lock It In', text: '30% deposit secures the booking, rest paid closer to the date'},
];

function HowBookingWorksSection() {
    return (
        <section className="how-booking-works">
            <h2 className="section-title">How Booking Works</h2>
            <p className="section-subtitle">Vote, tweak, pay — your stag do decided in 10 minutes.</p>
            <div className="booking-steps">
                {BOOKING_STEPS.map(step => (
                    <div key={step.title} className="booking-step">
                        <span className="booking-step-icon" aria-hidden="true">{step.icon}</span>
                        <h3 className="booking-step-title">{step.title}</h3>
                        <p className="booking-step-text">{step.text}</p>
                    </div>
                ))}
            </div>
            <div className="booking-support">
                <p className="booking-support-text">Got questions? Contact us.</p>
                <div className="booking-support-buttons">
                    <a className="btn btn--primary" href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">
                        WhatsApp
                    </a>
                    <a className="btn btn--primary" href={MESSENGER_URL} target="_blank" rel="noopener noreferrer">
                        Facebook Messenger
                    </a>
                </div>
            </div>
        </section>
    );
}

export default HowBookingWorksSection;
