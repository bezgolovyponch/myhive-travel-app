import {Link, useNavigate} from 'react-router-dom';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import './Footer.css';

function Footer() {
    const navigate = useNavigate();

    return (
        <footer className="site-footer">
            <div className="footer-content">
                <div className="footer-brand">
                    <Link to="/" className="footer-logo">Trivlu</Link>
                    <p className="footer-tagline">Turn group travel chaos into epic adventures with zero stress.</p>
                </div>

                <div className="footer-nav-group">
                    <nav className="footer-nav">
                        <span className="footer-nav-title">Explore</span>
                        <a href="/#activities" onClick={(e) => { e.preventDefault(); scrollToHomeSection(navigate, 'activities'); }}>Activities</a>
                        <Link to="/about">About</Link>
                        <Link to="/contact">Contact</Link>
                    </nav>
                    <nav className="footer-nav">
                        <span className="footer-nav-title">Connect</span>
                        <a href="https://instagram.com" target="_blank" rel="noopener noreferrer">Instagram</a>
                        <a href="https://twitter.com" target="_blank" rel="noopener noreferrer">Twitter</a>
                        <a href="https://facebook.com" target="_blank" rel="noopener noreferrer">Facebook</a>
                    </nav>
                </div>
            </div>

            <div className="footer-bottom">
                <p>&copy; {new Date().getFullYear()} Trivlu. All rights reserved.</p>
            </div>
        </footer>
    );
}

export default Footer;
