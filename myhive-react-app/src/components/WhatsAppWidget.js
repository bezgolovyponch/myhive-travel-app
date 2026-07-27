import {useLocation} from 'react-router-dom';
import {WHATSAPP_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import './WhatsAppWidget.css';

// Floating "chat with us" FAB. Hidden on the participant swipe page, where a
// fixed control would sit on top of the swipe buttons.
function WhatsAppWidget() {
    const {pathname} = useLocation();
    if (/^\/vote\/[^/]+\/activities$/.test(pathname)) {
        return null;
    }
    return (
        <a
            className="whatsapp-widget"
            href={WHATSAPP_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Chat with us on WhatsApp"
            onClick={() => pushEvent('cta_click', {cta_label: 'whatsapp_widget', page: pathname})}
        >
            <i className="ph ph-whatsapp-logo" aria-hidden="true"/>
        </a>
    );
}

export default WhatsAppWidget;
