// SSR About page — content ported verbatim from legacy-src/pages/AboutPage.js
// as a static Server Component.
import { pageMetadata } from '../../../lib/seo';
import '../../../legacy-src/pages/AboutPage.css';

const TITLE = 'About Trivlu — Group Travel Made Easy';
const DESCRIPTION =
  "We built Trivlu so best men can plan a stag the whole group actually agrees on. Here's who we are and why.";

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/about',
});

export default function AboutPage() {
  return (
    <div className="about-page">
      <section className="page-hero">
        <h1>About Trivlu</h1>
        <p>Making group travel effortless, one adventure at a time.</p>
      </section>

      <section className="about-section">
        <h2>Our Mission</h2>
        <p>
          Trivlu was born from a simple frustration: planning group trips shouldn&apos;t be harder
          than the trip itself. We believe that bringing people together for shared adventures
          should be seamless, exciting, and stress-free.
        </p>
        <p>
          Our AI-powered platform takes the chaos out of coordinating multi-traveler
          experiences. From selecting destinations to building custom itineraries, Trivlu
          handles the logistics so you can focus on making memories.
        </p>
      </section>

      <section className="about-section about-values">
        <h2>What We Stand For</h2>
        <div className="values-grid">
          <div className="value-card">
            <h3>Adventure First</h3>
            <p>
              We curate destinations and activities that create unforgettable experiences for groups
              of all sizes.
            </p>
          </div>
          <div className="value-card">
            <h3>Zero Stress</h3>
            <p>
              Our smart trip builder handles the complexity of group coordination so you don&apos;t
              have to.
            </p>
          </div>
          <div className="value-card">
            <h3>Better Together</h3>
            <p>Travel is best when shared. We design every feature to bring groups closer together.</p>
          </div>
        </div>
      </section>

      <section className="about-section">
        <h2>Our Story</h2>
        <p>
          It started with a weekend trip that almost didn&apos;t happen. Too many group chats,
          conflicting preferences, and last-minute cancellations nearly derailed what should
          have been a simple getaway.
        </p>
        <p>
          That experience sparked an idea: what if there was a platform that made planning
          group adventures as fun as the adventures themselves? Trivlu is the answer — the
          first AI-powered trip maker built specifically for multi-traveler experiences.
        </p>
      </section>
    </div>
  );
}
