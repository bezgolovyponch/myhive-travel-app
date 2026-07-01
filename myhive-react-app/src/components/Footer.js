import {Link, useNavigate} from 'react-router-dom';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import './Footer.css';

function Footer() {
    const navigate = useNavigate();

    return (
        <footer className="site-footer">
            <div className="footer-content">
                <Link to="/" className="footer-logo">Trivlu</Link>
                <p className="footer-tagline">Turn group travel chaos into epic adventures with zero stress.</p>

                <nav className="footer-nav">
                    <a href="/#activities" onClick={(e) => { e.preventDefault(); scrollToHomeSection(navigate, 'activities'); }}>Activities</a>
                    <Link to="/about">About</Link>
                    <Link to="/blog">Blog</Link>
                    <Link to="/contact">Contact</Link>
                </nav>
            </div>

            <div className="footer-bottom">
                <nav className="footer-legal">
                    <Link to="/cookie-policy">Cookie Policy</Link>
                    <Link to="/privacy-policy">Privacy Policy</Link>
                    {/* CookieYes binds the click handler to .cky-banner-element to reopen
                        the consent banner — no JS of ours needed. */}
                    <button type="button" className="cky-banner-element">Cookie settings</button>
                </nav>
                <p>&copy; {new Date().getFullYear()} Trivlu. All rights reserved.</p>
            </div>
        </footer>
    );
}

export default Footer;
