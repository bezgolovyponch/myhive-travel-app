'use client';

// The Prague landing at /landing/prague (mockup
// fixes/trivlu-landing-2-prague-desktop-sticky-right-v74.html): "Prague is
// ninety minutes away and four times cheaper". All copy comes from the landing
// dictionary (en/de); cta_click labels stay English so analytics never
// fragments by locale.
import './landing.css';
import './prague.css';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { inter } from './fonts';
import LandingHeader from './LandingHeader';
import LandingFooter from './LandingFooter';
import ActivityRows from './ActivityRows';
import TripCalculator from './TripCalculator';
import WhyUsSection from './WhyUsSection';
import ReviewsSection from './ReviewsSection';
import FaqSection from './FaqSection';
import { trackCta, trackCtaAndGo } from './analytics';
import { builderUrl, PHONE_DISPLAY, PHONE_HREF, type ActivityRow } from './data';
import { useLandingCart } from './useLandingCart';
import type { Pool } from './engine';

const TILE_IMAGES = [
  {
    key: 't1',
    src: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/wallet.webp',
    w: 480,
    h: 336,
  },
  {
    key: 't2',
    src: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/plane.webp',
    w: 480,
    h: 317,
  },
  {
    key: 't3',
    src: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/tank.webp',
    w: 480,
    h: 331,
  },
  {
    key: 't4',
    src: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/map.webp',
    w: 480,
    h: 322,
  },
] as const;

export default function PragueLanding({
  rows,
  pool,
  totalActivities,
  destinationSlug,
}: {
  rows: ActivityRow[];
  pool: Pool;
  totalActivities: number;
  destinationSlug: string;
}) {
  const t = useT('landing.prague');
  const tAct = useT('landing.activities');
  const tCalc = useT('landing.calc');
  const tChrome = useT('landing.chrome');
  const lp = useLocalePath();
  const builderHref = lp(builderUrl(destinationSlug));
  const cart = useLandingCart(
    destinationSlug,
    rows.flatMap((r) => r.items),
  );

  // Same first step as the main site: ask travelers/dates before the builder.
  // The modal (LandingCart mounts it) continues from there — to the cart panel
  // when there is a shortlist, to the builder when there is not.
  const builderLink = (block: string, label: string, className: string) => (
    <a
      className={className}
      href={builderHref}
      onClick={(e) => {
        e.preventDefault();
        trackCta(label, block);
        cart.openSetup();
      }}
    >
      {tChrome('buildTrip')}
    </a>
  );

  return (
    <div className={`tl tl--prague ${inter.variable}`} id="top">
      <LandingHeader destinationSlug={destinationSlug}>
        {builderLink('header', 'Build your trip', 'btn btn--primary')}
      </LandingHeader>

      {/* ══════════ HERO ══════════ */}
      <section className="hero">
        <div className="hero__bg" aria-hidden="true" />
        <div className="hero__veil" aria-hidden="true" />
        <div className="hero__in">
          <p className="hero__eyebrow">{t('hero.eyebrow')}</p>
          <h1 className="hero__title">
            {t('hero.tPre')}
            <em>{t('hero.tEm1')}</em>
            {t('hero.tMid')}
            <em>{t('hero.tEm2')}</em>
          </h1>
          <p className="hero__sub">{t('hero.sub')}</p>
          <div className="hero__cta">
            {builderLink('hero', 'Build your trip', 'btn btn--primary btn--lg')}
            <a
              className="btn btn--ghost btn--lg"
              href="#activities"
              onClick={() => trackCta('Explore Activities', 'hero')}
            >
              {t('hero.ctaExplore')}
            </a>
          </div>
          <div className="hero__pf">
            {(['pf1', 'pf2', 'pf3', 'pf4'] as const).map((key) => (
              <div className="pf__i" key={key}>
                <b>{t(`hero.${key}b`)}</b>
                <span>{t(`hero.${key}t`)}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ══════════ ACTIVITIES ══════════ */}
      <section id="activities">
        <div className="shell">
          <p className="t-eyebrow">{tAct('eyebrow')}</p>
          <h2 className="t-h2">{tAct('title', { count: totalActivities })}</h2>
          <p className="t-lede">{tAct('ledePrague')}</p>
          <ActivityRows
            rows={rows}
            destinationSlug={destinationSlug}
            showChip
            picked={cart.picked}
            onToggle={cart.toggle}
          />
          <div className="grid__more">
            <a
              className="btn btn--ghost btn--lg"
              href={lp(`/destination/${destinationSlug}?tab=activities`)}
              onClick={() => trackCta('View all activities', 'activities')}
            >
              {tAct('viewAll', { count: totalActivities })}
            </a>
          </div>
        </div>
      </section>

      <hr className="rule" />

      {/* ══════════ THE ARGUMENT ══════════ */}
      <section className="args" id="why-prague">
        <div className="shell args__hd">
          <h2 className="t-h2">{t('tiles.title')}</h2>
        </div>
        <div className="shell tiles">
          {TILE_IMAGES.map(({ key, src, w, h }) => (
            <article className="tile" key={key}>
              <h3 className="tile__h">{t(`tiles.${key}h`)}</h3>
              <img
                className="tile__ic"
                src={src}
                alt={t(`tiles.${key}alt`)}
                width={w}
                height={h}
                loading="lazy"
                decoding="async"
              />
              <p className="tile__n">
                <b>{t(`tiles.${key}b`)}</b>
                {t(`tiles.${key}mid`)}
                <em>{t(`tiles.${key}em`)}</em>
              </p>
            </article>
          ))}
        </div>
      </section>

      <figure className="band-img">
        <img
          alt={t('bandImg.alt')}
          decoding="async"
          loading="lazy"
          src="https://wsrv.nl/?url=https%3A%2F%2Fimages.unsplash.com%2Fphoto-1772202950305-2e6fee0b1ab3%3Ffm%3Djpg%26q%3D62%26w%3D2000%26auto%3Dformat%26fit%3Dcrop&w=1600&output=webp&q=76"
        />
        <figcaption>
          {t('bandImg.caption')}
          <small>{t('bandImg.small')}</small>
          <span className="band-img__cta">
            {builderLink('band', 'Build your trip', 'btn btn--primary')}
          </span>
        </figcaption>
      </figure>

      {/* ══════════ COSTS ══════════ */}
      <section id="costs">
        <div className="shell">
          <p className="t-eyebrow">{tCalc('eyebrow')}</p>
          <h2 className="t-h2">{tCalc('title')}</h2>
          <p className="t-lede">{tCalc('lede')}</p>
          <TripCalculator pool={pool} destinationSlug={destinationSlug} />
        </div>
      </section>

      <hr className="rule" />

      <WhyUsSection variant="prague" />

      <hr className="rule" />

      <ReviewsSection variant="prague" />

      <hr className="rule" />

      <FaqSection variant="prague" />

      {/* ══════════ THE 2026 RULES ══════════ */}
      <section className="rules" id="rules">
        <div className="shell">
          <p className="t-eyebrow">{t('rules.eyebrow')}</p>
          <h2 className="t-h2">{t('rules.title')}</h2>
          <p className="t-lede">{t('rules.lede')}</p>
          <div className="rules__grid">
            <div className="rules__card">
              <span className="rules__badge rules__badge--myth">{t('rules.mythBadge')}</span>
              <h4>{t('rules.mythH')}</h4>
              <p>{t('rules.mythP')}</p>
            </div>
            <div className="rules__card">
              <span className="rules__badge rules__badge--fact">{t('rules.factBadge')}</span>
              <h4>{t('rules.factH')}</h4>
              <p>{t('rules.factP1')}</p>
              <p>{t('rules.factP2')}</p>
            </div>
          </div>
          <ul className="rules__list">
            {(['l1', 'l2', 'l3', 'l4'] as const).map((key) => (
              <li key={key}>
                <b>{t(`rules.${key}b`)}</b>
                {t(`rules.${key}t`)}
              </li>
            ))}
          </ul>
          <div className="note">
            <b>{t('rules.noteB')}</b>
            {t('rules.noteT')}
          </div>
        </div>
      </section>

      {/* ══════════ FINAL ══════════ */}
      <section className="final">
        <div className="shell">
          <h2>{t('final.title')}</h2>
          <p>{t('final.p')}</p>
          <div className="final__row">
            {builderLink('final', 'Build your trip', 'btn btn--primary btn--lg')}
            <a
              className="btn btn--ghost btn--lg"
              href={PHONE_HREF}
              onClick={() => trackCta('Call', 'final')}
            >
              📞 {PHONE_DISPLAY}
            </a>
          </div>
          <p className="final__fine">{t('final.fine')}</p>
        </div>
      </section>

      <LandingFooter
        taglineKey="taglinePrague"
        tripLinks={[
          { href: '#activities', labelKey: 'activities' },
          { href: '#costs', labelKey: 'exampleWeekend' },
          { href: '#rules', labelKey: 'rules' },
        ]}
      />
    </div>
  );
}
