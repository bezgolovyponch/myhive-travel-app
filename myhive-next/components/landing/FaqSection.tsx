'use client';

// "The things people worry about" — native <details> accordions built from the
// landing dictionary, so the FAQ works before (and without) hydration. The
// vote landing has eight items (first three open, one with a second paragraph
// and the refund-policy link); the Prague landing has seven (first three open).
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { PHONE_DISPLAY } from './data';

export default function FaqSection({ variant }: { variant: 'vote' | 'prague' }) {
  const t = useT('landing.faq');
  const lp = useLocalePath();
  const vars = { phone: PHONE_DISPLAY };

  const items =
    variant === 'vote'
      ? [
          {
            q: t('vote.q1'),
            open: true,
            paragraphs: [t('vote.q1a1', vars), t('vote.q1a2', vars)],
          },
          { q: t('vote.q2'), open: true, paragraphs: [t('vote.q2a', vars)] },
          {
            q: t('vote.q3'),
            open: true,
            paragraphs: [t('vote.q3a', vars)],
            link: { href: lp('/refund-policy'), label: t('vote.q3link') },
          },
          { q: t('vote.q4'), paragraphs: [t('vote.q4a', vars)] },
          { q: t('vote.q5'), paragraphs: [t('vote.q5a', vars)] },
          { q: t('vote.q6'), paragraphs: [t('vote.q6a', vars)] },
          { q: t('vote.q7'), paragraphs: [t('vote.q7a', vars)] },
          { q: t('vote.q8'), paragraphs: [t('vote.q8a', vars)] },
        ]
      : [
          { q: t('prague.q1'), open: true, paragraphs: [t('prague.q1a', vars)] },
          { q: t('prague.q2'), open: true, paragraphs: [t('prague.q2a', vars)] },
          { q: t('prague.q3'), open: true, paragraphs: [t('prague.q3a', vars)] },
          { q: t('prague.q4'), paragraphs: [t('prague.q4a', vars)] },
          { q: t('prague.q5'), paragraphs: [t('prague.q5a', vars)] },
          { q: t('prague.q6'), paragraphs: [t('prague.q6a', vars)] },
          { q: t('prague.q7'), paragraphs: [t('prague.q7a', vars)] },
        ];

  return (
    <section id="faq">
      <div className="shell">
        {variant === 'prague' ? <p className="t-eyebrow">{t('eyebrowPrague')}</p> : null}
        <h2 className="t-h2">{t('title')}</h2>
        <div className="faq">
          {items.map((item) => (
            <details key={item.q} open={item.open}>
              <summary>{item.q}</summary>
              {item.paragraphs.map((p, i) => (
                <p className="faq__a" key={i}>
                  {p}
                  {item.link && i === item.paragraphs.length - 1 ? (
                    <>
                      {' '}
                      <a href={item.link.href}>{item.link.label}</a>
                    </>
                  ) : null}
                </p>
              ))}
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
