'use client';

// Fixed landing header: logo, language switcher, phone, WhatsApp and the
// page's primary CTA (passed as children). Gains a border once the page
// scrolls (is-stuck). The switcher swaps the locale prefix on the current
// path, mirroring the legacy header's dropdown.
import { useEffect, useState, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { useT, useLocale, splitLocale, localizePath, SUPPORTED_LOCALES } from '../../legacy-src/i18n';
import TrivluLogo, { PhoneIcon, WhatsAppIcon } from './TrivluLogo';
import { PHONE_DISPLAY, PHONE_HREF, WHATSAPP_HREF } from './data';

export default function LandingHeader({ children }: { children?: ReactNode }) {
  const [stuck, setStuck] = useState(false);
  const t = useT('landing.chrome');
  const locale = useLocale();
  const pathname = usePathname() ?? '/';
  const { pathname: bare } = splitLocale(pathname);

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
        <nav className="hdr__lang" aria-label={t('langAria')}>
          {SUPPORTED_LOCALES.map((l: string) => (
            <a
              key={l}
              href={localizePath(bare, l)}
              className={l === locale ? 'is-on' : undefined}
              aria-current={l === locale ? 'page' : undefined}
            >
              {l.toUpperCase()}
            </a>
          ))}
        </nav>
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
      </div>
    </header>
  );
}
