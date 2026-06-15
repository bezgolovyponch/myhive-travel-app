import {Helmet} from 'react-helmet-async';
import {SITE_URL} from '../services/config';
import './PolicyPage.css';

// Placeholder until the Privacy Policy is generated in CookieYes. It will then
// be added here as static content (same approach as the Cookie Policy).
function PrivacyPolicyPage() {
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
                <p className="policy-placeholder">
                    Our Privacy Policy is being finalised and will be published here shortly.
                </p>
            </section>
        </div>
    );
}

export default PrivacyPolicyPage;
