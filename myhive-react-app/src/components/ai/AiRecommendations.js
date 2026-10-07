import {useT} from '../../i18n';
import {duration} from './AiPackageView';

// "1 h 30 min · Action packed, ...": no price - the draft shows one "from" total only.
function meta(rec) {
    return [duration(rec.durationMinutes), rec.oneLine].filter(Boolean).join(' · ');
}

/**
 * What the chat recommended for the draft (trip draft A): the best match as a
 * card with Add, the related ones as "+ name" tags. Every one is a toggle - a
 * tap adds it to the draft, a tap on an added one ("Added ✓" / "✓ name")
 * takes it out again - with no chat turn in between.
 *
 * @param recommendations  [{activityId, name, oneLine, durationMinutes, imageUrl}], best first
 * @param isAdded          (activityId) => whether the draft holds it
 * @param onOpen           (rec) => void - shows the top match's card
 * @param onToggle         (rec, added) => void
 * @param pendingId        the activity whose tap is in flight
 * @param disabled         a chat turn or another tap is in flight
 */
function AiRecommendations({recommendations, isAdded, onOpen, onToggle, pendingId, disabled}) {
    const t = useT('aiPlanner');
    if (!recommendations?.length) return null;
    const [top, ...more] = recommendations;
    const topAdded = isAdded(top.activityId);
    return (
        <div className="aip-suggest" role="group" aria-label={t('result.suggestions')}>
            <div className="aip-suggest-top">
                <span className="aip-suggest-thumb" aria-hidden="true">
                    {top.imageUrl ? <img src={top.imageUrl} alt="" loading="lazy"/> : top.name.charAt(0)}
                </span>
                <div className="aip-suggest-body">
                    <div className="aip-suggest-name">
                        {onOpen ? (
                            <button type="button" className="activity-open" aria-haspopup="dialog"
                                    onClick={() => onOpen(top)}>
                                {top.name}
                            </button>
                        ) : top.name}
                    </div>
                    {meta(top) && <div className="aip-suggest-meta">{meta(top)}</div>}
                </div>
                <button
                    type="button"
                    className={`aip-suggest-add${topAdded ? ' is-added' : ''}`}
                    aria-pressed={topAdded}
                    aria-label={t(topAdded ? 'result.removeAria' : 'result.addAria', {name: top.name})}
                    disabled={disabled}
                    onClick={() => onToggle(top, topAdded)}
                >
                    {pendingId === top.activityId ? '…' : topAdded ? t('result.added') : t('result.add')}
                </button>
            </div>
            {more.length > 0 && (
                <div className="aip-suggest-more">
                    {more.map((rec) => {
                        const added = isAdded(rec.activityId);
                        return (
                            <button
                                key={rec.activityId}
                                type="button"
                                className={added ? 'is-added' : undefined}
                                aria-pressed={added}
                                aria-label={t(added ? 'result.removeAria' : 'result.addAria', {name: rec.name})}
                                disabled={disabled}
                                onClick={() => onToggle(rec, added)}
                            >
                                {added ? '✓' : '+'} {rec.name}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

export default AiRecommendations;
