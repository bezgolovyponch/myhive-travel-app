// "What our groups say" — four five-star review cards.
export interface LandingReview {
  quote: string;
  name: string;
  meta: string;
  avatar: string;
}

export default function ReviewsSection({
  eyebrow,
  reviews,
}: {
  eyebrow?: string;
  reviews: LandingReview[];
}) {
  return (
    <section>
      <div className="shell">
        {eyebrow ? <p className="t-eyebrow">{eyebrow}</p> : null}
        <h2 className="t-h2">What our groups say</h2>
        <div className="revs">
          {reviews.map((r) => (
            <article className="rev" key={r.name}>
              <div className="rev__st" role="img" aria-label="5 out of 5 stars">
                ★★★★★
              </div>
              <p className="rev__q">{r.quote}</p>
              <div className="rev__w">
                <img className="rev__av" src={r.avatar} alt="" aria-hidden="true" loading="lazy" decoding="async" />
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
