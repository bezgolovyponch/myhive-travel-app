// SSR contact page shell — mirrors legacy-src/pages/ContactPage.js markup/classes
// for the static parts (hero, info card, contact details). The interactive form
// (fields + Turnstile + submit) is a client island loaded via next/dynamic so the
// page shell stays a Server Component with real HTML for crawlers.
import dynamic from 'next/dynamic';
import { WHATSAPP_URL, pageMetadata } from '../../../lib/seo';
import '../../../legacy-src/pages/ContactPage.css';

const ContactFormIsland = dynamic(() => import('../../../components/site/ContactFormIsland'));

const TITLE = 'Contact Trivlu — Talk to the Team';
const DESCRIPTION =
  "Questions about your stag do or booking? Reach the Trivlu team by WhatsApp, Messenger or email — we're quick to reply.";

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/contact',
});

export default function ContactPage() {
  return (
    <div className="contact-page">
      <section className="page-hero">
        <h1>Contact Us</h1>
        <p>Have a question or want to plan a group trip? We&apos;d love to hear from you.</p>
      </section>

      <div className="contact-layout">
        <div className="contact-info">
          <div className="contact-info-card">
            <h3>Get in Touch</h3>
            <p>
              Whether you have a question about destinations, pricing, or anything else, our team is
              ready to help.
            </p>

            <div className="contact-details">
              <div className="contact-detail-item">
                <span className="contact-detail-label">Email</span>
                <a href="mailto:info@trivlu.com">info@trivlu.com</a>
              </div>
              <div className="contact-detail-item">
                <span className="contact-detail-label">Response Time</span>
                <span>Within 24 hours</span>
              </div>
              <div className="contact-detail-item">
                <span className="contact-detail-label">Company</span>
                <span>Pragout group s.r.o.</span>
              </div>
              <div className="contact-detail-item">
                <span className="contact-detail-label">Address</span>
                <span>Na Folimance 2155/15, Vinohrady, 120 00 Prague 2, Czech Republic</span>
              </div>
              <div className="contact-detail-item">
                <span className="contact-detail-label">WhatsApp</span>
                <a href={WHATSAPP_URL} target="_blank" rel="noopener noreferrer">
                  Chat on WhatsApp
                </a>
              </div>
            </div>
          </div>
        </div>

        <div className="contact-form-section">
          <ContactFormIsland />
        </div>
      </div>
    </div>
  );
}
