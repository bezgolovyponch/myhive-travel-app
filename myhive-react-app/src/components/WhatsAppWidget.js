import {useLocation} from 'react-router-dom';
import {WHATSAPP_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import './WhatsAppWidget.css';

// Full-screen fixed flows (swipe deck / quiz) where the FAB would sit on top
// of their own fixed controls — hidden entirely rather than offset.
const FULL_SCREEN_ROUTES = [
    /^\/vote\/[^/]+\/activities$/, // participant swipe page
    /^\/vote\/new\/curate$/,       // organizer swipe deck (same UI as above)
    /^\/vote\/new\/quiz$/,         // organizer quiz — fixed full-screen flow
];

// Activity detail pages render a fixed mobile Add-to-Trip bar; the FAB is
// offset above it there so it never covers that primary CTA.
const ADD_BAR_ROUTE = /^\/destination\/[^/]+\/activity\/[^/]+$/;

// Floating "chat with us" FAB.
function WhatsAppWidget() {
    const {pathname} = useLocation();
    if (FULL_SCREEN_ROUTES.some(re => re.test(pathname))) {
        return null;
    }
    const aboveAddBar = ADD_BAR_ROUTE.test(pathname);
    const className = aboveAddBar ? 'whatsapp-widget whatsapp-widget--above-add-bar' : 'whatsapp-widget';
    return (
        <a
            className={className}
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
