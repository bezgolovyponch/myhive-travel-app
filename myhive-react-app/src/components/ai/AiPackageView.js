import {useRef, useState} from 'react';
import {useLocalePath, useT} from '../../i18n';
import {addDays, formatAmount, formatDayLabel, formatDayRange, formatDuration} from '../../utils/format';
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
        .filter((op) => op.packageKey === packageKey && op.op !== 'REMOVE' && op.op !== 'MOVE')
        .map((op) => (op.op === 'REPLACE' ? op.replacement : op.activity)));
}

/**
 * The trip draft: one flat list of what the group gets, with the three trims
 * above it until the organizer starts changing things in the chat. From then
 * on it is their own draft and the trims are gone.
 *
 * @param startDate  the trip's first day (a Date) when its dates match the plan, else null
 * @param custom     the organizer has started editing: no trims, just the draft
 * @param removing   name of the activity whose removal is in flight, if any
 * @param onOpen     (item) => void — shows the activity's card
 * @param onRemove   (pkg, item) => void — asks the planner to drop it (a chat edit)
 * @param onMove     (pkg, item, dayNumber) => void — the row was dragged onto another day
 * @param onUndo     (packageKey) => void — back to the generation before the edit
 * @param busy       a chat turn is in flight: edits wait for it
 * @param cta        rendered under the draft (the "Ask the group" button)
 */
function AiPackageView({
    generation, destinationName, destinationSlug, startDate, activeKey, onTierChange, custom, onOpen, onRemove, onMove, removing,
    onUndo, busy, cta,
}) {
    const t = useT('aiPlanner');
    const tDuration = useT('activityDetail.duration');
    const lp = useLocalePath();
    const packages = generation.packages;
    const pkg = packages.find((p) => p.key === activeKey) || packages[0];
    const brief = generation.brief || {};
    const added = addedNames(generation, pkg.key);
    const pending = generation.textsPending;
    const change = lastChange(generation);
    const rows = pkg.days.flatMap((day) => day.items.map((item) => ({day, item})));
    // A trip of several days is laid out day by day, and a row can be dragged by its handle onto
    // another day. One day has nowhere to move to: a flat list, no handles.
    const byDay = pkg.days.length > 1 && Boolean(onMove);
    const dayLabel = (day) => (startDate ? formatDayLabel(addDays(startDate, day.dayNumber - 1))
        : t('result.day', {n: day.dayNumber}));
    // {activityId, fromDay, startY, dy, overDay} while a row is being dragged.
    const [drag, setDrag] = useState(null);
    const dragRef = useRef(null);
    // By where the day blocks lie, not by what is under the pointer: under the pointer is the row
    // being dragged, which still belongs to the day it came from.
    const draftRef = useRef(null);
    const dayUnder = (y) => {
        const blocks = [...(draftRef.current?.querySelectorAll('[data-day]') || [])];
        const hit = blocks.find((el) => {
            const box = el.getBoundingClientRect();
            return y >= box.top && y <= box.bottom;
        });
        return hit ? Number(hit.getAttribute('data-day')) : null;
    };
    const startDrag = (e, day, item) => {
        if (busy || pending) return;
        e.preventDefault();
        e.currentTarget.setPointerCapture?.(e.pointerId);
        dragRef.current = {activityId: item.activityId, fromDay: day.dayNumber, startY: e.clientY, dy: 0,
            overDay: day.dayNumber};
        setDrag(dragRef.current);
    };
    const moveDrag = (e) => {
        if (!dragRef.current) return;
        dragRef.current = {...dragRef.current, dy: e.clientY - dragRef.current.startY,
            overDay: dayUnder(e.clientY) ?? dragRef.current.overDay};
        setDrag(dragRef.current);
    };
    const endDrag = (item) => {
        const done = dragRef.current;
        dragRef.current = null;
        setDrag(null);
        if (done && done.overDay !== done.fromDay) onMove(pkg, item, done.overDay);
    };
    // The same move without a pointer: the arrow keys on the handle step the activity a day back or on.
    const keyMove = (e, day, item) => {
        const step = e.key === 'ArrowUp' ? -1 : e.key === 'ArrowDown' ? 1 : 0;
        const target = pkg.days.find((d) => d.dayNumber === day.dayNumber + step);
        if (!step || !target || busy || pending) return;
        e.preventDefault();
        onMove(pkg, item, target.dayNumber);
    };

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

            <section className="aip-draft" aria-label={t('result.draftLabel')} ref={draftRef}>
                <div className="aip-draft-head">
                    <span className="aip-draft-label">{t('result.draftLabel')}</span>
                    <span>{plural(t, 'result.activities', rows.length)}</span>
                </div>
                {pkg.days.map((day) => (byDay || day.items.length > 0) && (
                    <div key={day.dayNumber} data-day={byDay ? day.dayNumber : undefined}
                         className={`aip-day${drag && drag.overDay === day.dayNumber && drag.fromDay !== day.dayNumber ? ' is-over' : ''}`}>
                        {byDay && <div className="aip-day-head">{dayLabel(day)}</div>}
                        {byDay && day.items.length === 0 && <div className="aip-day-empty">{t('result.dayEmpty')}</div>}
                        {day.items.map((item) => {
                            const isAdded = added.has(item.name);
                            const isRemoving = removing === item.name;
                            const dragging = drag?.activityId === item.activityId;
                            return (
                                <div key={`${item.activityId}-${day.dayNumber}-${item.slot}`}
                                     className={`aip-item ${isAdded ? 'is-added' : ''} ${isRemoving ? 'is-removing' : ''}${dragging ? ' is-dragging' : ''}`}
                                     style={dragging ? {transform: `translateY(${drag.dy}px)`} : undefined}>
                                    {byDay && (
                                        <button
                                            type="button"
                                            className="aip-grip"
                                            aria-label={t('result.moveAria', {name: item.name})}
                                            disabled={busy || pending}
                                            onPointerDown={(e) => startDrag(e, day, item)}
                                            onPointerMove={moveDrag}
                                            onPointerUp={() => endDrag(item)}
                                            onPointerCancel={() => { dragRef.current = null; setDrag(null); }}
                                            onKeyDown={(e) => keyMove(e, day, item)}
                                        >
                                            <i className="ph ph-dots-six-vertical" aria-hidden="true"/>
                                        </button>
                                    )}
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
                                        {/* The day only, never an hour: the planner sets the times after the
                                            vote. Under a day's own heading the day is not said again. */}
                                        <div className="aip-item-sub">
                                            {isRemoving ? t('result.removing')
                                                : [formatDuration(item.durationMinutes, tDuration), !byDay && dayLabel(day)]
                                                    .filter(Boolean).join(' · ')}
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
                    </div>
                ))}
                <div className="aip-draft-foot">
                    <span>{brief.groupSize ? t('result.people', {count: brief.groupSize}) : ''}</span>
                    {priceFrom(t, pkg) && <strong>{priceFrom(t, pkg)}</strong>}
                </div>
            </section>

            {/* What the last change was, with the way back - in the page's flow, between the plan and its
                buttons, so it never lies on top of them. */}
            {change && generation.parentId && (
                <div className="aip-toast" role="status">
                    <span>{t(`result.changed.${change.op}`, {
                        name: change.activity, replacement: change.replacement,
                    })}</span>
                    <button type="button" onClick={() => onUndo(pkg.key)} disabled={busy}>{t('result.undo')}</button>
                </div>
            )}
            {cta}

            {generation.degraded && <div className="aip-hint">{t('result.degraded')}</div>}

        </div>
    );
}

export default AiPackageView;
