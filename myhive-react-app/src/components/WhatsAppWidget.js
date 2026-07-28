import {useEffect, useRef, useState} from 'react';
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

const GREETING = 'Ready to give the groom the ultimate send off? 🌍 Send me the '
    + 'dates or destination you’re thinking of, and I’ll send over a '
    + 'personalized quote right away! 👇';

// Floating "chat with us" FAB that opens a WhatsApp-style chat teaser; the
// send button hands the typed message over to the real WhatsApp conversation.
function WhatsAppWidget() {
    const {pathname} = useLocation();
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState('');
    const chatRef = useRef(null);

    // Mobile keyboards shrink only the *visual* viewport — cap the chat card
    // to it so the header is never pushed off the top of the screen while
    // typing (the greeting scrolls inside instead).
    useEffect(() => {
        const vv = window.visualViewport;
        if (!open || !vv) return undefined;
        const apply = () => {
            if (chatRef.current) {
                chatRef.current.style.maxHeight = `${Math.max(220, vv.height - 96)}px`;
            }
        };
        apply();
        vv.addEventListener('resize', apply);
        return () => vv.removeEventListener('resize', apply);
    }, [open]);

    if (FULL_SCREEN_ROUTES.some(re => re.test(pathname))) {
        return null;
    }
    const aboveAddBar = ADD_BAR_ROUTE.test(pathname);
    const className = [
        'whatsapp-widget',
        aboveAddBar && 'whatsapp-widget--above-add-bar',
        open && 'whatsapp-widget--open',
    ].filter(Boolean).join(' ');

    const handleToggle = () => {
        if (!open) {
            pushEvent('cta_click', {cta_label: 'whatsapp_widget', page: pathname});
        }
        setOpen(o => !o);
    };

    const handleSend = () => {
        const text = message.trim();
        const url = text ? `${WHATSAPP_URL}?text=${encodeURIComponent(text)}` : WHATSAPP_URL;
        pushEvent('cta_click', {cta_label: 'whatsapp_widget_send', page: pathname});
        window.open(url, '_blank', 'noopener,noreferrer');
        setMessage('');
        setOpen(false);
    };

    return (
        <div className={className}>
            {open && (
                <div className="wa-chat" role="dialog" aria-label="Chat with us on WhatsApp" ref={chatRef}>
                    <div className="wa-chat-header">
                        <div className="wa-chat-avatar" aria-hidden="true">
                            M
                            <span className="wa-chat-online"/>
                        </div>
                        <div className="wa-chat-identity">
                            <strong>Maria</strong>
                            <span>Typically replies instantly</span>
                        </div>
                        <button
                            type="button"
                            className="wa-chat-close"
                            onClick={() => setOpen(false)}
                            aria-label="Close chat"
                        >×</button>
                    </div>
                    <div className="wa-chat-body">
                        <div className="wa-chat-bubble">{GREETING}</div>
                    </div>
                    <div className="wa-chat-input-row">
                        <input
                            type="text"
                            value={message}
                            placeholder="Type your destination or dates here…"
                            onChange={e => setMessage(e.target.value)}
                            onKeyDown={e => {
                                if (e.key === 'Enter') handleSend();
                            }}
                        />
                        <button
                            type="button"
                            className="wa-chat-send"
                            onClick={handleSend}
                            aria-label="Send on WhatsApp"
                        >
                            <i className="ph ph-paper-plane-right" aria-hidden="true"/>
                        </button>
                    </div>
                </div>
            )}
            <button
                type="button"
                className="whatsapp-fab"
                onClick={handleToggle}
                aria-expanded={open}
                aria-label="Chat with us on WhatsApp"
            >
                <i className="ph ph-whatsapp-logo" aria-hidden="true"/>
            </button>
        </div>
    );
}

export default WhatsAppWidget;
