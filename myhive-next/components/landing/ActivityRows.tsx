'use client';

// "72 activities. Pick the ones your group wants" — one horizontally
// scrollable row per category, six cards deep. Two card modes:
//   - link (Prague landing): "Add to trip" navigates to the trip builder
//   - pick (vote landing): the button toggles the activity into the shared
//     shortlist without leaving the page
// Activity names/categories arrive already localized from the backend
// (?locale= on the server fetch); UI strings come from the landing dictionary.
import { useT, useLocalePath } from '../../legacy-src/i18n';
import type { ActivityRow, LandingActivity } from './data';
import { BUILDER_URL, categoryLink } from './data';

function ActivityCard({
  a,
  showChip,
  picked,
  onToggle,
}: {
  a: LandingActivity;
  showChip: boolean;
  picked?: boolean;
  onToggle?: (slug: string) => void;
}) {
  const t = useT('landing.activities');
  const lp = useLocalePath();
  return (
    <article className={`acard${picked ? ' is-picked' : ''}`}>
      <div className="acard__ph">
        {a.imageUrl ? (
          <img src={a.imageUrl} alt={a.name} loading="lazy" decoding="async" />
        ) : (
          <span className="acard__noimg">{t('photoComing')}</span>
        )}
        {showChip && a.category ? <span className="acard__cat">{a.category}</span> : null}
      </div>
      <div className="acard__bd">
        <h3 className="acard__nm" title={a.name}>
          {a.name}
        </h3>
        <div className="acard__dur">{a.durationLabel ?? ' '}</div>
        <div className="acard__pr">
          {a.hasGroupMin ? `${t('from')} ` : ''}€{a.price} <span>{t('perPerson')}</span>
        </div>
        <div className="acard__min">
          {a.minPrice ? t('minPerGroup', { min: a.minPrice }) : ' '}
        </div>
        <div className="acard__row">
          {onToggle ? (
            <button
              type="button"
              className="btn btn--primary js-add-trip"
              aria-pressed={picked ? 'true' : 'false'}
              onClick={() => onToggle(a.slug)}
            >
              {picked ? t('added') : t('addToTrip')}
            </button>
          ) : (
            <a className="btn btn--primary" href={lp(`${BUILDER_URL}&add=${a.slug}`)}>
              {t('addToTrip')}
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
}: {
  rows: ActivityRow[];
  destinationSlug: string;
  showChip?: boolean;
  picked?: string[];
  onToggle?: (slug: string) => void;
}) {
  const t = useT('landing.activities');
  const tRows = useT('landing.rows');
  const lp = useLocalePath();
  // useT falls back to the key path for unknown keys; a future category the
  // dictionary doesn't know yet renders its live localized name instead.
  const rowLabel = (row: ActivityRow) => {
    const label = tRows(row.slug);
    return label === `landing.rows.${row.slug}` ? row.liveName || row.slug : label;
  };
  return (
    <div>
      {rows.map((row) => (
        <section className="arow" key={row.slug}>
          <div className="arow__hd">
            <h3 className="arow__t">
              <a className="arow__l" href={lp(categoryLink(destinationSlug, row.slug))}>
                <b>{rowLabel(row)}</b> <i aria-hidden="true">→</i>
              </a>
            </h3>
            <span className="arow__n">{t('inCatalogue', { count: row.total })}</span>
          </div>
          <div className="acar">
            {row.items.map((a) => (
              <ActivityCard
                key={a.slug}
                a={a}
                showChip={showChip}
                picked={picked?.includes(a.slug)}
                onToggle={onToggle}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
