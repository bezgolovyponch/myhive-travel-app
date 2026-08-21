// "72 activities. Pick the ones your group wants" — one horizontally
// scrollable row per category, six cards deep. Two card modes:
//   - link (Prague landing): "Add to trip" navigates to the trip builder
//   - pick (home landing): the button toggles the activity into the shared
//     shortlist without leaving the page
import type { ActivityRow, LandingActivity } from './data';
import { BUILDER_URL, categoryLink } from './data';

function ActivityCard({
  a,
  showChip,
  picked,
  onToggle,
  onAddToTrip,
}: {
  a: LandingActivity;
  showChip: boolean;
  picked?: boolean;
  onToggle?: (slug: string) => void;
  onAddToTrip?: (a: LandingActivity) => void;
}) {
  return (
    <article className={`acard${picked ? ' is-picked' : ''}`}>
      <div className="acard__ph">
        {a.imageUrl ? (
          <img src={a.imageUrl} alt={a.name} loading="lazy" decoding="async" />
        ) : (
          <span className="acard__noimg">Photo coming soon</span>
        )}
        {showChip && a.category ? <span className="acard__cat">{a.category}</span> : null}
      </div>
      <div className="acard__bd">
        <h3 className="acard__nm" title={a.name}>
          {a.name}
        </h3>
        <div className="acard__dur">{a.durationLabel ?? ' '}</div>
        <div className="acard__pr">
          {a.hasGroupMin ? 'from ' : ''}€{a.price} <span>/ person</span>
        </div>
        <div className="acard__min">{a.minPrice ? `min €${a.minPrice} per group` : ' '}</div>
        <div className="acard__row">
          {onToggle ? (
            <button
              type="button"
              className="btn btn--primary js-add-trip"
              aria-pressed={picked ? 'true' : 'false'}
              onClick={() => onToggle(a.slug)}
            >
              {picked ? 'Added ✓' : 'Add to trip'}
            </button>
          ) : (
            <a
              className="btn btn--primary"
              href={`${BUILDER_URL}&add=${a.slug}`}
              onClick={(e) => {
                if (!onAddToTrip) return;
                e.preventDefault();
                onAddToTrip(a);
              }}
            >
              Add to trip
            </a>
          )}
        </div>
      </div>
    </article>
  );
}

export default function ActivityRows({
  rows,
  destinationSlug,
  showChip = false,
  picked,
  onToggle,
  onAddToTrip,
}: {
  rows: ActivityRow[];
  destinationSlug: string;
  showChip?: boolean;
  picked?: string[];
  onToggle?: (slug: string) => void;
  onAddToTrip?: (a: LandingActivity) => void;
}) {
  return (
    <div>
      {rows.map((row) => (
        <section className="arow" key={row.name}>
          <div className="arow__hd">
            <h3 className="arow__t">
              <a className="arow__l" href={categoryLink(destinationSlug, row.slug)}>
                <b>{row.name}</b> <i aria-hidden="true">→</i>
              </a>
            </h3>
            <span className="arow__n">{row.total} in the catalogue</span>
          </div>
          <div className="acar">
            {row.items.map((a) => (
              <ActivityCard
                key={a.slug}
                a={a}
                showChip={showChip}
                picked={picked?.includes(a.slug)}
                onToggle={onToggle}
                onAddToTrip={onAddToTrip}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
