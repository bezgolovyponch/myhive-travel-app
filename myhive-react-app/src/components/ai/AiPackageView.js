import {useLocalePath, useT} from '../../i18n';
import {addDays, formatAmount, formatDayLabel, formatDayRange} from '../../utils/format';
import './AiPackageView.css';

// The result canvas: once the planner has packages they ARE the page and the
// chat sits docked below. One trim at a time — BASIC / MEDIUM / PREMIUM as tabs,
// MEDIUM the planner's pick — until the organizer edits; then it is one draft.
// No per-activity prices on purpose: one "from" price for the whole group.

export const AI_PICK_KEY = 'MEDIUM';
// "from €146 / person": one traveller's share of the package's "from" price,
// calculated on the backend, or null when there is none. Whole euros: cents
// read as a quote. Packages stored before the share existed show the group's.
export function priceFrom(t, pkg, perPersonKey = 'result.priceFromPerPerson') {
    const shown = (value, key) => {
        const n = Number(value);
        if (value == null || !Number.isFinite(n) || n <= 0) return null;
        return t(key, {price: formatAmount(Math.round(n))});
    };
    return shown(pkg.fromPricePerPerson, perPersonKey) || shown(pkg.fromPrice, 'result.priceFrom');
}

// useT has no plurals: count keys are {one, other} objects.
export const plural = (t, key, count) => t(`${key}.${count === 1 ? 'one' : 'other'}`, {count});

// What the newest edit did, for the toast: the first applied op is enough.
function lastChange(generation) {
    if (generation.kind !== 'EDITED') return null;
    return generation.editReport?.applied?.[0] || null;
}

// Names the latest edit put into this package, for the "AI added" badge.
function addedNames(generation, packageKey) {
    const applied = generation.editReport?.applied || [];
    return new Set(applied
        .filter((op) => op.packageKey === packageKey && op.op !== 'REMOVE')
        .map((op) => (op.op === 'REPLACE' ? op.replacement : op.activity)));
}

// The backend's placeholder for a day title (PlaceholderTexts.dayTitle): "Day 3" / "Tag 3". It is copy
// that never came, not a name, and the day's own label already says it.
const STOCK_DAY_TITLE = /^\s*(day|tag)\s*\d+\s*$/i;

function isStockDayTitle(title) {
    return STOCK_DAY_TITLE.test(title);
}

// "2 h", "1 h 30 min", "45 min": how long an activity takes, short enough for a row.
export function duration(minutes) {
    if (!minutes) return null;
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return [hours && `${hours} h`, rest && `${rest} min`].filter(Boolean).join(' ');
}

/**
 * The trip draft: what the group gets, day by day - the date (or "Day N") and
 * the planner's name for the day over its rows, an empty day kept on the page -
 * with the three trims above it until the organizer starts changing things in
 * the chat. From then on it is their own draft and the trims are gone.
 *
 * @param startDate  the trip's first day (a Date) when its dates match the plan, else null
 * @param custom     the organizer has started editing: no trims, just the draft
 * @param removing   name of the activity whose removal is in flight, if any
 * @param onOpen     (item) => void — shows the activity's card
 * @param onRemove   (pkg, item) => void — asks the planner to drop it (a chat edit)
 * @param onUndo     (packageKey) => void — back to the generation before the edit
 * @param busy       a chat turn is in flight: edits wait for it
 * @param cta        rendered under the draft (the "Ask the group" button)
 */
function AiPackageView({
    generation, destinationName, destinationSlug, startDate, activeKey, onTierChange, custom, onOpen, onRemove, removing,
    onUndo, busy, cta,
}) {
    const t = useT('aiPlanner');
    const lp = useLocalePath();
    const packages = generation.packages;
    const pkg = packages.find((p) => p.key === activeKey) || packages[0];
    const brief = generation.brief || {};
    const added = addedNames(generation, pkg.key);
    const pending = generation.textsPending;
    const change = lastChange(generation);
    const total = pkg.days.reduce((n, day) => n + day.items.length, 0);

    return (
        <div className="aip-view">
            <div className="aip-head">
                <div>
                    <h1 className="aip-title">
                        {destinationName ? t('result.title', {destination: destinationName}) : t('result.eyebrow')}
                    </h1>
                    <div className="aip-meta">
                        {[
                            startDate ? formatDayRange(startDate, addDays(startDate, brief.days - 1))
                                : brief.days && plural(t, 'result.days', brief.days),
                            brief.groupSize && t('result.people', {count: brief.groupSize}),
                        ].filter(Boolean).join(' · ')}
                    </div>
                </div>
                {destinationSlug && (
                    <a className="aip-browse" href={lp(`/destination/${destinationSlug}?tab=activities`)}
                       target="_blank" rel="noopener noreferrer">
                        {t('result.browseAll')} <span aria-hidden="true">↗</span>
                    </a>
                )}
            </div>

            {!custom && (
                <div className="aip-tiers" role="tablist" aria-label={t('result.tiersAria')}>
                    {packages.map((p) => (
                        <button
                            key={p.key}
                            type="button"
                            role="tab"
                            aria-selected={p.key === pkg.key}
                            className={`aip-tier ${p.key === pkg.key ? 'is-active' : ''}`}
                            onClick={() => onTierChange(p.key)}
                        >
                            <span className="aip-tier-name">{t(`tiers.${p.key}`)}</span>
                            <span className="aip-tier-tag">{t(`tierTags.${p.key}`)}</span>
                            {priceFrom(t, p) && (
                                // The tab is a third of a phone wide: the amount, then "per person" under it.
                                <span className="aip-tier-price">
                                    {priceFrom(t, p, 'result.priceFrom')}
                                    {p.fromPricePerPerson != null && <small>{t('result.perPerson')}</small>}
                                </span>
                            )}
                        </button>
                    ))}
                </div>
            )}

            <section className="aip-draft" aria-label={t('result.draftLabel')}>
                <div className="aip-draft-head">
                    <span className="aip-draft-label">{t('result.draftLabel')}</span>
                    <span>{plural(t, 'result.activities', total)}</span>
                </div>
                {pkg.days.map((day) => {
                    // The day only, never an hour or a part of the day: the plan is a wish list for
                    // the group, and the planner sets the times after the vote.
                    const label = startDate ? formatDayLabel(addDays(startDate, day.dayNumber - 1))
                        : t('result.day', {n: day.dayNumber});
                    const headingId = `aip-day-${pkg.key}-${day.dayNumber}`;
                    // The planner's name for the day, unless it is still the stock "Day N"/"Tag N" - the
                    // placeholder stays past textsPending when a package's copy failed, the model left
                    // the title blank, or an edit reset it - which would read "Day 1 · Day 1".
                    const dayName = day.title && !pending && !isStockDayTitle(day.title) ? day.title : null;
                    return (
                        <section key={day.dayNumber} className="aip-day" aria-labelledby={headingId}>
                            <div className="aip-day-head">
                                <h2 id={headingId} className="aip-day-title">
                                    {label}
                                    {dayName && <span className="aip-day-name"> · {dayName}</span>}
                                </h2>
                                {day.items.length > 0 && (
                                    <span className="aip-day-count">{plural(t, 'result.activities', day.items.length)}</span>
                                )}
                            </div>
                            {/* An empty day stays on the page: a two-day trip must never read as one. The
                                line says it; a "0 activities" count next to it would say it twice. */}
                            {day.items.length === 0 && <div className="aip-day-empty">{t('result.emptyDay')}</div>}
                            {day.items.map((item) => {
                                const isAdded = added.has(item.name);
                                const isRemoving = removing === item.name;
                                const sub = isRemoving ? t('result.removing') : duration(item.durationMinutes);
                                return (
                                    <div key={`${item.activityId}-${item.slot}`}
                                         className={`aip-item ${isAdded ? 'is-added' : ''} ${isRemoving ? 'is-removing' : ''}`}>
                                        <span className="aip-item-thumb" aria-hidden="true">
                                            {item.imageUrl ? <img src={item.imageUrl} alt="" loading="lazy"/> : item.name.charAt(0)}
                                        </span>
                                        <div className="aip-item-body">
                                            <div className="aip-item-name">
                                                {onOpen ? (
                                                    <button type="button" className="activity-open" aria-haspopup="dialog"
                                                            onClick={() => onOpen(item)}>
                                                        {item.name}
                                                    </button>
                                                ) : item.name}
                                                {isAdded && <span className="aip-badge">{t('result.aiAdded')}</span>}
                                            </div>
                                            {sub && <div className="aip-item-sub">{sub}</div>}
                                        </div>
                                        <button
                                            type="button"
                                            className="aip-remove"
                                            aria-label={t('result.removeAria', {name: item.name})}
                                            onClick={() => onRemove(pkg, item)}
                                            disabled={busy || pending}
                                        >
                                            <i className="ph ph-x" aria-hidden="true"/>
                                        </button>
                                    </div>
                                );
                            })}
                        </section>
                    );
                })}
                <div className="aip-draft-foot">
                    <span>{brief.groupSize ? t('result.people', {count: brief.groupSize}) : ''}</span>
                    {priceFrom(t, pkg) && <strong>{priceFrom(t, pkg)}</strong>}
                </div>
            </section>

            {cta}

            {generation.degraded && <div className="aip-hint">{t('result.degraded')}</div>}

            {change && generation.parentId && (
                <div className="aip-toast" role="status">
                    <span>{t(`result.changed.${change.op}`, {
                        name: change.activity, replacement: change.replacement,
                    })}</span>
                    <button type="button" onClick={() => onUndo(pkg.key)} disabled={busy}>{t('result.undo')}</button>
                </div>
            )}
        </div>
    );
}

export default AiPackageView;
