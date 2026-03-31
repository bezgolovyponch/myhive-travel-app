import {useEffect, useState} from 'react';
import './CookieConsent.css';

function CookieConsent() {
    const [visible, setVisible] = useState(false);

    useEffect(() => {
        const consent = localStorage.getItem('cookieConsent');
        if (!consent) {
            setVisible(true);
        }
    }, []);

    const handleAccept = () => {
        localStorage.setItem('cookieConsent', 'accepted');
        setVisible(false);
    };

    const handleDecline = () => {
        localStorage.setItem('cookieConsent', 'declined');
        setVisible(false);
    };

    if (!visible) return null;

    return (
        <div className="cookie-banner">
            <div className="cookie-content">
                <p>
                    We use cookies to enhance your browsing experience and analyze site traffic.
                    By clicking "Accept", you consent to our use of cookies.
                </p>
                <div className="cookie-actions">
                    <button className="cookie-btn cookie-btn--decline" onClick={handleDecline}>
                        Decline
                    </button>
                    <button className="cookie-btn cookie-btn--accept" onClick={handleAccept}>
                        Accept
                    </button>
                </div>
            </div>
        </div>
    );
}

export default CookieConsent;
