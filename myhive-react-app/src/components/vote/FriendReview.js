import {Fragment, useMemo, useState} from 'react';
import {capitalizeFirst} from '../../utils/format';
import {groupByDay} from '../../utils/voteDays';
import {useT} from '../../i18n';
import './FriendVote.css';

const ALL = 'all';

// The friend's review after the swipe (v3 4c): their keeps and drops, which
// they can still flip, then activities from the catalogue to recommend, then
// one "Send to group" that sends both. Nothing here is shown again afterwards.
function FriendReview({
    eyebrow, activities, votes, onVote, catalog, categories, recommended, onToggleRecommend, onSend, sending, error,
    startDate,
}) {
    const t = useT('vote');
    const [filter, setFilter] = useState(ALL);
    const ballotIds = useMemo(() => new Set(activities.map(a => a.id)), [activities]);
    const kept = activities.filter(a => votes[a.id]).length;
    const recommendedActivities = recommended
        .map(id => catalog.find(a => a.id === id))
        .filter(Boolean);
    const offBallot = catalog.filter(a => !ballotIds.has(a.id));
    const shown = filter === ALL
        ? offBallot
        : offBallot.filter(a => (a.categories || []).some(c => c.slug === filter));

    const summary = [
        t('review.kept', {kept, total: activities.length}),
        recommended.length > 0 && t('review.recommended', {count: recommended.length}),
        t('review.browse'),
    ].filter(Boolean).join(' · ');

    return (
        <div className="fv-screen fv-review">
            <div className="fv-review-scroll">
                {eyebrow && <div className="fv-eyebrow">{eyebrow}</div>}
                <h1 className="fv-title">{t('review.title')}</h1>
                <p className="fv-summary">{summary}</p>

                <ul className="fv-rows">
                    {/* Day by day when the organiser's plan has days; one list otherwise. */}
                    {(groupByDay(activities, a => a.dayNumber ?? null, startDate, n => t('review.day', {n}))
                        || [{dayNumber: null, label: null, rows: activities, plain: true}]).map(group => (
                        <Fragment key={group.dayNumber ?? 'none'}>
                            {!group.plain && (
                                <li className="fv-day">{group.label ?? t('review.noDay')}</li>
                            )}
                            {group.rows.map(a => {
                        const keep = votes[a.id] === true;
                        return (
                            <li key={a.id} className={`fv-row ${keep ? 'is-keep' : 'is-drop'}`}>
                                <div className="fv-row-name">{a.name}</div>
                                <div className="fv-toggle" role="group" aria-label={a.name}>
                                    <button type="button" className={`fv-toggle-btn fv-toggle-drop${keep ? '' : ' is-on'}`}
                                            aria-pressed={!keep} onClick={() => onVote(a.id, false)}>
                                        {t('review.drop')}
                                    </button>
                                    <button type="button" className={`fv-toggle-btn fv-toggle-keep${keep ? ' is-on' : ''}`}
                                            aria-pressed={keep} onClick={() => onVote(a.id, true)}>
                                        {t('review.keep')}
                                    </button>
                                </div>
                            </li>
                        );
                            })}
                        </Fragment>
                    ))}
                    {recommendedActivities.map(a => (
                        <li key={a.id} className="fv-row is-recommended">
                            <div>
                                <div className="fv-row-meta">{t('review.yourRecommendation')}</div>
                                <div className="fv-row-name">{a.name}</div>
                            </div>
                            <button type="button" className="fv-remove-btn" onClick={() => onToggleRecommend(a.id)}
                                    aria-label={t('review.removeAria', {name: a.name})}>
                                {t('review.remove')}
                            </button>
                        </li>
                    ))}
                </ul>

                <hr className="fv-divider"/>

                <section className="fv-recommend">
                    <h2 className="fv-section-title">{t('review.recommendTitle')}</h2>
                    <p className="fv-muted">{t('review.recommendText')}</p>
                    <div className="fv-chips">
                        <button type="button" className={`fv-chip${filter === ALL ? ' is-on' : ''}`}
                                aria-pressed={filter === ALL} onClick={() => setFilter(ALL)}>
                            {t('review.all')}
                        </button>
                        {categories.map(c => (
                            <button key={c.slug} type="button" className={`fv-chip${filter === c.slug ? ' is-on' : ''}`}
                                    aria-pressed={filter === c.slug} onClick={() => setFilter(c.slug)}>
                                {capitalizeFirst(c.name)}
                            </button>
                        ))}
                    </div>
                    <ul className="fv-catalog">
                        {shown.map(a => {
                            const added = recommended.includes(a.id);
                            return (
                                <li key={a.id} className="fv-catalog-item">
                                    {a.imageUrl
                                        ? <img src={a.imageUrl} alt="" className="fv-catalog-img" loading="lazy"/>
                                        : <span className="fv-catalog-img" aria-hidden="true"/>}
                                    <div className="fv-catalog-text">
                                        <div className="fv-row-name">{a.name}</div>
                                        {a.categories?.[0] && (
                                            <div className="fv-muted">{capitalizeFirst(a.categories[0].name)}</div>
                                        )}
                                    </div>
                                    <button type="button" className={`fv-add-btn${added ? ' is-added' : ''}`}
                                            aria-pressed={added} aria-label={t(added ? 'review.addedAria' : 'review.addAria', {name: a.name})}
                                            onClick={() => onToggleRecommend(a.id)}>
                                        {added ? t('review.added') : t('review.add')}
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                </section>
            </div>
            <div className="fv-bottom">
                {error && <p className="fv-error" role="alert">{error}</p>}
                <button type="button" className="fv-send-btn" onClick={onSend} disabled={sending}>
                    {sending ? t('review.sending') : t('review.send')}
                </button>
            </div>
        </div>
    );
}

export default FriendReview;
