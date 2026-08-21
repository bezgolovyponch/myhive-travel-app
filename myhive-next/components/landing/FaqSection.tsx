// "The things people worry about" — native <details> accordions, so the FAQ
// works before (and without) hydration.
import type { ReactNode } from 'react';

export interface FaqItem {
  q: string;
  a: ReactNode; // one or more <p className="faq__a"> blocks
  open?: boolean;
}

export default function FaqSection({ eyebrow, items }: { eyebrow?: string; items: FaqItem[] }) {
  return (
    <section id="faq">
      <div className="shell">
        {eyebrow ? <p className="t-eyebrow">{eyebrow}</p> : null}
        <h2 className="t-h2">The things people worry about</h2>
        <div className="faq">
          {items.map((item) => (
            <details key={item.q} open={item.open}>
              <summary>{item.q}</summary>
              {item.a}
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
