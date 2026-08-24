import {WHATSAPP_URL} from '../../services/config';
import {pushEvent} from '../../utils/analytics';
import {useT} from '../../i18n';
import './ContactCtaSection.css';

function ContactCtaSection() {
    const t = useT('home');
    return (
        <section className="contact-cta">
            <div className="contact-cta-card">
                <div className="contact-cta-text">
                    <h2 className="contact-cta-title">{t('contactCta.title')}</h2>
                    <p className="contact-cta-sub">
                        {t('contactCta.subtitle')}
                    </p>
                    <div className="contact-cta-wa-wrap">
                        <a
                            className="contact-cta-wa"
                            href={WHATSAPP_URL}
                            target="_blank"
                            rel="noopener noreferrer"
                            onClick={() => pushEvent('contact_click', {channel: 'whatsapp'})}
                        >
                            <i className="ph ph-whatsapp-logo" aria-hidden="true"/> {t('contactCta.whatsappCta')}
                        </a>
                    </div>
                </div>
                <div className="contact-cta-img" aria-hidden="true"/>
            </div>
        </section>
    );
}

export default ContactCtaSection;
