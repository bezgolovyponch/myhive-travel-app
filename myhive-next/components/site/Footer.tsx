// Server-rendered footer mirroring legacy-src/components/Footer.js.
import Link from 'next/link';
import '../../legacy-src/components/Footer.css';

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-content">
        <Link href="/" className="footer-logo">Trivlu</Link>
        <p className="footer-tagline">Turn group travel chaos into epic adventures with zero stress.</p>
        <nav className="footer-nav">
          <a href="/#activities">Activities</a>
          <Link href="/about">About</Link>
          <Link href="/blog">Blog</Link>
          <Link href="/contact">Contact</Link>
        </nav>
      </div>
      <div className="footer-bottom">
        <nav className="footer-legal">
          <Link href="/terms">Terms</Link>
          <Link href="/refund-policy">Refund Policy</Link>
          <Link href="/cookie-policy">Cookie Policy</Link>
          <Link href="/privacy-policy">Privacy Policy</Link>
          {/* CookieYes binds the click handler to .cky-banner-element */}
          <button type="button" className="cky-banner-element">Cookie settings</button>
        </nav>
        <p>&copy; {new Date().getFullYear()} Trivlu. All rights reserved.</p>
      </div>
    </footer>
  );
}
