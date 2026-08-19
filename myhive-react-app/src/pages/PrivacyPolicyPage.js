import PageHead from '../components/PageHead';
import {Link} from 'react-router-dom';
import {SITE_URL} from '../services/config';
import {COMPANY, POLICY_EFFECTIVE_DATE} from '../legal/companyInfo';
import './PolicyPage.css';

// Static, GDPR-oriented Privacy Policy. This is a good-faith draft covering the
// data actually processed by the app (bookings, contact form, payments, consent-
// gated analytics); have it reviewed by a qualified lawyer before relying on it.
function PrivacyPolicyPage() {
    return (
        <div className="policy-page">
            <PageHead>
                <title>Privacy Policy | Trivlu</title>
                <meta name="description"
                      content="How Trivlu collects, uses, and protects your personal data under the GDPR."/>
                <link rel="canonical" href={`${SITE_URL}/privacy-policy`}/>
            </PageHead>
            <section className="page-hero">
                <h1>Privacy Policy</h1>
            </section>
            <section className="policy-section">
                <p className="policy-dates">
                    Effective date: {POLICY_EFFECTIVE_DATE}<br/>
                    Last updated: {POLICY_EFFECTIVE_DATE}
                </p>

                <p>
                    This Privacy Policy explains how {COMPANY.legalName} ("{COMPANY.tradeName}", "we", "us")
                    collects, uses, and protects your personal data when you use our website and book
                    experiences through us. We are the data controller for the personal data described below.
                </p>

                <h2>Who we are</h2>
                <p>
                    {COMPANY.legalName}, a company registered in the Czech Republic
                    (company ID {COMPANY.companyId}, {COMPANY.registration}), with its registered office at{' '}
                    {COMPANY.address}. For any privacy questions, contact us at{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>.
                </p>

                <h2>What data we collect</h2>
                <ul>
                    <li>
                        <strong>Booking data</strong> — your name, email address, the activities or packages you
                        book, travel dates, group size, and any notes you provide.
                    </li>
                    <li>
                        <strong>Contact-form data</strong> — the name, email address, and message you submit
                        when you contact us.
                    </li>
                    <li>
                        <strong>Payment data</strong> — payments are processed by Stripe. We do not receive or
                        store your full card number; we retain only a transaction reference and payment status.
                    </li>
                    <li>
                        <strong>Technical &amp; usage data</strong> — IP address, device and browser
                        information, and how you interact with the site. Analytics data is collected only where
                        you consent (see our{' '}
                        <Link to="/cookie-policy">Cookie Policy</Link>).
                    </li>
                </ul>

                <h2>How we use your data and our legal bases</h2>
                <ul>
                    <li>
                        To take and fulfil your booking and provide customer support —{' '}
                        <em>performance of a contract</em> (Art. 6(1)(b) GDPR).
                    </li>
                    <li>
                        To respond to your enquiries via the contact form —{' '}
                        <em>our legitimate interest</em> in answering you (Art. 6(1)(f) GDPR).
                    </li>
                    <li>
                        To process payments and prevent fraud —{' '}
                        <em>performance of a contract</em> and <em>legal obligation</em>.
                    </li>
                    <li>
                        To keep records required by tax and accounting law —{' '}
                        <em>legal obligation</em> (Art. 6(1)(c) GDPR).
                    </li>
                    <li>
                        To measure and improve the site through analytics and marketing —{' '}
                        <em>your consent</em> (Art. 6(1)(a) GDPR), which you can withdraw at any time.
                    </li>
                </ul>

                <h2>Who we share your data with</h2>
                <p>
                    We share personal data only with service providers ("processors") that help us operate the
                    service, under contracts that require them to protect your data:
                </p>
                <ul>
                    <li><strong>Stripe</strong> — payment processing.</li>
                    <li><strong>Render</strong> — application hosting and database (EU region).</li>
                    <li><strong>Cloudflare</strong> — content delivery, security, and image storage (R2).</li>
                    <li><strong>Resend</strong> — sending transactional email (e.g. booking confirmations).</li>
                    <li><strong>Zoho Mail</strong> — receiving and handling email you send us (EU region).</li>
                    <li>
                        <strong>Google (Analytics / Tag Manager)</strong> and <strong>Microsoft Clarity</strong>{' '}
                        — website analytics, used only with your consent.
                    </li>
                    <li>
                        The <strong>local activity partners</strong> needed to deliver a booking you have made,
                        limited to what they need to provide the activity.
                    </li>
                </ul>
                <p>
                    We do not sell your personal data. We may also disclose data where required by law or to
                    establish, exercise, or defend legal claims.
                </p>

                <h2>International transfers</h2>
                <p>
                    Where a provider processes data outside the European Economic Area, the transfer is
                    safeguarded by an adequacy decision or the European Commission's Standard Contractual
                    Clauses.
                </p>

                <h2>How long we keep your data</h2>
                <p>
                    We keep booking and payment records for as long as needed to provide the service and to
                    meet legal, tax, and accounting obligations (generally up to 10 years for accounting
                    records under Czech law). Contact-form messages are kept only as long as needed to handle
                    your enquiry. Consent-based analytics data is retained for the period stated in our{' '}
                    <Link to="/cookie-policy">Cookie Policy</Link>.
                </p>

                <h2>Your rights</h2>
                <p>
                    Under the GDPR you have the right to access, rectify, or erase your data; to restrict or
                    object to processing; to data portability; and to withdraw consent at any time without
                    affecting processing already carried out. To exercise any of these rights, email{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a>. You also have the
                    right to lodge a complaint with the Czech Office for Personal Data Protection (Úřad pro
                    ochranu osobních údajů,{' '}
                    <a href="https://uoou.gov.cz" target="_blank" rel="noopener noreferrer">uoou.gov.cz</a>) or
                    your local supervisory authority.
                </p>

                <h2>Cookies</h2>
                <p>
                    We use cookies and similar technologies as described in our{' '}
                    <Link to="/cookie-policy">Cookie Policy</Link>. Non-essential cookies are set only with
                    your consent, which you can change or withdraw at any time.
                </p>

                <h2>Changes to this policy</h2>
                <p>
                    We may update this Privacy Policy from time to time. The "Last updated" date above shows
                    when it was last changed, and the current version always applies.
                </p>

                <h2>Contact us</h2>
                <p>
                    For any question about this policy or your personal data, email{' '}
                    <a href={`mailto:${COMPANY.contactEmail}`}>{COMPANY.contactEmail}</a> or write to us at{' '}
                    {COMPANY.address}.
                </p>
            </section>
        </div>
    );
}

export default PrivacyPolicyPage;
