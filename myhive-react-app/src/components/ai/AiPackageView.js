import {useT} from '../../i18n';
import {addDays, formatAmount, formatDayLabel, formatDayRange} from '../../utils/format';
import './AiPackageView.css';

// The result canvas (v3 landing, 2e): once the planner has packages they ARE
// the page and the chat sits in the dock below. One trim at a time — BASIC /
// MEDIUM / PREMIUM as tabs, MEDIUM the planner's pick. No prices on purpose:
// the Prague planner quotes one price on the call.

export const AI_PICK_KEY = 'MEDIUM';
export const TIER_KEYS = ['BASIC', 'MEDIUM', 'PREMIUM'];

export const activityCount = (pkg) => pkg.days.reduce((n, day) => n + day.items.length, 0);

// "from €4,584": the package's group total — the number the cart shows once it
// is picked — or null when there is none. Whole euros: cents read as a quote.
// "from €X": the group total less the fixed margin, calculated on the backend.
export function priceFrom(t, pkg) {
    const n = Number(pkg.fromPrice);
    if (pkg.fromPrice == null || !Number.isFinite(n) || n <= 0) return null;
    return t('result.priceFrom', {price: formatAmount(Math.round(n))});
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

/**
 * @param startDate  the trip's first day (a Date) when its dates match the plan, else null
 * @param removing   name of the activity whose removal is in flight, if any
 * @param onRemove   (pkg, item) => void — asks the planner to drop it (a chat edit)
 * @param onAskAi    () => void — opens the chat (the "free time" row)
 * @param onUndo     (packageKey) => void — back to the generation before the edit
 * @param busy       a chat turn is in flight: edits wait for it
 */
function AiPackageView({generation, destinationName, startDate, activeKey, onTierChange, onRemove, removing, onAskAi, onUndo, busy}) {
    const t = useT('aiPlanner');
    const packages = generation.packages;
    const pkg = packages.find((p) => p.key === activeKey) || packages[0];
    const brief = generation.brief || {};
    const added = addedNames(generation, pkg.key);
    const pending = generation.textsPending;
    const change = lastChange(generation);

    return (
        <div className="aip-view">
            <div className="aip-eyebrow">{t('result.eyebrow')}</div>
            <h1 className="aip-title">
                {destinationName ? `${destinationName} · ` : ''}{t(`tiers.${pkg.key}`)}
            </h1>
            <div className="aip-meta">
                {[
                    brief.groupSize && t('result.people', {count: brief.groupSize}),
                    startDate ? formatDayRange(startDate, addDays(startDate, brief.days - 1))
                        : brief.days && plural(t, 'result.days', brief.days),
                    plural(t, 'result.activities', activityCount(pkg)),
                ].filter(Boolean).join(' · ')}
            </div>

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
                        {priceFrom(t, p) && <span className="aip-tier-price">{priceFrom(t, p)}</span>}
                    </button>
                ))}
            </div>
            {!pending && pkg.tagline && <p className="aip-tagline">{pkg.tagline}</p>}

            {pkg.days.map((day) => (
                <section key={day.dayNumber} className="aip-day">
                    <div className="aip-day-head">
                        <h2 className="aip-day-title">
                            {startDate ? formatDayLabel(addDays(startDate, day.dayNumber - 1))
                                : t('result.day', {n: day.dayNumber})}
                            {day.title && !pending && <span> · {day.title}</span>}
                        </h2>
                        <span className="aip-day-count">{plural(t, 'result.activities', day.items.length)}</span>
                    </div>
                    {day.items.length === 0 && (
                        <button type="button" className="aip-free" onClick={onAskAi}>
                            {t('result.freeTime')}
                        </button>
                    )}
                    {day.items.map((item) => {
                        const isAdded = added.has(item.name);
                        const isRemoving = removing === item.name;
                        return (
                            <div key={`${item.activityId}-${item.slot}`}
                                 className={`aip-item ${isAdded ? 'is-added' : ''} ${isRemoving ? 'is-removing' : ''}`}>
                                <div className="aip-item-time">{item.startHint || t(`slots.${item.slot}`)}</div>
                                <div className="aip-item-body">
                                    <div className="aip-item-name">
                                        {item.name}
                                        {isAdded && <span className="aip-badge">{t('result.aiAdded')}</span>}
                                    </div>
                                    <div className="aip-item-sub">
                                        {isRemoving ? t('result.removing')
                                            : pending ? t('result.writing') : (item.why || item.includes)}
                                    </div>
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
            ))}

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

// The same three trims as cards inside the chat (v3 landing, 2d): tapping one
// switches the package behind the drawer.
export function TrimCards({generation, activeKey, onTierChange}) {
    const t = useT('aiPlanner');
    return (
        <div className="aip-trim-cards" role="group" aria-label={t('result.tiersAria')}>
            {generation.packages.map((p) => (
                <button
                    key={p.key}
                    type="button"
                    aria-pressed={p.key === activeKey}
                    className={`aip-trim-card ${p.key === activeKey ? 'is-active' : ''}`}
                    onClick={() => onTierChange(p.key)}
                >
                    <b>{t(`tiers.${p.key}`)}</b>
                    <span>{t(`tierTags.${p.key}`)}</span>
                    <em>{plural(t, 'result.activities', activityCount(p))}</em>
                    {priceFrom(t, p) && <strong>{priceFrom(t, p)}</strong>}
                </button>
            ))}
        </div>
    );
}

export default AiPackageView;
