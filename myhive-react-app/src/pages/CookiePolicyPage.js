import PageHead from '../components/PageHead';
import {SITE_URL} from '../services/config';
import './PolicyPage.css';

// Static Cookie Policy content generated with CookieYes. Kept as plain content
// (not the CookieYes embed script, which only renders when present in the page
// at parse time) so it renders reliably on this SPA route. The cookie list is
// maintained manually; the "Consent Preferences" control reuses CookieYes'
// .cky-banner-element hook to reopen the consent banner.
function CookiePolicyPage() {
    return (
        <div className="policy-page">
            <PageHead>
                <title>Cookie Policy | Trivlu</title>
                <meta name="description"
                      content="Learn how Trivlu uses cookies and how to manage your cookie preferences."/>
                <link rel="canonical" href={`${SITE_URL}/cookie-policy`}/>
            </PageHead>
            <section className="page-hero">
                <h1>Cookie Policy</h1>
            </section>
            <section className="policy-section">
                <p className="policy-dates">
                    Effective date: June 15, 2026<br/>
                    Last updated: June 15, 2026
                </p>

                <h2>What are cookies?</h2>
                <p>
                    This Cookie Policy explains what cookies are, how we use them, the types of cookies we
                    use (i.e., the information we collect using cookies and how that information is used), and
                    how to manage your cookie settings.
                </p>
                <p>
                    Cookies are small text files used to store small pieces of information. They are stored on
                    your device when a website loads in your browser. These cookies help ensure that the
                    website functions properly, enhance security, provide a better user experience, and analyse
                    performance to identify what works and where improvements are needed.
                </p>

                <h2>How do we use cookies?</h2>
                <p>
                    Like most online services, our website uses both first-party and third-party cookies for
                    various purposes. First-party cookies are primarily necessary for the website to function
                    properly and do not collect any personally identifiable data.
                </p>
                <p>
                    The third-party cookies used on our website primarily help us understand how the website
                    performs, track how you interact with it, keep our services secure, deliver relevant
                    advertisements, and enhance your overall user experience while improving the speed of your
                    future interactions with our website.
                </p>

                <h2>Types of cookies we use</h2>
                <p>
                    A detailed, categorised list of the cookies used on this site will be published in this
                    section. You can review and change which categories you allow at any time using the
                    "Consent Preferences" option below.
                </p>

                <h2>Manage cookie preferences</h2>
                <p>
                    <button type="button" className="cky-banner-element policy-consent-link">
                        Consent Preferences
                    </button>
                </p>
                <p>
                    You can modify your cookie settings anytime by clicking the "Consent Preferences" button
                    above. This will allow you to revisit the cookie consent banner and update your preferences
                    or withdraw your consent immediately.
                </p>
                <p>
                    Additionally, different browsers offer various methods to block and delete cookies used by
                    websites. You can adjust your browser settings to block or delete cookies. Below are links
                    to support documents on how to manage and delete cookies in major web browsers:
                </p>
                <ul>
                    <li>
                        Chrome:{' '}
                        <a href="https://support.google.com/accounts/answer/32050"
                           target="_blank" rel="noopener noreferrer">
                            support.google.com/accounts/answer/32050
                        </a>
                    </li>
                    <li>
                        Safari:{' '}
                        <a href="https://support.apple.com/en-in/guide/safari/sfri11471/mac"
                           target="_blank" rel="noopener noreferrer">
                            support.apple.com/en-in/guide/safari/sfri11471/mac
                        </a>
                    </li>
                    <li>
                        Firefox:{' '}
                        <a href="https://support.mozilla.org/en-US/kb/clear-cookies-and-site-data-firefox"
                           target="_blank" rel="noopener noreferrer">
                            support.mozilla.org/en-US/kb/clear-cookies-and-site-data-firefox
                        </a>
                    </li>
                    <li>
                        Microsoft Edge:{' '}
                        <a href="https://support.microsoft.com/en-us/microsoft-edge/delete-cookies-in-microsoft-edge-63947406-40ac-c3b8-57b9-2a946a29ae09"
                           target="_blank" rel="noopener noreferrer">
                            support.microsoft.com (Microsoft Edge)
                        </a>
                    </li>
                </ul>
                <p>
                    If you are using a different web browser, please refer to its official support
                    documentation.
                </p>
            </section>
        </div>
    );
}

export default CookiePolicyPage;
