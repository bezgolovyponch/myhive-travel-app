import {useRef, useState} from 'react';
import {useT} from '../../i18n';
import {duration} from './AiPackageView';

// "1 h 30 min · Action packed, ...": no price - the draft shows one "from" total only.
function meta(rec) {
    return [duration(rec.durationMinutes), rec.oneLine].filter(Boolean).join(' · ');
}

/** A finger has to travel this far on the header for it to be a swipe and not a tap. */
const SWIPE_PX = 24;

/**
 * The pointer is captured only once it has moved this far: a browser retargets the click to the element
 * holding the capture, so captured on pointerdown every tap on the header's buttons landed on the header
 * itself and Hide/Show did nothing (seen live). A plain click never gets this far.
 */
const CAPTURE_PX = 6;

/** The click a browser fires after a swipe arrives within this; a real tap comes later, after its own pointerdown. */
const SWIPE_TAIL_MS = 400;

/** Bottom to top: a swipe up on the header opens the next one, a swipe down the one before. */
const VIEWS = ['hidden', 'compact', 'expanded'];

/** One recommendation: picture, name (opens its card), duration, and Add - a toggle, as everywhere. */
function Card({rec, isAdded, onOpen, onToggle, pendingId, disabled}) {
    const t = useT('aiPlanner');
    const added = isAdded(rec.activityId);
    return (
        <div className="aip-suggest-card">
            <span className="aip-suggest-thumb" aria-hidden="true">
                {rec.imageUrl ? <img src={rec.imageUrl} alt="" loading="lazy"/> : rec.name.charAt(0)}
            </span>
            <div className="aip-suggest-body">
                <div className="aip-suggest-name">
                    {onOpen ? (
                        <button type="button" className="activity-open" aria-haspopup="dialog"
                                onClick={() => onOpen(rec)}>
                            {rec.name}
                        </button>
                    ) : rec.name}
                </div>
                {meta(rec) && <div className="aip-suggest-meta">{meta(rec)}</div>}
            </div>
            <button
                type="button"
                className={`aip-suggest-add${added ? ' is-added' : ''}`}
                aria-pressed={added}
                aria-label={t(added ? 'result.removeAria' : 'result.addAria', {name: rec.name})}
                disabled={disabled}
                onClick={() => onToggle(rec, added)}
            >
                {pendingId === rec.activityId ? '…' : added ? t('result.added') : t('result.add')}
            </button>
        </div>
    );
}

/**
 * What the chat recommended for the draft, as a sheet above the composer with three states: hidden
 * (one line with the count), compact (the top match as a card with Add, the rest behind "N more") and
 * expanded (every one a card, in a list that scrolls, "Show less" under it). The header is the handle: a
 * tap toggles the list, "Hide"/"Show" fold it to the line and back, and on a phone a swipe up or down on
 * it steps through the states. Every card is a toggle: a tap adds the activity to the draft, a tap on an
 * added one ("Added ✓") takes it out again, with no chat turn in between.
 *
 * <p>A new offer opens compact again, hidden or not - the chat's "the top match is above" has to point
 * at something. What counts as a new offer is the parent's call ({@code resetKey}: a chat turn, a tag
 * answer, another trim), never the list itself: an Add takes the added one out of the default
 * suggestions, and that must not fold the list under the organizer's finger.
 *
 * @param recommendations  [{activityId, name, oneLine, durationMinutes, imageUrl}], best first
 * @param resetKey         changes when a new offer arrives; the sheet then opens compact
 * @param isAdded          (activityId) => whether the draft holds it
 * @param onOpen           (rec) => void - shows the activity's card
 * @param onToggle         (rec, added) => void
 * @param pendingId        the activity whose tap is in flight
 * @param disabled         a chat turn or another tap is in flight
 */
function AiRecommendations({recommendations, resetKey = '', isAdded, onOpen, onToggle, pendingId, disabled}) {
    const t = useT('aiPlanner');
    const [view, setView] = useState('compact');
    // Derived while rendering, not in an effect: the new offer never paints a frame in the old state.
    const [seenKey, setSeenKey] = useState(resetKey);
    if (seenKey !== resetKey) {
        setSeenKey(resetKey);
        setView('compact');
    }
    // The swipe on the header: where the pointer went down, and when a swipe last ended - the click the
    // browser fires right after it is the swipe's tail, not a tap.
    const swipeStart = useRef(null);
    const swipedAt = useRef(0);

    if (!recommendations?.length) {
        return null;
    }
    const [top, ...more] = recommendations;
    const expanded = view === 'expanded';
    const hidden = view === 'hidden';

    const step = (delta) => setView((current) => {
        const next = VIEWS.indexOf(current) + delta;
        return VIEWS[Math.min(VIEWS.length - 1, Math.max(0, next))];
    });
    const onPointerDown = (e) => {
        swipeStart.current = e.clientY;
        swipedAt.current = 0;
    };
    const onPointerMove = (e) => {
        if (swipeStart.current == null || Math.abs(e.clientY - swipeStart.current) < CAPTURE_PX) {
            return;
        }
        // A drag, then: a mouse that leaves the header before letting go still ends the swipe here.
        if (!e.currentTarget.hasPointerCapture?.(e.pointerId)) {
            e.currentTarget.setPointerCapture?.(e.pointerId);
        }
    };
    const onPointerUp = (e) => {
        if (swipeStart.current == null) {
            return;
        }
        const dy = e.clientY - swipeStart.current;
        swipeStart.current = null;
        if (Math.abs(dy) < SWIPE_PX) {
            return;
        }
        swipedAt.current = Date.now();
        step(dy < 0 ? 1 : -1);
    };
    const onPointerCancel = () => {
        swipeStart.current = null;
    };
    /** A click on the header's buttons, unless it is the tail of the swipe that just happened. */
    const tap = (action) => () => {
        if (Date.now() - swipedAt.current < SWIPE_TAIL_MS) {
            return;
        }
        action();
    };
    const toggle = tap(() => setView((current) => (current === 'compact' ? 'expanded' : 'compact')));
    const hideOrShow = tap(() => setView(hidden ? 'compact' : 'hidden'));
    const card = (rec) => (
        <Card key={rec.activityId} rec={rec} isAdded={isAdded} onOpen={onOpen} onToggle={onToggle}
              pendingId={pendingId} disabled={disabled}/>
    );

    return (
        <div className="aip-suggest" role="group" aria-label={t('result.suggestions')} data-view={view}>
            <div className="aip-suggest-head" onPointerDown={onPointerDown} onPointerMove={onPointerMove}
                 onPointerUp={onPointerUp} onPointerCancel={onPointerCancel}>
                <button type="button" className="aip-suggest-toggle" aria-expanded={expanded} onClick={toggle}>
                    <span className="aip-suggest-grip" aria-hidden="true"/>
                    <span className="aip-suggest-title">
                        {t('result.suggestionsCount', {count: recommendations.length})}
                    </span>
                    <i className={`ph ph-caret-${expanded ? 'down' : 'up'}`} aria-hidden="true"/>
                </button>
                <button type="button" className="aip-suggest-hide" onClick={hideOrShow}>
                    {t(hidden ? 'result.show' : 'result.hide')}
                </button>
            </div>
            {view === 'compact' && (
                <>
                    {card(top)}
                    {more.length > 0 && (
                        <button type="button" className="aip-suggest-more" onClick={() => setView('expanded')}>
                            {t('result.moreCount', {count: more.length})}
                        </button>
                    )}
                </>
            )}
            {expanded && (
                <>
                    <div className="aip-suggest-list">
                        {recommendations.map(card)}
                    </div>
                    <button type="button" className="aip-suggest-more" onClick={() => setView('compact')}>
                        {t('result.fewer')}
                    </button>
                </>
            )}
        </div>
    );
}

export default AiRecommendations;
