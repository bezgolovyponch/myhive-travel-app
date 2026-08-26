import {Link, useNavigate} from 'react-router-dom';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import {useLocalePath, useT} from '../i18n';
import './Footer.css';

function Footer() {
    const navigate = useNavigate();
    const t = useT('footer');
    const lp = useLocalePath();

    return (
        <footer className="site-footer">
            <div className="footer-content">
                <Link to="/" className="footer-logo">Trivlu</Link>
                <p className="footer-tagline">{t('tagline')}</p>

                <nav className="footer-nav">
                    <a href={lp('/') + '#activities'} onClick={(e) => { e.preventDefault(); scrollToHomeSection(navigate, 'activities'); }}>{t('nav.activities')}</a>
                    <Link to="/about">{t('nav.about')}</Link>
                    <Link to="/blog">{t('nav.blog')}</Link>
                    <Link to="/contact">{t('nav.contact')}</Link>
                </nav>
            </div>

            <div className="footer-bottom">
                <nav className="footer-legal">
                    <Link to="/terms">{t('legal.terms')}</Link>
                    <Link to="/refund-policy">{t('legal.refundPolicy')}</Link>
                    <Link to="/cookie-policy">{t('legal.cookiePolicy')}</Link>
                    <Link to="/privacy-policy">{t('legal.privacyPolicy')}</Link>
                    {/* CookieScript binds the click handler to .csconsentlink to reopen
                        the consent banner — no JS of ours needed. */}
                    <button type="button" className="csconsentlink">{t('legal.cookieSettings')}</button>
                </nav>
                <p>&copy; {new Date().getFullYear()} {t('copyright')}</p>
            </div>
        </footer>
    );
}

export default Footer;
