import {useEffect} from 'react';
import {Helmet} from 'react-helmet-async';
import {SITE_URL} from '../services/config';
import './PolicyPage.css';

function PrivacyPolicyPage() {
    useEffect(() => {
        // Same pattern as the cookie policy: a GTM tag injects the CookieYes
        // privacy-policy script into #privacy-policy-content. Until that policy is
        // generated in CookieYes and its GTM tag is published, the mount point stays
        // empty and the fallback notice below is shown.
        window.dataLayer = window.dataLayer || [];
        window.dataLayer.push({event: 'privacy_policy_view'});
    }, []);

    return (
        <div className="policy-page">
            <Helmet>
                <title>Privacy Policy — Trivlu</title>
                <meta name="description"
                      content="How Trivlu collects, uses, and protects your personal data."/>
                <link rel="canonical" href={`${SITE_URL}/privacy-policy`}/>
            </Helmet>
            <section className="page-hero">
                <h1>Privacy Policy</h1>
            </section>
            <section className="policy-section">
                <div id="privacy-policy-content">
                    <p className="policy-placeholder">
                        Our Privacy Policy is being finalised and will be published here shortly.
                    </p>
                </div>
            </section>
        </div>
    );
}

export default PrivacyPolicyPage;
