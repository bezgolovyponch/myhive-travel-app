// Landing footer: wordmark, three link columns and the legal line. The Trips
// column is in-page anchors (per page); Company and legal links go to the real
// routes rather than the mockups' placeholder "#".
import TrivluLogo from './TrivluLogo';
import { PHONE_DISPLAY, PHONE_HREF } from './data';

export interface FooterAnchor {
  href: string;
  label: string;
}

export default function LandingFooter({
  tagline,
  tripLinks,
}: {
  tagline: string;
  tripLinks: FooterAnchor[];
}) {
  return (
    <footer className="ftr">
      <div className="shell ftr__in">
        <div>
          <a className="logo" href="#top">
            <TrivluLogo />
          </a>
          <p className="ftr__tag">{tagline}</p>
        </div>
        <div>
          <h4>Trips</h4>
          <ul>
            {tripLinks.map((l) => (
              <li key={l.href}>
                <a href={l.href}>{l.label}</a>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h4>Company</h4>
          <ul>
            <li>
              <a href="/about">About</a>
            </li>
            <li>
              <a href="/blog">Blog</a>
            </li>
            <li>
              <a href="/contact">Contact</a>
            </li>
          </ul>
        </div>
        <div>
          <h4>Talk to a person</h4>
          <ul>
            <li>
              <a href={PHONE_HREF}>{PHONE_DISPLAY}</a>
            </li>
            <li>
              <a href="https://wa.me/420795518597" target="_blank" rel="noopener">
                WhatsApp
              </a>
            </li>
            <li>
              <a href="mailto:hello@trivlu.com">hello@trivlu.com</a>
            </li>
          </ul>
        </div>
      </div>
      <div className="shell ftr__bot">
        <span>© 2026 Trivlu · Prague, Czechia</span>
        <span>
          <a href="/terms">Terms</a> · <a href="/refund-policy">Refund policy</a> ·{' '}
          <a href="/privacy-policy">Privacy</a> · <a href="/cookie-policy">Cookies</a>
        </span>
      </div>
    </footer>
  );
}
