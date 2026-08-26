'use client';

// Fixed landing header: logo, phone, WhatsApp, the page's primary CTA (passed
// as children), then the language dropdown (the same one the main homepage
// header uses) sitting immediately left of the cart — matching the main
// header's action-cluster order. Gains a border once the page scrolls
// (is-stuck).
import { useEffect, useState, type ReactNode } from 'react';
import { useT } from '../../legacy-src/i18n';
import TrivluLogo, { PhoneIcon, WhatsAppIcon } from './TrivluLogo';
import LandingLanguageSwitcher from './LandingLanguageSwitcher';
import LandingCart from './LandingCart';
import { PHONE_DISPLAY, PHONE_HREF, WHATSAPP_HREF } from './data';

export default function LandingHeader({
  children,
  destinationSlug,
}: {
  children?: ReactNode;
  /** The cart's Continue button navigates by it, so every landing passes it. */
  destinationSlug: string;
}) {
  const [stuck, setStuck] = useState(false);
  const t = useT('landing.chrome');

  useEffect(() => {
    const onScroll = () => setStuck(window.scrollY > 20);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header className={`hdr${stuck ? ' is-stuck' : ''}`}>
      <div className="hdr__in">
        <a className="logo" href="#top">
          <TrivluLogo />
        </a>
        <span className="hdr__spacer" />
        <a className="hdr__tel" href={PHONE_HREF}>
          <PhoneIcon />
          <span className="hdr__num">{PHONE_DISPLAY}</span>
        </a>
        <a
          className="hdr__wa"
          href={WHATSAPP_HREF}
          target="_blank"
          rel="noopener"
          aria-label={t('waAria')}
        >
          <WhatsAppIcon />
        </a>
        {children}
        <LandingLanguageSwitcher />
        <LandingCart destinationSlug={destinationSlug} />
      </div>
    </header>
  );
}
