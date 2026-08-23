'use client';

// "What our groups say" — four five-star review cards from the landing
// dictionary; the third review differs between the two landings.
import { useT } from '../../legacy-src/i18n';

const AVATARS = [
  'https://randomuser.me/api/portraits/men/32.jpg',
  'https://randomuser.me/api/portraits/men/44.jpg',
  'https://randomuser.me/api/portraits/men/75.jpg',
  'https://randomuser.me/api/portraits/men/11.jpg',
];

export default function ReviewsSection({ variant }: { variant: 'vote' | 'prague' }) {
  const t = useT('landing.reviews');
  const third = variant === 'vote' ? 'r3Vote' : 'r3Prague';
  const reviews = [
    { quote: t('r1Quote'), meta: t('r1Meta'), name: 'James W.' },
    { quote: t('r2Quote'), meta: t('r2Meta'), name: 'Connor M.' },
    { quote: t(`${third}Quote`), meta: t(`${third}Meta`), name: 'Mark D.' },
    { quote: t('r4Quote'), meta: t('r4Meta'), name: 'Tom V.' },
  ];

  return (
    <section>
      <div className="shell">
        {variant === 'prague' ? <p className="t-eyebrow">{t('eyebrowPrague')}</p> : null}
        <h2 className="t-h2">{t('title')}</h2>
        <div className="revs">
          {reviews.map((r, i) => (
            <article className="rev" key={r.name}>
              <div className="rev__st" role="img" aria-label={t('starsAria')}>
                ★★★★★
              </div>
              <p className="rev__q">{r.quote}</p>
              <div className="rev__w">
                <img className="rev__av" src={AVATARS[i]} alt="" aria-hidden="true" loading="lazy" decoding="async" />
                <div>
                  <div className="rev__nm">{r.name}</div>
                  <div className="rev__mt">{r.meta}</div>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
