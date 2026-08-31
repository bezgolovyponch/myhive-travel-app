'use client';

// "New company. Experienced team" — the story plus the seller card with the
// direct lines. Copy comes from the landing dictionary; the two landings
// differ in paragraph split, the fourth guarantee and the eyebrow.
import { useT } from '../../legacy-src/i18n';
import { PHONE_DISPLAY, PHONE_HREF, WHATSAPP_HREF } from './data';
import { trackCta } from './analytics';

const SELLER_AVATAR = '/landing/martin.webp';

export default function WhyUsSection({ variant }: { variant: 'vote' | 'prague' }) {
  const t = useT('landing.why');
  const paragraphs =
    variant === 'vote' ? [t('p1'), t('p2Vote')] : [t('p1'), t('p2Prague'), t('p3Prague')];
  const guarantees =
    variant === 'vote'
      ? [t('g1'), t('g2'), t('g3')]
      : [t('g1'), t('g2'), t('g3'), t('g4')];

  return (
    <section id="why">
      <div className="shell">
        {variant === 'prague' ? <p className="t-eyebrow">{t('eyebrowPrague')}</p> : null}
        <h2 className="t-h2">{t('title')}</h2>
        <div className="why">
          <div>
            {paragraphs.map((p) => (
              <p key={p}>{p}</p>
            ))}
            <ul className="guar">
              {guarantees.map((g) => (
                <li key={g}>{g}</li>
              ))}
            </ul>
          </div>
          <aside className="seller">
            <div className="seller__top">
              <img className="seller__av" src={SELLER_AVATAR} alt={t('sellerName')} loading="lazy" decoding="async" />
              <div>
                <div className="seller__nm">{t('sellerName')}</div>
                <div className="seller__rl">{t('sellerRole')}</div>
                <div className="seller__live">
                  <span className="pulse" /> {t('sellerLive')}
                </div>
              </div>
            </div>
            <div className="seller__row">
              <a
                className="btn btn--primary"
                href={PHONE_HREF}
                onClick={() => trackCta('Call now', 'why')}
              >
                {t('callNow')}
              </a>
              <a
                className="btn btn--wa"
                href={WHATSAPP_HREF}
                target="_blank"
                rel="noopener"
                onClick={() => trackCta('WhatsApp', 'why')}
              >
                {t('whatsapp')}
              </a>
            </div>
            <p className="mini" style={{ margin: '1rem 0 0', textAlign: 'center' }}>
              {PHONE_DISPLAY}
            </p>
          </aside>
        </div>
      </div>
    </section>
  );
}
