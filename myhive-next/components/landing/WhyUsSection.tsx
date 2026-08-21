'use client';

// "New company. Experienced team" — the story plus the seller card with the
// direct lines. Copy variants differ slightly between the two landings.
import { PHONE_DISPLAY, PHONE_HREF, WHATSAPP_HREF } from './data';
import { trackCta } from './analytics';

const SELLER_AVATAR = 'https://img.trivlu.com/hero_stag_do_prague.png';

export default function WhyUsSection({
  eyebrow,
  paragraphs,
  guarantees,
  block,
}: {
  eyebrow?: string;
  paragraphs: string[];
  guarantees: string[];
  block: string;
}) {
  return (
    <section id="why">
      <div className="shell">
        {eyebrow ? <p className="t-eyebrow">{eyebrow}</p> : null}
        <h2 className="t-h2">New company. Experienced team</h2>
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
              <img className="seller__av" src={SELLER_AVATAR} alt="" aria-hidden="true" loading="lazy" decoding="async" />
              <div>
                <div className="seller__nm">Trivlu team</div>
                <div className="seller__rl">Your trip planner in Prague</div>
                <div className="seller__live">
                  <span className="pulse" /> Replies in 10 minutes · 7 days a week
                </div>
              </div>
            </div>
            <div className="seller__row">
              <a
                className="btn btn--primary"
                href={PHONE_HREF}
                onClick={() => trackCta('Call now', block)}
              >
                Call now
              </a>
              <a
                className="btn btn--wa"
                href={WHATSAPP_HREF}
                target="_blank"
                rel="noopener"
                onClick={() => trackCta('WhatsApp', block)}
              >
                WhatsApp
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
