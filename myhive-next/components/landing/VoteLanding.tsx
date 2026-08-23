'use client';

// The voting landing at /landing/vote (mockup
// fixes/trivlu-landing-1-voting-v58.html): "Skip chatting. Start voting."
// One picked list — fed by the hero swipe deck and the catalogue's Add-to-trip
// buttons — drives the header cart, step 1's done flag, the sticky bar and the
// final CTA.
import { useReducer } from 'react';
import './landing.css';
import './home.css';
import { inter } from './fonts';
import LandingHeader from './LandingHeader';
import LandingFooter from './LandingFooter';
import TrivluLogo from './TrivluLogo';
import ActivityRows from './ActivityRows';
import TripCalculator from './TripCalculator';
import WhyUsSection from './WhyUsSection';
import ReviewsSection, { type LandingReview } from './ReviewsSection';
import FaqSection, { type FaqItem } from './FaqSection';
import SwipeDeck, { builderUrlWithPicks } from './SwipeDeck';
import { deckReducer, initialDeck } from './deck';
import { trackCta, trackCtaAndGo } from './analytics';
import { PHONE_DISPLAY, PHONE_HREF, type ActivityRow, type LandingActivity } from './data';
import type { Pool } from './engine';

const REVIEWS: LandingReview[] = [
  {
    quote:
      'The easiest bachelor party I ever organised. The group voted. Trivlu arranged everything. I only arrived.',
    name: 'James W.',
    meta: '11 people · UK · May 2025 · karting, shooting',
    avatar: 'https://randomuser.me/api/portraits/men/32.jpg',
  },
  {
    quote:
      'We booked shooting, karting and a boat party for fourteen people. No problems. A perfect weekend.',
    name: 'Connor M.',
    meta: '14 people · Ireland · June 2025 · boat, shooting',
    avatar: 'https://randomuser.me/api/portraits/men/44.jpg',
  },
  {
    quote:
      'The group vote ended every argument in the chat. The decision took one evening instead of a month.',
    name: 'Mark D.',
    meta: '8 people · UK · Sept 2025 · rafting, beer spa',
    avatar: 'https://randomuser.me/api/portraits/men/75.jpg',
  },
  {
    quote: 'Trivlu communicated well. The plan was exactly right. The deposit payment was easy.',
    name: 'Tom V.',
    meta: '9 people · Netherlands · Oct 2025 · paintball, dinner',
    avatar: 'https://randomuser.me/api/portraits/men/11.jpg',
  },
];

const FAQ: FaqItem[] = [
  {
    q: 'Is Prague still a good choice in 2026? I read that bachelor parties were banned.',
    open: true,
    a: (
      <>
        <p className="faq__a">
          Prague banned no bachelor parties. The city banned <em>commercial</em> pub crawls in
          November 2024. The ban covers the historic centre between 22:00 and 06:00. The fine
          reaches 100,000 CZK. The fine applies to the tour company, not to your group. Your
          friends may walk from one pub to another at any hour.
        </p>
        <p className="faq__a">
          The city centre also contains no-drinking zones. An open can in your hand costs up to
          10,000 CZK. Our evenings start earlier. Our evenings happen inside venues, not on the
          street. These rules affect no part of your weekend.
        </p>
      </>
    ),
  },
  {
    q: 'Does everyone need to download an app or create an account?',
    open: true,
    a: (
      <p className="faq__a">
        No. You share one link. Your friends open the link in a browser. No app, no account, no
        password. You only leave your details before booking.
      </p>
    ),
  },
  {
    q: 'Who pays, and when?',
    open: true,
    a: (
      <p className="faq__a">
        One person pays a 30 percent deposit by card. The deposit reserves every activity. The
        group pays the remaining balance on arrival. Free cancellation protects your group if the
        group size changes. <a href="/refund-policy">Refund policy</a>
      </p>
    ),
  },
  {
    q: 'We are not interested in strip clubs. Is there anything else?',
    a: (
      <p className="faq__a">
        Trivlu offers 72 activities. Only one category out of eight contains adult entertainment.
        Choose rafting, shooting, karting or axe throwing. Choose a beer spa, a brewery tour or a
        river cruise. Choose bubble football or tank driving. Many groups ignore the adult category
        completely.
      </p>
    ),
  },
  {
    q: 'Do you book flights and hotels too?',
    a: (
      <p className="faq__a">
        No. Trivlu adds no fee to flights or hotels. You find better prices yourself. Trivlu shows
        realistic price ranges for both. Trivlu books the activities, plans the timing, and solves
        problems in the city.
      </p>
    ),
  },
  {
    q: 'What if someone cancels?',
    a: (
      <p className="faq__a">
        Tell us. We change the booking. Free cancellation covers every activity. Watch the minimum
        group price. Some activities charge a minimum price for the whole group. Trivlu shows the
        minimum price on the activity card.
      </p>
    ),
  },
  {
    q: 'What is the smallest group you accept?',
    a: (
      <p className="faq__a">
        A group of four works for most activities. Smaller groups pay more per person for
        activities with a minimum group price. Trivlu shows the exact price before you book.
      </p>
    ),
  },
  {
    q: 'How fast do you really reply?',
    a: (
      <p className="faq__a">
        We reply within ten minutes, seven days a week. Call {PHONE_DISPLAY}. Or send a message to
        the same number on WhatsApp.
      </p>
    ),
  },
];

// The three drawn numerals: same 90×120 box, monoline skeleton, hairline only.
const STEP_FIGURES = [
  'M30.2 58.8 48.0 42.4 48.0 100.0 48.2 101.6 48.6 103.1 49.7 104.9 51.1 106.3 52.5 107.2 54.4 107.8 56.0 108.0 58.1 107.7 60.0 106.9 61.7 105.7 62.9 104.0 63.7 102.1 64.0 100.0 64.0 23.5 63.7 21.9 63.2 20.4 62.3 19.1 60.8 17.6 58.9 16.6 56.4 16.0 53.7 16.3 51.4 17.5 20.5 46.2 19.5 47.3 18.7 48.7 18.2 50.2 18.0 51.7 18.1 53.3 18.5 54.8 19.2 56.2 20.2 57.5 21.3 58.5 22.7 59.3 24.2 59.8 25.7 60.0 27.3 59.9 28.8 59.5Z',
  'M34.9 55.3 35.2 47.9 36.5 43.0 38.5 39.0 39.7 37.3 41.7 35.2 43.1 34.1 45.2 32.9 48.4 32.0 50.6 32.1 52.5 32.5 54.3 33.3 55.6 34.4 56.9 36.7 57.3 38.4 57.4 40.3 56.8 44.0 55.1 47.9 52.0 53.2 45.9 61.6 19.4 95.5 18.4 97.4 18.0 100.0 18.4 102.5 19.3 104.4 20.3 105.6 22.0 106.9 23.9 107.7 25.5 108.0 66.0 108.0 68.6 107.6 70.4 106.7 72.0 105.3 73.2 103.5 73.8 101.6 74.0 99.5 73.6 97.4 72.7 95.6 71.3 94.0 69.5 92.8 67.6 92.2 66.0 92.0 42.4 92.0 55.5 75.4 62.9 65.5 68.4 57.0 71.4 50.6 72.4 47.3 73.1 43.7 73.4 39.5 73.2 36.4 72.5 33.0 71.5 30.1 69.7 26.5 67.8 24.1 64.5 20.9 60.4 18.4 55.9 16.8 50.5 16.0 45.7 16.2 40.5 17.5 35.7 19.8 31.3 23.0 28.4 26.0 25.2 30.1 23.0 33.9 21.4 37.6 19.8 43.2 19.2 47.4 18.9 51.9 19.2 55.8 19.7 57.3 20.5 58.6 21.5 59.8 22.8 60.8 24.2 61.5 25.7 61.9 27.2 62.0 28.8 61.8 30.3 61.3 31.6 60.5 32.8 59.5 33.8 58.2 34.5 56.8Z',
  'M34.6 37.5 35.7 35.6 38.3 33.8 41.8 32.6 45.4 32.0 48.6 32.1 51.8 32.8 54.3 34.4 55.8 36.4 56.4 38.3 56.4 40.6 55.7 42.5 54.4 44.3 52.1 45.9 49.4 47.0 46.1 47.7 40.4 48.2 38.0 49.2 36.3 50.5 35.1 52.2 34.3 54.1 34.1 56.2 34.3 57.8 35.0 59.8 36.3 61.5 37.9 62.8 40.3 63.8 44.8 64.4 48.8 65.5 52.0 67.1 55.1 69.7 56.4 71.5 57.1 72.9 58.1 76.1 58.3 79.9 57.7 83.1 56.9 85.1 55.7 86.8 53.7 88.8 51.8 90.1 49.0 91.3 46.6 91.8 44.1 92.0 41.1 91.8 37.8 90.7 36.6 90.0 35.4 88.9 34.2 87.0 33.4 83.8 32.8 82.3 31.9 81.0 30.9 79.9 29.6 79.0 28.1 78.4 26.6 78.0 24.5 78.1 23.0 78.5 21.1 79.4 19.9 80.5 18.7 82.1 17.9 84.1 17.7 85.6 18.3 89.7 19.0 92.2 20.0 94.5 21.3 96.7 22.8 98.8 24.5 100.7 26.4 102.4 30.5 105.0 35.5 106.9 41.0 107.9 47.2 107.9 51.9 107.1 55.4 106.0 59.4 104.2 62.5 102.2 65.9 99.4 68.4 96.6 70.3 93.9 72.3 89.7 73.4 86.4 74.1 82.9 74.3 78.8 74.1 75.0 73.1 70.0 71.8 66.6 70.0 63.1 67.2 59.3 64.6 56.6 67.2 53.9 68.9 51.6 70.5 48.6 71.5 45.9 72.2 43.1 72.5 40.1 72.4 37.3 71.7 33.6 70.1 29.1 67.8 25.5 64.9 22.4 61.4 19.8 57.5 17.9 53.1 16.6 48.4 16.0 43.1 16.2 38.4 16.9 34.5 18.0 30.2 19.9 27.0 21.9 24.2 24.5 21.7 27.6 19.8 31.2 18.9 34.4 18.7 36.0 18.8 37.5 19.3 39.0 20.0 40.4 21.0 41.6 22.2 42.6 23.6 43.4 25.1 43.8 26.7 44.0 28.2 43.9 29.7 43.4 31.1 42.7 32.3 41.7 33.3 40.5 34.1 39.1Z',
];

const DEMO_POLL: { label: string; pct: number }[] = [
  { label: 'AK-47 shooting', pct: 89 },
  { label: 'River boat cruise', pct: 78 },
  { label: 'Beer spa', pct: 67 },
  { label: 'Go-karting', pct: 56 },
  { label: 'Army tank experience', pct: 44 },
];

function scrollToDeck() {
  document
    .getElementById('deck-stage')
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

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
  const [state, dispatch] = useReducer(deckReducer, undefined, initialDeck);
  const picked = state.picked;
  const builderHref = builderUrlWithPicks(picked);

  const goToBuilder = (label: string, block: string) => trackCtaAndGo(label, block, builderHref);

  return (
    <div className={`tl tl--home ${inter.variable}`} id="top">
      <LandingHeader>
        <button
          className="btn btn--primary"
          type="button"
          onClick={() => {
            trackCta('Start group vote', 'header');
            scrollToDeck();
          }}
        >
          Start group vote
        </button>
        <button
          className="hdr__cart"
          type="button"
          aria-label={`Selected activities. ${picked.length} ${picked.length === 1 ? 'item' : 'items'}`}
          onClick={() => {
            if (picked.length > 0) goToBuilder('Cart', 'header');
            else document.getElementById('activities')?.scrollIntoView({ behavior: 'smooth' });
          }}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <circle cx="9" cy="20" r="1" />
            <circle cx="19" cy="20" r="1" />
            <path d="M3 4h2l2.4 10.4a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.6L21 8H6" />
          </svg>
          <span className="hdr__cart-count">{picked.length > 0 ? picked.length : ''}</span>
        </button>
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
                Skip chatting.
                <br />
                <em>Start voting</em>
              </h1>
              <p className="hero__lead">
                Plan a <em>bachelor party in Prague</em> with no chaos in the group chat.
              </p>
              <ol className="hero__steps">
                <li>
                  <span className="hero__n">1</span>Create a shortlist
                </li>
                <li>
                  <span className="hero__n">2</span>Share the vote link
                </li>
                <li>
                  <span className="hero__n">3</span>Confirm the winners
                </li>
              </ol>
            </div>

            <div className="hero__cta">
              <button
                className="btn btn--primary btn--lg"
                type="button"
                onClick={() => {
                  trackCta('Start group vote', 'hero');
                  scrollToDeck();
                }}
              >
                Start group vote
              </button>
              <a
                className="btn btn--ghost btn--lg"
                href="#activities"
                onClick={() => trackCta('See all activities', 'hero')}
              >
                See all {totalActivities} activities
              </a>
            </div>

            <div className="hero__meta">
              <span>{totalActivities} activities in Prague</span>
              <span>from €{fromPrice} per person</span>
              <span>Free cancellation</span>
            </div>
          </div>

          {/* the signature: a live deck, zero inputs required */}
          <SwipeDeck deck={deck} state={state} dispatch={dispatch} />
        </div>
      </section>

      {/* ══════════ THE REAL PROBLEM ══════════ */}
      <section className="problem">
        <div className="shell">
          <h2 className="t-h2">Getting your group to agree is the hardest part of the trip</h2>
          <div className="cols__hd">What it usually looks like</div>
          <div className="cols">
            <div className="col">
              <div className="col__vis col__vis--icon">
                <img
                  className="col__icon"
                  src="/landing/problem-chat.webp"
                  alt="Unread group chat icon"
                  loading="lazy"
                  decoding="async"
                />
              </div>
              <h3 className="col__h">Chaos in a group chat</h3>
              <p className="col__p">Everyone holds an opinion. Everyone waits for somebody else.</p>
            </div>
            <div className="col">
              <div className="col__vis col__vis--icon">
                <img
                  className="col__icon"
                  src="/landing/problem-calendar.webp"
                  alt="Calendar with a selected date icon"
                  loading="lazy"
                  decoding="async"
                />
              </div>
              <h3 className="col__h">Time passes. No decision</h3>
              <p className="col__p">The bachelor party is close. The group booked nothing.</p>
            </div>
            <div className="col">
              <div className="col__vis col__vis--icon">
                <img
                  className="col__icon"
                  src="/landing/problem-prices.webp"
                  alt="Rising price chart icon"
                  loading="lazy"
                  decoding="async"
                />
              </div>
              <h3 className="col__h">Flight prices rise every week</h3>
              <p className="col__p">Every week of delay costs the group real money.</p>
            </div>
          </div>
        </div>

        {/* the answer, edge to edge */}
        <div className="band">
          <div className="band__in">
            <div className="band__head">
              <span className="band__lbl">
                What it looks like using <TrivluLogo className="band__logo" />
              </span>
              <h3 className="band__h">Voting is finished. Tour booked in 10 minutes</h3>
              <p className="band__p">
                Your group votes. Trivlu builds one exact itinerary, tailored to your group hour by
                hour.
              </p>
            </div>
            <div className="band__body">
              <div className="band__time">
                <em>Three weeks</em>
                <strong>10 minutes</strong>
              </div>
              <div className="band__cta">
                <button
                  className="btn btn--primary btn--lg"
                  type="button"
                  onClick={() => goToBuilder('Start group vote', 'band')}
                >
                  Start group vote
                </button>
              </div>
              <p className="band__fine">No account or payment card needed.</p>
            </div>
            <div className="band__vis">
              <div className="band__hd">
                <b>Voting is finished</b>
                <span>11 of 11 answered</span>
              </div>
              <div className="ev-poll">
                {DEMO_POLL.map((row) => (
                  <div className="ev-prow" key={row.label}>
                    <span className="ev-plab">{row.label}</span>
                    <span className="ev-pval" style={{ color: 'var(--brand-ic)' }}>
                      {row.pct}%
                    </span>
                    <span className="ev-ptrack">
                      <span className="ev-pfill" style={{ width: `${row.pct}%` }} />
                    </span>
                  </div>
                ))}
              </div>
              <div className="band__ok">
                <span className="dot-ok">✓</span>
                <span>
                  <b>Booking confirmed</b>
                  <span>Prague · 19–21 Jun · 8 people</span>
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
          <h2 className="t-h2">Plan your trip in 3 easy steps</h2>
          <p className="t-lede">Let the group decide. You confirm the winners.</p>
          <div className="steps">
            {[
              {
                h: 'Create a shortlist',
                p: `Choose from ${totalActivities} activities in Prague. No account, no form.`,
              },
              {
                h: 'Share one link. The group votes',
                p: 'Send the vote link to the group chat. We email you the results.',
              },
              {
                h: 'Confirm the winners',
                p: 'We confirm every booking with the venue. You pay 30 percent now, the balance on arrival.',
              },
            ].map((step, i) => {
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
                  <p className="step__flag">{done ? `Done. ${picked.length} chosen.` : ''}</p>
                </article>
              );
            })}
          </div>
          <div className="cta-row">
            <button
              className="btn btn--primary btn--lg"
              type="button"
              onClick={() => {
                trackCta('Start group vote', 'steps');
                scrollToDeck();
              }}
            >
              Start group vote
            </button>
          </div>
        </div>
      </section>

      {/* ══════════ TRUST STRIP ══════════ */}
      <section className="trust">
        <div className="trust__in">
          <div className="trust__i">
            <strong>Voting is free</strong>
            <span>Pay only when you book.</span>
          </div>
          <div className="trust__i">
            <strong>30% deposit</strong>
            <span>Pay the balance on arrival.</span>
          </div>
          <div className="trust__i">
            <strong>15 years in Prague</strong>
            <span>Local team. No agency fee.</span>
          </div>
          <div className="trust__i">
            <strong>We reply in 10 minutes</strong>
            <span>Human support 24/7</span>
          </div>
        </div>
      </section>

      {/* ══════════ ACTIVITIES ══════════ */}
      <section id="activities">
        <div className="shell">
          <p className="t-eyebrow">What you can book</p>
          <h2 className="t-h2">{totalActivities} activities. Pick the ones your group wants</h2>
          <p className="t-lede">
            From tank driving to river cruises and spas, there are activities for every group.
          </p>
          <ActivityRows
            rows={rows}
            destinationSlug={destinationSlug}
            picked={picked}
            onToggle={(id) => dispatch({ type: 'toggle', id })}
          />
          <div className="grid__more">
            <a
              className="btn btn--ghost btn--lg"
              href={`/destination/${destinationSlug}?tab=activities`}
              onClick={() => trackCta('View all activities', 'activities')}
            >
              View all {totalActivities} activities →
            </a>
          </div>
        </div>
      </section>

      <hr className="rule" />

      {/* ══════════ COSTS ══════════ */}
      <section id="costs">
        <div className="shell">
          <p className="t-eyebrow">A weekend for every budget</p>
          <h2 className="t-h2">See what your budget actually buys</h2>
          <p className="t-lede">
            Say how long you are staying and how much you want to spend. We create an example
            weekend from the real catalogue.
          </p>
          <TripCalculator pool={pool} ctaLabel="Build your trip now" />
        </div>
      </section>

      <hr className="rule" />

      <WhyUsSection
        paragraphs={[
          'We organised bachelor parties in Prague for fifteen years.',
          'You gain two advantages with Trivlu: you pay the venue price without an added agency fee. You speak to the person who books your activities. Our team is located in Prague and always happy to assist you.',
        ]}
        guarantees={[
          'Pay a 30 percent deposit now. Pay the balance later.',
          'Free cancellation covers every activity.',
          'Card payments are processed by Stripe.',
        ]}
        block="why"
      />

      <hr className="rule" />

      <ReviewsSection reviews={REVIEWS} />

      <hr className="rule" />

      <FaqSection items={FAQ} />

      {/* ══════════ FINAL CTA ══════════ */}
      <section className="final">
        <div className="shell">
          <h2>Stop debating in the group chat</h2>
          <p>Choose the activities. Send one link. Let the group vote.</p>
          <div className="final__row">
            <button
              className="btn btn--primary btn--lg"
              type="button"
              onClick={() => {
                if (picked.length > 0) {
                  goToBuilder('Build your trip now', 'final');
                } else {
                  trackCta('Start group vote', 'final');
                  scrollToDeck();
                }
              }}
            >
              {picked.length > 0 ? `Build your trip now (${picked.length} chosen)` : 'Start group vote'}
            </button>
            <a
              className="btn btn--ghost btn--lg"
              href={PHONE_HREF}
              onClick={() => trackCta('Call', 'final')}
            >
              📞 {PHONE_DISPLAY}
            </a>
          </div>
          <p className="final__fine">Reply in 10 minutes · Free cancellation · 30% deposit</p>
        </div>
      </section>

      <LandingFooter
        tagline="Prague stag do. Planned in 10 minutes. Your group votes, we do the rest."
        tripLinks={[
          { href: '#activities', label: 'Activities' },
          { href: '#how', label: 'How Trivlu works' },
          { href: '#costs', label: 'Prices' },
        ]}
      />

      {/* ══════════ STICKY MOBILE BAR ══════════ */}
      <div className="sticky">
        <button
          className="btn btn--primary"
          type="button"
          onClick={() => goToBuilder('Build your trip now', 'sticky')}
        >
          Build your trip now
        </button>
        <span className="sticky__note">No account or credit card needed.</span>
      </div>
    </div>
  );
}
