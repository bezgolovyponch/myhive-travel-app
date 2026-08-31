'use client';

// The voting landing at /landing/vote (mockup
// fixes/trivlu-landing-1-voting-v58.html): "Skip chatting. Start voting."
// One picked list — fed by the hero swipe deck and the catalogue's Add-to-trip
// buttons — drives the header cart, step 1's done flag, the sticky bar and the
// final CTA. All copy comes from the landing dictionary (en/de); cta_click
// labels stay English so analytics never fragments by locale.
import { useMemo, useReducer } from 'react';
import './landing.css';
import './home.css';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { useTrip } from '../../legacy-src/context/TripContext';
import { inter } from './fonts';
import LandingHeader from './LandingHeader';
import LandingCart from './LandingCart';
import LandingFooter from './LandingFooter';
import TripSetupModal from '../../legacy-src/components/TripSetupModal';
import TrivluLogo from './TrivluLogo';
import ActivityRows from './ActivityRows';
import TripCalculator from './TripCalculator';
import WhyUsSection from './WhyUsSection';
import ReviewsSection from './ReviewsSection';
import FaqSection from './FaqSection';
import SwipeDeck from './SwipeDeck';
import { deckReducer, initialDeck, type DeckAction } from './deck';
import { trackCta, trackCtaAndGo } from './analytics';
import {
  builderUrlWithPicks,
  toCartItem,
  PHONE_DISPLAY,
  PHONE_HREF,
  type ActivityRow,
  type LandingActivity,
} from './data';
import type { Pool } from './engine';
import { VOTE_FLOW_PATH } from '../../lib/routes';

// The three drawn numerals: same 90×120 box, monoline skeleton, hairline only.
const STEP_FIGURES = [
  'M30.2 58.8 48.0 42.4 48.0 100.0 48.2 101.6 48.6 103.1 49.7 104.9 51.1 106.3 52.5 107.2 54.4 107.8 56.0 108.0 58.1 107.7 60.0 106.9 61.7 105.7 62.9 104.0 63.7 102.1 64.0 100.0 64.0 23.5 63.7 21.9 63.2 20.4 62.3 19.1 60.8 17.6 58.9 16.6 56.4 16.0 53.7 16.3 51.4 17.5 20.5 46.2 19.5 47.3 18.7 48.7 18.2 50.2 18.0 51.7 18.1 53.3 18.5 54.8 19.2 56.2 20.2 57.5 21.3 58.5 22.7 59.3 24.2 59.8 25.7 60.0 27.3 59.9 28.8 59.5Z',
  'M34.9 55.3 35.2 47.9 36.5 43.0 38.5 39.0 39.7 37.3 41.7 35.2 43.1 34.1 45.2 32.9 48.4 32.0 50.6 32.1 52.5 32.5 54.3 33.3 55.6 34.4 56.9 36.7 57.3 38.4 57.4 40.3 56.8 44.0 55.1 47.9 52.0 53.2 45.9 61.6 19.4 95.5 18.4 97.4 18.0 100.0 18.4 102.5 19.3 104.4 20.3 105.6 22.0 106.9 23.9 107.7 25.5 108.0 66.0 108.0 68.6 107.6 70.4 106.7 72.0 105.3 73.2 103.5 73.8 101.6 74.0 99.5 73.6 97.4 72.7 95.6 71.3 94.0 69.5 92.8 67.6 92.2 66.0 92.0 42.4 92.0 55.5 75.4 62.9 65.5 68.4 57.0 71.4 50.6 72.4 47.3 73.1 43.7 73.4 39.5 73.2 36.4 72.5 33.0 71.5 30.1 69.7 26.5 67.8 24.1 64.5 20.9 60.4 18.4 55.9 16.8 50.5 16.0 45.7 16.2 40.5 17.5 35.7 19.8 31.3 23.0 28.4 26.0 25.2 30.1 23.0 33.9 21.4 37.6 19.8 43.2 19.2 47.4 18.9 51.9 19.2 55.8 19.7 57.3 20.5 58.6 21.5 59.8 22.8 60.8 24.2 61.5 25.7 61.9 27.2 62.0 28.8 61.8 30.3 61.3 31.6 60.5 32.8 59.5 33.8 58.2 34.5 56.8Z',
  'M34.6 37.5 35.7 35.6 38.3 33.8 41.8 32.6 45.4 32.0 48.6 32.1 51.8 32.8 54.3 34.4 55.8 36.4 56.4 38.3 56.4 40.6 55.7 42.5 54.4 44.3 52.1 45.9 49.4 47.0 46.1 47.7 40.4 48.2 38.0 49.2 36.3 50.5 35.1 52.2 34.3 54.1 34.1 56.2 34.3 57.8 35.0 59.8 36.3 61.5 37.9 62.8 40.3 63.8 44.8 64.4 48.8 65.5 52.0 67.1 55.1 69.7 56.4 71.5 57.1 72.9 58.1 76.1 58.3 79.9 57.7 83.1 56.9 85.1 55.7 86.8 53.7 88.8 51.8 90.1 49.0 91.3 46.6 91.8 44.1 92.0 41.1 91.8 37.8 90.7 36.6 90.0 35.4 88.9 34.2 87.0 33.4 83.8 32.8 82.3 31.9 81.0 30.9 79.9 29.6 79.0 28.1 78.4 26.6 78.0 24.5 78.1 23.0 78.5 21.1 79.4 19.9 80.5 18.7 82.1 17.9 84.1 17.7 85.6 18.3 89.7 19.0 92.2 20.0 94.5 21.3 96.7 22.8 98.8 24.5 100.7 26.4 102.4 30.5 105.0 35.5 106.9 41.0 107.9 47.2 107.9 51.9 107.1 55.4 106.0 59.4 104.2 62.5 102.2 65.9 99.4 68.4 96.6 70.3 93.9 72.3 89.7 73.4 86.4 74.1 82.9 74.3 78.8 74.1 75.0 73.1 70.0 71.8 66.6 70.0 63.1 67.2 59.3 64.6 56.6 67.2 53.9 68.9 51.6 70.5 48.6 71.5 45.9 72.2 43.1 72.5 40.1 72.4 37.3 71.7 33.6 70.1 29.1 67.8 25.5 64.9 22.4 61.4 19.8 57.5 17.9 53.1 16.6 48.4 16.0 43.1 16.2 38.4 16.9 34.5 18.0 30.2 19.9 27.0 21.9 24.2 24.5 21.7 27.6 19.8 31.2 18.9 34.4 18.7 36.0 18.8 37.5 19.3 39.0 20.0 40.4 21.0 41.6 22.2 42.6 23.6 43.4 25.1 43.8 26.7 44.0 28.2 43.9 29.7 43.4 31.1 42.7 32.3 41.7 33.3 40.5 34.1 39.1Z',
];

const DEMO_POLL_PCT = [89, 78, 67, 56, 44];

export default function VoteLanding({
  rows,
  deck,
  pool,
  totalActivities,
  fromPrice,
  destinationSlug,
}: {
  rows: ActivityRow[];
  deck: LandingActivity[];
  pool: Pool;
  totalActivities: number;
  fromPrice: number;
  destinationSlug: string;
}) {
  const t = useT('landing.vote');
  const tAct = useT('landing.activities');
  const tChrome = useT('landing.chrome');
  const lp = useLocalePath();
  const [state, deckDispatch] = useReducer(deckReducer, undefined, initialDeck);

  // Picks are the real cart: the header badge, the cart panel, step 1's done
  // flag and the trip builder all read this one list, and it survives a reload
  // because TripProvider persists it. The deck reducer only tracks which card
  // is on top.
  const { state: trip, dispatch: tripDispatch } = useTrip();
  const bySlug = useMemo(
    () => new Map([...deck, ...rows.flatMap((r) => r.items)].map((a) => [a.slug, a])),
    [deck, rows]
  );
  const picked = useMemo(
    () => trip.tripItems.map((i: { slug?: string }) => i.slug).filter(Boolean) as string[],
    [trip.tripItems]
  );

  // Not silent: the first add pops the travelers/dates modal and later ones open
  // the cart panel, exactly as on a destination page. Dismissing that modal runs
  // CANCEL_TRIP_SETUP, which empties the cart — also as on a destination page.
  const addPick = (slug: string) => {
    const activity = bySlug.get(slug);
    if (!activity) return;
    tripDispatch({ type: 'ADD_TO_TRIP', activity: toCartItem(activity, destinationSlug) });
  };
  const togglePick = (slug: string) => {
    const activity = bySlug.get(slug);
    if (!activity) return;
    if (picked.includes(slug)) {
      tripDispatch({ type: 'REMOVE_FROM_TRIP', activityId: activity.id });
    } else {
      addPick(slug);
    }
  };
  // The deck advances on every swipe; only a right-swipe is a pick.
  const dispatch = (action: DeckAction) => {
    deckDispatch(action);
    if (action.type === 'swipe' && action.yes) addPick(action.id);
  };

  const builderHref = lp(builderUrlWithPicks(destinationSlug, picked));
  // "Start group vote" behaves exactly like the homepage's CTA of the same name
  // (components/site/LegacyHomePage.tsx): it enters the vote funnel. It used to
  // scroll to the swipe deck, which read as the CTA doing nothing but jumping
  // the page upward.
  const voteHref = lp(VOTE_FLOW_PATH);

  const goToBuilder = (label: string, block: string) => trackCtaAndGo(label, block, builderHref);

  const voteLink = (block: string, className: string) => (
    <a
      className={className}
      href={voteHref}
      onClick={(e) => {
        e.preventDefault();
        trackCtaAndGo('Start group vote', block, voteHref);
      }}
    >
      {tChrome('startVote')}
    </a>
  );

  const steps = [
    { h: t('steps.s1h'), p: t('steps.s1p', { count: totalActivities }) },
    { h: t('steps.s2h'), p: t('steps.s2p') },
    { h: t('steps.s3h'), p: t('steps.s3p') },
  ];

  return (
    <div className={`tl tl--home ${inter.variable}`} id="top">
      <LandingHeader cart={<LandingCart />}>
        {voteLink('header', 'btn btn--primary')}
      </LandingHeader>

      {/* ══════════ HERO ══════════ */}
      <section className="hero">
        <div className="hero__bg" aria-hidden="true" />
        <div className="hero__veil" aria-hidden="true" />
        <div className="hero__fade" aria-hidden="true" />
        <div className="hero__in">
          <div className="hero__left">
            <div className="hero__copy">
              <h1 className="hero__title">
                {t('hero.title1')}
                <br />
                <em>{t('hero.title2')}</em>
              </h1>
              <p className="hero__lead">
                {t('hero.leadPre')}
                <em>{t('hero.leadEm')}</em>
                {t('hero.leadPost')}
              </p>
              <ol className="hero__steps">
                <li>
                  <span className="hero__n">1</span>
                  {t('hero.step1')}
                </li>
                <li>
                  <span className="hero__n">2</span>
                  {t('hero.step2')}
                </li>
                <li>
                  <span className="hero__n">3</span>
                  {t('hero.step3')}
                </li>
              </ol>
            </div>

            <div className="hero__cta">
              {voteLink('hero', 'btn btn--primary btn--lg')}
              <a
                className="btn btn--ghost btn--lg"
                href="#activities"
                onClick={() => trackCta('See all activities', 'hero')}
              >
                {t('hero.ctaSeeAll', { count: totalActivities })}
              </a>
            </div>

            <div className="hero__meta">
              <span>{t('hero.metaActivities', { count: totalActivities })}</span>
              <span>{t('hero.metaFrom', { price: fromPrice })}</span>
              <span>{t('hero.metaCancel')}</span>
            </div>
          </div>

          {/* the signature: a live deck, zero inputs required */}
          <SwipeDeck
            deck={deck}
            state={state}
            dispatch={dispatch}
            picked={picked}
            destinationSlug={destinationSlug}
          />
        </div>
      </section>

      {/* ══════════ THE REAL PROBLEM ══════════ */}
      <section className="problem">
        <div className="shell">
          <h2 className="t-h2">{t('problem.title')}</h2>
          <div className="cols__hd">{t('problem.colsHd')}</div>
          <div className="cols">
            {(
              [
                ['problem-chat', 'c1'],
                ['problem-calendar', 'c2'],
                ['problem-prices', 'c3'],
              ] as const
            ).map(([img, key]) => (
              <div className="col" key={key}>
                <div className="col__vis col__vis--icon">
                  <img
                    className="col__icon"
                    src={`/landing/${img}.webp`}
                    alt={t(`problem.${key}alt`)}
                    loading="lazy"
                    decoding="async"
                  />
                </div>
                <h3 className="col__h">{t(`problem.${key}h`)}</h3>
                <p className="col__p">{t(`problem.${key}p`)}</p>
              </div>
            ))}
          </div>
        </div>

        {/* the answer, edge to edge */}
        <div className="band">
          <div className="band__in">
            <div className="band__head">
              <span className="band__lbl">
                {t('band.lblPre')} <TrivluLogo className="band__logo" />
              </span>
              <h3 className="band__h">{t('band.h')}</h3>
              <p className="band__p">{t('band.p')}</p>
            </div>
            <div className="band__body">
              <div className="band__time">
                <em>{t('band.timeOld')}</em>
                <strong>{t('band.timeNew')}</strong>
              </div>
              <div className="band__cta">
                {voteLink('band', 'btn btn--primary btn--lg')}
              </div>
              <p className="band__fine">{t('band.fine')}</p>
            </div>
            <div className="band__vis">
              <div className="band__hd">
                <b>{t('band.visTitle')}</b>
                <span>{t('band.visAnswered')}</span>
              </div>
              <div className="ev-poll">
                {DEMO_POLL_PCT.map((pct, i) => (
                  <div className="ev-prow" key={pct}>
                    <span className="ev-plab">{t(`band.poll${i + 1}`)}</span>
                    <span className="ev-pval" style={{ color: 'var(--brand-ic)' }}>
                      {pct}%
                    </span>
                    <span className="ev-ptrack">
                      <span className="ev-pfill" style={{ width: `${pct}%` }} />
                    </span>
                  </div>
                ))}
              </div>
              <div className="band__ok">
                <span className="dot-ok">✓</span>
                <span>
                  <b>{t('band.okTitle')}</b>
                  <span>{t('band.okSub')}</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <hr className="rule" />

      {/* ══════════ STEPS ══════════ */}
      <section id="how">
        <div className="shell">
          <h2 className="t-h2">{t('steps.title')}</h2>
          <p className="t-lede">{t('steps.lede')}</p>
          <div className="steps">
            {steps.map((step, i) => {
              const done = i === 0 && picked.length > 0;
              return (
                <article className={`step${done ? ' is-done' : ''}`} key={step.h}>
                  <div className="step__fig" aria-hidden="true">
                    <svg viewBox="0 0 90 120" role="img" focusable="false">
                      <path d={STEP_FIGURES[i]} />
                    </svg>
                  </div>
                  <h3 className="step__h">{step.h}</h3>
                  <p className="step__p">{step.p}</p>
                  <p className="step__flag">
                    {done ? t('steps.done', { count: picked.length }) : ''}
                  </p>
                </article>
              );
            })}
          </div>
          <div className="cta-row">{voteLink('steps', 'btn btn--primary btn--lg')}</div>
        </div>
      </section>

      {/* ══════════ TRUST STRIP ══════════ */}
      <section className="trust">
        <div className="trust__in">
          {(['t1', 't2', 't3', 't4'] as const).map((key) => (
            <div className="trust__i" key={key}>
              <strong>{t(`trust.${key}h`)}</strong>
              <span>{t(`trust.${key}p`)}</span>
            </div>
          ))}
        </div>
      </section>

      {/* ══════════ ACTIVITIES ══════════ */}
      <section id="activities">
        <div className="shell">
          <p className="t-eyebrow">{tAct('eyebrow')}</p>
          <h2 className="t-h2">{tAct('title', { count: totalActivities })}</h2>
          <p className="t-lede">{tAct('ledeVote')}</p>
          <ActivityRows
            rows={rows}
            destinationSlug={destinationSlug}
            picked={picked}
            onToggle={togglePick}
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

      {/* ══════════ COSTS ══════════ */}
      <CostsSection pool={pool} destinationSlug={destinationSlug} />

      <hr className="rule" />

      <WhyUsSection variant="vote" />

      <hr className="rule" />

      <ReviewsSection variant="vote" />

      <hr className="rule" />

      <FaqSection variant="vote" />

      {/* ══════════ FINAL CTA ══════════ */}
      <section className="final">
        <div className="shell">
          <h2>{t('final.title')}</h2>
          <p>{t('final.p')}</p>
          <div className="final__row">
            {/* With a shortlist the group has already voted here, so this
                continues to the builder; empty, it enters the vote funnel. */}
            {picked.length > 0 ? (
              <a
                className="btn btn--primary btn--lg"
                href={builderHref}
                onClick={(e) => {
                  e.preventDefault();
                  goToBuilder('Build your trip now', 'final');
                }}
              >
                {t('final.buildNow')}
              </a>
            ) : (
              voteLink('final', 'btn btn--primary btn--lg')
            )}
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
        taglineKey="taglineVote"
        tripLinks={[
          { href: '#activities', labelKey: 'activities' },
          { href: '#how', labelKey: 'how' },
          { href: '#costs', labelKey: 'prices' },
        ]}
      />

      {/* ══════════ STICKY MOBILE BAR ══════════ */}
      <div className="sticky">
        {/* Same branch as the final CTA: with a shortlist there is a trip to
            continue to, empty it enters the vote funnel — otherwise the bar
            dropped visitors into an empty builder. */}
        {picked.length > 0 ? (
          <button
            className="btn btn--primary"
            type="button"
            onClick={() => goToBuilder('Build your trip now', 'sticky')}
          >
            {t('sticky.cta')}
          </button>
        ) : (
          voteLink('sticky', 'btn btn--primary')
        )}
        <span className="sticky__note">{t('sticky.note')}</span>
      </div>

      {/* The first add's travelers/dates modal, same as a destination page's.
          Mounted here at the page root, NOT inside LandingCart: .hdr carries a
          backdrop-filter, which makes it the containing block for fixed
          descendants and would trap the overlay inside the header bar.
          clearOnCancel=false: in the app a dismiss empties the trip, but here
          that would throw away a shortlist the visitor swiped together. */}
      <TripSetupModal clearOnCancel={false} />
    </div>
  );
}

function CostsSection({ pool, destinationSlug }: { pool: Pool; destinationSlug: string }) {
  const t = useT('landing.calc');
  return (
    <section id="costs">
      <div className="shell">
        <p className="t-eyebrow">{t('eyebrow')}</p>
        <h2 className="t-h2">{t('title')}</h2>
        <p className="t-lede">{t('lede')}</p>
        <TripCalculator pool={pool} destinationSlug={destinationSlug} />
      </div>
    </section>
  );
}
