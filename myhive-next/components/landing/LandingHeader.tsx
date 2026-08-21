'use client';

// Fixed landing header: logo, phone, WhatsApp and the page's primary CTA.
// The home variant adds the shortlist cart; the Prague variant links straight
// to the trip builder. Gains a border once the page scrolls (is-stuck).
import { useEffect, useState, type ReactNode } from 'react';
import TrivluLogo, { PhoneIcon, WhatsAppIcon } from './TrivluLogo';
import { PHONE_DISPLAY, PHONE_HREF, WHATSAPP_HREF } from './data';

export default function LandingHeader({ children }: { children?: ReactNode }) {
  const [stuck, setStuck] = useState(false);

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
          aria-label="Message us on WhatsApp"
        >
          <WhatsAppIcon />
        </a>
        {children}
      </div>
    </header>
  );
}
