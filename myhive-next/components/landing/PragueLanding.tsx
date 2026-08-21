'use client';

// The Prague destination landing (mockup
// fixes/trivlu-landing-2-prague-desktop-sticky-right-v74.html): "Prague is
// ninety minutes away and four times cheaper". Serves the bare
// /destination/prague URL; the ?tab= catalogue stays with the SPA.
import './landing.css';
import './prague.css';
import { inter } from './fonts';
import LandingHeader from './LandingHeader';
import LandingFooter from './LandingFooter';
import ActivityRows from './ActivityRows';
import TripCalculator from './TripCalculator';
import WhyUsSection from './WhyUsSection';
import ReviewsSection, { type LandingReview } from './ReviewsSection';
import FaqSection, { type FaqItem } from './FaqSection';
import { trackCta, trackCtaAndGo } from './analytics';
import {
  BUILDER_URL,
  PHONE_DISPLAY,
  PHONE_HREF,
  type ActivityRow,
} from './data';
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
      'We worried about the new rules in Prague. The rules changed nothing. The weekend ran exactly as planned.',
    name: 'Mark D.',
    meta: '8 people · UK · Sept 2025 · rafting, river boat',
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
    q: 'When is the best time to go?',
    open: true,
    a: (
      <p className="faq__a">
        Choose May, June or September. The weather suits the Vltava river and the outdoor
        activities. The city stays calm. July and August bring heat and crowds. Winter is cheap and
        the bars are excellent. Winter closes rafting and outdoor shooting.
      </p>
    ),
  },
  {
    q: 'How far ahead should we book?',
    open: true,
    a: (
      <p className="faq__a">
        Book six to eight weeks ahead. Flights cost about 44 percent less than a last-minute
        booking. The popular Saturday slots stay open. Four weeks still works. Two weeks works with
        a smaller choice.
      </p>
    ),
  },
  {
    q: 'Do you organise the accommodation and transfers?',
    open: true,
    a: (
      <p className="faq__a">
        Trivlu offers airport transfers as an activity. Trivlu sells no hotels. You find a better
        price yourself. Trivlu adds no fee. We name the districts that keep every venue walkable.
      </p>
    ),
  },
  {
    q: 'Is Prague safe for a large group?',
    a: (
      <p className="faq__a">
        Yes. Watch for pickpockets around Charles Bridge and Old Town Square. Never change money on
        the street. Agree the taxi price before the ride. Our guides manage the venue arrangements.
        We book only partner venues.
      </p>
    ),
  },
  {
    q: 'We are not interested in strip clubs. Is there anything else?',
    a: (
      <p className="faq__a">
        Trivlu offers 72 activities. Only one category out of eight contains adult entertainment.
        Choose rafting, shooting, karting or axe throwing. Choose a beer spa, a brewery tour or a
        river cruise. Many groups ignore the adult category completely.
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

const TILES = [
  {
    h: 'Your money lasts four times longer',
    img: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/wallet.webp',
    alt: 'A purple wallet holding euro notes',
    w: 480,
    hh: 336,
    note: (
      <>
        <b>€2.20</b> a beer <em>· €9.60 in Oslo</em>
      </>
    ),
  },
  {
    h: 'You land before dinner on Friday',
    img: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/plane.webp',
    alt: 'An aeroplane taking off beside a clock',
    w: 480,
    hh: 317,
    note: (
      <>
        <b>1 h 20</b> direct <em>· from €66 return</em>
      </>
    ),
  },
  {
    h: 'Things you simply cannot do at home',
    img: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/tank.webp',
    alt: 'An armoured tracked vehicle you can drive on a range',
    w: 480,
    hh: 331,
    note: (
      <>
        <b>72 activities</b> <em>· from €26</em>
      </>
    ),
  },
  {
    h: 'Everything sits inside a twenty-minute walk',
    img: 'https://raw.githubusercontent.com/cyrudi/sandbox/main/map.webp',
    alt: 'A map of central Prague with Charles Bridge and three pins',
    w: 480,
    hh: 322,
    note: (
      <>
        <b>20 min</b> corner to corner
      </>
    ),
  },
];

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
  const builderLink = (block: string, label: string, className: string) => (
    <a
      className={className}
      href={BUILDER_URL}
      onClick={(e) => {
        e.preventDefault();
        trackCtaAndGo(label, block, BUILDER_URL);
      }}
    >
      {label}
    </a>
  );

  return (
    <div className={`tl tl--prague ${inter.variable}`} id="top">
      <LandingHeader>{builderLink('header', 'Build your trip', 'btn btn--primary')}</LandingHeader>

      {/* ══════════ HERO ══════════ */}
      <section className="hero">
        <div className="hero__bg" aria-hidden="true" />
        <div className="hero__veil" aria-hidden="true" />
        <div className="hero__in">
          <p className="hero__eyebrow">Bachelor party planner for Prague</p>
          <h1 className="hero__title">
            Prague is <em>ninety minutes away</em> and <em>four times cheaper</em>
          </h1>
          <p className="hero__sub">
            Prague is the most popular bachelor party city in Europe. Lots to do, low prices, and
            most activities are within walking distance.
          </p>
          <div className="hero__cta">
            {builderLink('hero', 'Build your trip', 'btn btn--primary btn--lg')}
            <a
              className="btn btn--ghost btn--lg"
              href="#activities"
              onClick={() => trackCta('Explore Activities', 'hero')}
            >
              Explore Activities
            </a>
          </div>
          <div className="hero__pf">
            <div className="pf__i">
              <b>15 years</b>
              <span>Experience in Prague. Local team</span>
            </div>
            <div className="pf__i">
              <b>10 min</b>
              <span>Average reply time. 7 days a week</span>
            </div>
            <div className="pf__i">
              <b>30%</b>
              <span>Deposit now. Free cancellation</span>
            </div>
            <div className="pf__i">
              <b>€0</b>
              <span>Plan and vote for free before you book</span>
            </div>
          </div>
        </div>
      </section>

      {/* ══════════ ACTIVITIES ══════════ */}
      <section id="activities">
        <div className="shell">
          <p className="t-eyebrow">What you can book</p>
          <h2 className="t-h2">{totalActivities} activities. Pick the ones your group wants</h2>
          <p className="t-lede">
            From tank driving to strip clubs and spa — we&apos;ve got every stag style covered.
          </p>
          <ActivityRows rows={rows} destinationSlug={destinationSlug} showChip />
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

      {/* ══════════ THE ARGUMENT ══════════ */}
      <section className="args" id="why-prague">
        <div className="shell args__hd">
          <h2 className="t-h2">Four arguments for choosing Prague</h2>
        </div>
        <div className="shell tiles">
          {TILES.map((t) => (
            <article className="tile" key={t.h}>
              <h3 className="tile__h">{t.h}</h3>
              <img
                className="tile__ic"
                src={t.img}
                alt={t.alt}
                width={t.w}
                height={t.hh}
                loading="lazy"
                decoding="async"
              />
              <p className="tile__n">{t.note}</p>
            </article>
          ))}
        </div>
      </section>

      <figure className="band-img">
        <img
          alt="Charles Bridge and the Prague skyline at dusk"
          decoding="async"
          loading="lazy"
          src="https://wsrv.nl/?url=https%3A%2F%2Fimages.unsplash.com%2Fphoto-1772202950305-2e6fee0b1ab3%3Ffm%3Djpg%26q%3D62%26w%3D2000%26auto%3Dformat%26fit%3Dcrop&w=1600&output=webp&q=76"
        />
        <figcaption>
          Build it now, decide later
          <small>Nothing to pay until the group agrees. Let them vote on the activities you pick.</small>
          <span className="band-img__cta">{builderLink('band', 'Build your trip', 'btn btn--primary')}</span>
        </figcaption>
      </figure>

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
        eyebrow="About us"
        paragraphs={[
          'We organised bachelor parties in Prague for fifteen years.',
          'You gain two advantages with Trivlu: You pay the venue price without an added agency fee. You speak to the person who books your activities.',
          'Our team is located in Prague and always happy to assist you.',
        ]}
        guarantees={[
          'Pay a 30 percent deposit now. Pay the balance later.',
          'Free cancellation covers every activity.',
          'Card payments are processed by Stripe.',
          'One person books. The group repays that person later.',
        ]}
        block="why"
      />

      <hr className="rule" />

      <ReviewsSection eyebrow="Reviews from groups we organised before" reviews={REVIEWS} />

      <hr className="rule" />

      <FaqSection eyebrow="Answered before you ask" items={FAQ} />

      {/* ══════════ THE 2026 RULES ══════════ */}
      <section className="rules" id="rules">
        <div className="shell">
          <p className="t-eyebrow">The question everyone asks now</p>
          <h2 className="t-h2">&ldquo;Did Prague ban bachelor parties?&rdquo;</h2>
          <p className="t-lede">
            No. One rule changed in 2024. Most websites explain the rule badly. Read the facts
            below.
          </p>
          <div className="rules__grid">
            <div className="rules__card">
              <span className="rules__badge rules__badge--myth">What people think</span>
              <h4>&ldquo;Bachelor parties are banned and you get fined.&rdquo;</h4>
              <p>
                Headlines about a pub crawl ban created this belief. No law made group travel to
                Prague illegal.
              </p>
            </div>
            <div className="rules__card">
              <span className="rules__badge rules__badge--fact">What is true</span>
              <h4>Commercial pub crawls cannot run at night</h4>
              <p>
                The city banned commercial pub crawls in November 2024. The ban covers the historic
                centre between 22:00 and 06:00. The fine reaches 100,000 CZK. The fine applies to
                the company, not to your group.
              </p>
              <p>Your friends may walk from one pub to another at any hour.</p>
            </div>
          </div>
          <ul className="rules__list">
            <li>
              <b>Street drinking</b> is banned in parts of the city centre. An open can in your
              hand costs up to 10,000 CZK. Bar terraces and beer gardens remain legal.
            </li>
            <li>
              <b>Rented e-scooters</b> left Prague in January 2026. Bikes and public transport
              still operate. The centre stays walkable.
            </li>
            <li>
              <b>No alcohol</b> is permitted for any driver, including a cyclist. Not half a beer.
              None.
            </li>
            <li>
              <b>Costumes</b> work in party bars. Better clubs and traditional beer halls refuse a
              group in matching outfits.
            </li>
          </ul>
          <div className="note">
            <b>Our method.</b> Our evenings start earlier. Our evenings happen inside partner
            venues, not on the street. We worked in Prague through the rule change. No Trivlu group
            ever received a fine.
          </div>
        </div>
      </section>

      {/* ══════════ FINAL ══════════ */}
      <section className="final">
        <div className="shell">
          <h2>It&apos;s time to build your perfect weekend in Prague</h2>
          <p>
            Pick your dates. Choose the activities. See the price for your group. The whole process
            takes ten minutes.
          </p>
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
          <p className="final__fine">Reply in 10 minutes · Free cancellation · 30% deposit</p>
        </div>
      </section>

      <LandingFooter
        tagline="Group travel with no chaos in a group chat. Built in Prague."
        tripLinks={[
          { href: '#activities', label: 'Activities' },
          { href: '#costs', label: 'Example weekend' },
          { href: '#rules', label: 'The 2026 rules' },
        ]}
      />
    </div>
  );
}
