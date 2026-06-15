import {useEffect} from 'react';
import {Helmet} from 'react-helmet-async';
import {SITE_URL} from '../services/config';
import './PolicyPage.css';

function CookiePolicyPage() {
    useEffect(() => {
        // The CookieYes cookie-policy script is NOT bundled in this repo. A GTM tag
        // injects it into #cookie-policy-content; this generic dataLayer event tells
        // GTM the mount point is in the DOM (it has already rendered by the time this
        // effect runs).
        window.dataLayer = window.dataLayer || [];
        window.dataLayer.push({event: 'cookie_policy_view'});
    }, []);

    return (
        <div className="policy-page">
            <Helmet>
                <title>Cookie Policy — Trivlu</title>
                <meta name="description"
                      content="Learn how Trivlu uses cookies and how to manage your cookie preferences."/>
                <link rel="canonical" href={`${SITE_URL}/cookie-policy`}/>
            </Helmet>
            <section className="page-hero">
                <h1>Cookie Policy</h1>
            </section>
            <section className="policy-section">
                <div id="cookie-policy-content"/>
            </section>
        </div>
    );
}

export default CookiePolicyPage;
