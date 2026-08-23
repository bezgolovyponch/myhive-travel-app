'use client';

// Landing footer: wordmark, three link columns and the legal line, all from
// the landing dictionary. The Trips column is in-page anchors (per page);
// Company and legal links go to the locale-prefixed real routes.
import { useT, useLocalePath } from '../../legacy-src/i18n';
import TrivluLogo from './TrivluLogo';
import { PHONE_DISPLAY, PHONE_HREF } from './data';

export interface FooterAnchor {
  href: string;
  labelKey: string; // key inside landing.footer
}

export default function LandingFooter({
  taglineKey,
  tripLinks,
}: {
  taglineKey: 'taglineVote' | 'taglinePrague';
  tripLinks: FooterAnchor[];
}) {
  const t = useT('landing.footer');
  const lp = useLocalePath();

  return (
    <footer className="ftr">
      <div className="shell ftr__in">
        <div>
          <a className="logo" href="#top">
            <TrivluLogo />
          </a>
          <p className="ftr__tag">{t(taglineKey)}</p>
        </div>
        <div>
          <h4>{t('trips')}</h4>
          <ul>
            {tripLinks.map((l) => (
              <li key={l.href}>
                <a href={l.href}>{t(l.labelKey)}</a>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h4>{t('company')}</h4>
          <ul>
            <li>
              <a href={lp('/about')}>{t('about')}</a>
            </li>
            <li>
              <a href={lp('/blog')}>{t('blog')}</a>
            </li>
            <li>
              <a href={lp('/contact')}>{t('contact')}</a>
            </li>
          </ul>
        </div>
        <div>
          <h4>{t('talk')}</h4>
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
        <span>{t('copyright')}</span>
        <span>
          <a href={lp('/terms')}>{t('terms')}</a> ·{' '}
          <a href={lp('/refund-policy')}>{t('refund')}</a> ·{' '}
          <a href={lp('/privacy-policy')}>{t('privacy')}</a> ·{' '}
          <a href={lp('/cookie-policy')}>{t('cookies')}</a>
        </span>
      </div>
    </footer>
  );
}
