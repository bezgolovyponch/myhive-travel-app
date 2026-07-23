// SSR homepage — content parity with legacy-src/pages/HomePage.js (+ sections),
// rendered as a Server Component so crawlers get real HTML. Funnel CTAs
// full-page-navigate into SPA-owned URLs (/vote/new opens the setup modal).
import Link from 'next/link';
import { api, REVALIDATE_SECONDS, type Activity } from '../../lib/api';
import { SITE_URL, WHATSAPP_URL, DEFAULT_DESTINATION_SLUG, pageMetadata, jsonLd } from '../../lib/seo';
import ActivityCardStatic from '../../components/site/ActivityCardStatic';
import '../../legacy-src/pages/HomePage.css';
import '../../legacy-src/components/home/TrustBar.css';
import '../../legacy-src/components/home/HowItWorksSection.css';
import '../../legacy-src/components/home/ReviewsSection.css';
import '../../legacy-src/components/home/ContactCtaSection.css';
import '../../legacy-src/components/home/FeaturedActivitiesSection.css';

export const revalidate = 3600;

// Title/description from SEO план v3 (метатеги table); the visible H1 stays
// the designed hero copy — same meaning, brand tone (doc: «заготовки»).
const TITLE = 'Trivlu — Stag Do Trips, Sorted in Minutes';
const DESCRIPTION =
  'Plan a stag weekend the whole group agrees on. Vote on activities, get a price for the group, book with a 30% deposit.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/',
});

const VOTE_ROWS = [
  { icon: 'ph-beer-stein', name: 'Bar Crawl', num: 8, pct: 89, fill: 'var(--purple-ll)' },
  { icon: 'ph-steering-wheel', name: 'Karting', num: 6, pct: 67, fill: 'var(--purple-l)' },
  { icon: 'ph-target', name: 'Shooting', num: 5, pct: 56, fill: 'var(--purple-l)' },
  { icon: 'ph-boat', name: 'Tiki Boat', num: 4, pct: 44, fill: 'var(--purple-l)' },
];

const TRUST_ITEMS = [
  { icon: 'ph-certificate', title: 'Stag Do Specialists', text: "We've done this thousands of times" },
  { icon: 'ph-list-heart', title: 'Group Voted Itinerary', text: 'Built on what your mates actually want' },
  { icon: 'ph-kanban', title: 'We Handle Everything', text: 'Booking, logistics, support' },
  { icon: 'ph-headset', title: 'Real Human Support', text: 'WhatsApp & chat, 7 days a week' },
];

const STEPS = [
  { title: 'Define your stag style', text: 'Wild or classy, chill or adrenaline', img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.38.35.png', objectPosition: 'top' },
  { title: 'Handpick the shortlist', text: 'Pick what the group gets to vote on', img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.52.51.jpg', objectPosition: 'center' },
  { title: 'Send the vote link', text: 'Your mates pick their favourites', img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.55.15.png', objectPosition: 'center' },
  { title: 'Review & confirm', text: 'Add, remove or tweak before you book', img: 'https://cdn.jsdelivr.net/gh/cyrudi/sandbox@main/Screenshot%202026-06-19%20at%2017.40.55.jpg', objectPosition: 'left top' },
];

const REVIEWS = [
  { quote: "Easiest stag do I've ever organised. The lads voted, Trivlu sorted the rest — all I did was show up.", name: 'James W.', country: 'United Kingdom' },
  { quote: 'Booked shooting, karting and a boat party for 14 guys. Zero chaos, brilliant weekend.', name: 'Connor M.', country: 'Ireland' },
  { quote: 'The group vote ended every argument in the group chat. 10/10, would use again.', name: 'Mark D.', country: 'United Kingdom' },
  { quote: 'Great communication and the itinerary was spot on. The deposit system made paying painless.', name: 'Tom V.', country: 'Netherlands' },
];

const MAX_FEATURED = 12;

async function loadFeatured(): Promise<{ activities: Activity[]; catalogSlug: string }> {
  try {
    const destinations = (await api.getDestinations()) ?? [];
    const main = destinations.find((d) => d.slug === DEFAULT_DESTINATION_SLUG) ?? destinations[0];
    let activities = (await api.getFeaturedActivities().catch(() => null)) ?? [];
    if (activities.length === 0 && main) {
      activities = (await api.getActivities(main.id)) ?? [];
    }
    return { activities: activities.slice(0, MAX_FEATURED), catalogSlug: main?.slug ?? DEFAULT_DESTINATION_SLUG };
  } catch {
    // Backend unreachable: render the page without the grid rather than 500.
    return { activities: [], catalogSlug: DEFAULT_DESTINATION_SLUG };
  }
}

export default async function HomePage() {
  const { activities, catalogSlug } = await loadFeatured();

  const organizationJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Trivlu',
    url: SITE_URL,
    logo: `${SITE_URL}/logo-trivlu.png`,
    sameAs: [WHATSAPP_URL],
  };

  return (
    <div className="homepage">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: jsonLd(organizationJsonLd) }}
      />

      <section className="hero">
        <div className="hero-overlay" />
        <div className="hero-fade" aria-hidden="true" />
        <div className="hero-content">
          <div className="hero-text">
            <h1 className="hero-title">The Easiest Stag Do Decision. All Sorted For You.</h1>
            <p className="hero-subtitle">Your mates vote in 10 minutes. We deliver the perfect weekend.</p>

            <aside className="vote-card" aria-hidden="true">
              <div className="vc-head">
                <span className="vc-badge"><i className="ph ph-check-square" /></span>
                <span className="vc-title">Vote on activities</span>
              </div>
              <div className="vc-sub">9 of 11 lads voted</div>
              {VOTE_ROWS.map((row) => (
                <div className="vc-row" key={row.name}>
                  <div className="vc-row-top">
                    <span className="vc-name"><i className={`ph ${row.icon}`} />{row.name}</span>
                    <span className="vc-num">{row.num}</span>
                  </div>
                  <div className="vc-bar">
                    <div className="vc-fill" style={{ width: `${row.pct}%`, background: row.fill }} />
                  </div>
                </div>
              ))}
            </aside>

            <div className="hero-cta-group">
              <a className="hp-btn-primary" href="/vote/new">
                <i className="ph ph-check-square" aria-hidden="true" /> Start Group Vote
              </a>
              <a className="hp-btn-secondary" href="/#activities">Explore activities</a>
            </div>
            <div className="hero-trust-line">
              <span>You pick the vibe</span>
              <span className="dot">·</span>
              <span>Lads vote</span>
              <span className="dot">·</span>
              <span>We organise it</span>
            </div>
          </div>
        </div>
      </section>

      <section className="trust-bar">
        <h2 className="trust-bar-title">Why Plan Your Prague Stag Weekend with Trivlu</h2>
        <div className="trust-bar-grid">
          {TRUST_ITEMS.map((item) => (
            <div key={item.title} className="trust-item">
              <span className="trust-icon" aria-hidden="true"><i className={`ph ${item.icon}`} /></span>
              <h3 className="trust-title">{item.title}</h3>
              <p className="trust-text">{item.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="how-it-works">
        <h2 className="section-title">The Smartest Way to Plan a Stag Do</h2>
        <p className="section-subtitle">
          Our Trip Builder uses group voting to turn everyone&apos;s preferences into one perfect stag do package.
        </p>
        <div className="how-it-works-steps">
          {STEPS.map((step, index) => (
            <div key={step.title} className="how-it-works-step">
              <div className="step-img">
                <img src={step.img} alt="" loading="lazy" style={{ objectPosition: step.objectPosition }} />
              </div>
              <div className="step-body">
                <div className="step-head">
                  <span className="step-number">{index + 1}</span>
                  <h3 className="step-title">{step.title}</h3>
                </div>
                <p className="step-text">{step.text}</p>
              </div>
            </div>
          ))}
        </div>
        <a className="btn btn--primary btn--lg" href="/vote/new">
          <i className="ph ph-check-square" aria-hidden="true" /> Start Group Vote
        </a>
      </section>

      {activities.length > 0 && (
        <section className="featured-activities" id="activities">
          <h2 className="section-title">70+ Activities. Something for Every Group.</h2>
          <p className="section-subtitle">
            From tank driving to strip clubs and spa — we&apos;ve got every stag style covered.
          </p>
          <div className="featured-activities-grid">
            {activities.map((activity) => (
              <ActivityCardStatic key={activity.id} activity={activity} />
            ))}
          </div>
          <Link href={`/destination/${catalogSlug}`} className="btn btn--lg featured-activities-cta">
            View All Activities
          </Link>
        </section>
      )}

      <section className="reviews-section">
        <h2 className="section-title reviews-title">What the Lads Say</h2>
        <p className="section-subtitle reviews-subtitle">Real reviews from real stag dos.</p>
        <div className="reviews-grid">
          {REVIEWS.map((review) => (
            <div key={review.name} className="review-card">
              <div className="review-stars" role="img" aria-label="5 out of 5 stars">★★★★★</div>
              <blockquote className="review-quote">&quot;{review.quote}&quot;</blockquote>
              <div className="review-author">
                <span className="review-avatar" aria-hidden="true">
                  {review.name.split(' ').map((p) => p[0]).join('').toUpperCase()}
                </span>
                <div>
                  <div className="review-name">{review.name}</div>
                  <div className="review-country">{review.country}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
        <a className="btn btn--primary btn--lg" href="/vote/new">Build Your Trip</a>
      </section>

      <section className="contact-cta">
        <div className="contact-cta-card">
          <div className="contact-cta-text">
            <h2 className="contact-cta-title">We&apos;re just a message away</h2>
            <p className="contact-cta-sub">
              Chat with our team on WhatsApp — ask anything, we&apos;ll help plan the perfect stag do.
            </p>
            <div className="contact-cta-wa-wrap">
              <a className="contact-cta-wa" href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">
                <i className="ph ph-whatsapp-logo" aria-hidden="true" /> Chat on WhatsApp
              </a>
            </div>
          </div>
          <div className="contact-cta-img" aria-hidden="true" />
        </div>
      </section>
    </div>
  );
}
