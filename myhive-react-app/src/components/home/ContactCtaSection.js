import {WHATSAPP_URL} from '../../services/config';
import {pushEvent} from '../../utils/analytics';
import './ContactCtaSection.css';

function ContactCtaSection() {
    return (
        <section className="contact-cta">
            <div className="contact-cta-card">
                <div className="contact-cta-text">
                    <h2 className="contact-cta-title">We're just a message away</h2>
                    <p className="contact-cta-sub">
                        Chat with our team on WhatsApp — ask anything, we'll help plan the perfect stag do.
                    </p>
                    <div>
                        <a
                            className="contact-cta-wa"
                            href={WHATSAPP_URL}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={() => pushEvent('contact_click', {channel: 'whatsapp'})}
                        >
                            <i className="ph ph-whatsapp-logo" aria-hidden="true"/> Chat on WhatsApp
                        </a>
                    </div>
                </div>
                <div className="contact-cta-img" aria-hidden="true"/>
            </div>
        </section>
    );
}

export default ContactCtaSection;
