import {useEffect, useRef, useState} from 'react';
import {WhatsAppGroupPreview} from './StartGroupVoteModal';
import {copyToClipboard} from '../../utils/clipboard';
import {openWhatsApp} from '../../utils/openWhatsApp';
import {pushEvent} from '../../utils/analytics';
import {groupMessage, inviteUrl, whatsappShareUrls} from '../../utils/groupVote';
import {useLocalePath, useT} from '../../i18n';
import './VoteDashboard.css';

// The pieces of the organiser dashboard (v3 4b) the Trip Builder shows while a
// vote runs: the header with who has voted, the invite link for the group chat,
// the plan with "N/M keep" on each activity, and what the group recommended.

export function VoteDashboardHeader({destinationName, dates, tally}) {
    const t = useT('tripBuilder');
    const voted = tally?.participantCount ?? 0;
    const total = tally?.numberOfTravelers || 0;
    const left = Math.max(0, total - voted);
    const closed = tally?.status === 'COMPLETED';
    return (
        <div className="vd-header">
            <div className="vd-header-top">
                <span className="vd-eyebrow">
                    {dates
                        ? t('dashboard.eyebrow', {destination: destinationName, dates})
                        : t('dashboard.eyebrowNoDates', {destination: destinationName})}
                </span>
                <span className="vd-badge">{t('dashboard.organiser')}</span>
            </div>
            <h2 className="vd-title">{t('dashboard.title')}</h2>
            {tally && (
                <div className="vd-progress">
                    <div className="vd-progress-text">
                        <span>{total > 0 ? t('dashboard.voted', {voted, total}) : t('dashboard.votedNoTotal', {voted})}</span>
                        <span className="vd-muted">
                            {closed ? t('dashboard.closed') : left > 0 ? t('dashboard.left', {count: left}) : t('dashboard.everyone')}
                        </span>
                    </div>
                    <div className="vd-progress-bar">
                        <span style={{width: `${total > 0 ? Math.min(100, Math.round(voted / total * 100)) : 0}%`}}/>
                    </div>
                </div>
            )}
        </div>
    );
}

/** "7/8 keep" and its bar: how many of the friends who voted on it kept it. */
export function KeepBar({row}) {
    const t = useT('tripBuilder');
    const keep = row?.likeCount ?? 0;
    const votes = keep + (row?.skipCount ?? 0);
    if (votes === 0) {
        return <div className="vd-keep vd-muted">{t('dashboard.noVotesYet')}</div>;
    }
    const share = keep / votes;
    return (
        <div className={`vd-keep${share < 0.5 ? ' is-low' : ''}`}>
            <span className="vd-keep-bar" aria-hidden="true">
                <span style={{width: `${Math.round(share * 100)}%`}}/>
            </span>
            <span className="vd-keep-text">{t('dashboard.keep', {keep, votes})}</span>
        </div>
    );
}

/**
 * One line of the plan: the activity, its keep bar, and × to drop it from the
 * vote — or, once dropped, struck through with Restore. With onOpen the name
 * opens the activity's card.
 */
export function PlanRow({name, row, dropped = false, onOpen, onRemove, onRestore}) {
    const t = useT('tripBuilder');
    return (
        <li className={`vd-row${dropped ? ' is-dropped' : ''}`}>
            <div className="vd-row-body">
                <div className="vd-row-name">
                    {onOpen ? (
                        <button type="button" className="activity-open" aria-haspopup="dialog" onClick={onOpen}>
                            {name}
                        </button>
                    ) : name}
                </div>
                <KeepBar row={row}/>
            </div>
            {dropped ? (
                onRestore && (
                    <button type="button" className="vd-restore-btn" onClick={onRestore}>
                        {t('dashboard.restore')}
                    </button>
                )
            ) : (
                onRemove && (
                    <button type="button" className="vd-remove-btn" onClick={onRemove}
                            aria-label={t('items.removeAria', {name})}>
                        ×
                    </button>
                )
            )}
        </li>
    );
}

/** The link for the group chat: the same WhatsApp message as the contact modal, now with the link. */
export function VoteInvitePanel({shareToken, destinationName, members, startDate, endDate}) {
    const t = useT('tripBuilder');
    const tv = useT('voteComponents');
    const lp = useLocalePath();
    const [copied, setCopied] = useState(false);
    const link = inviteUrl(window.location.origin, lp, shareToken);
    const message = groupMessage(tv, {destinationName, startDate, endDate});

    const send = () => {
        openWhatsApp(whatsappShareUrls(message, link));
        pushEvent('group_message_sent', {trip_id: shareToken, source: 'organizer_dashboard'});
    };
    const copy = () => {
        copyToClipboard(link).then(ok => {
            if (ok) {
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            }
        });
    };

    return (
        <section className="vd-invite sgv-scope" aria-label={t('dashboard.inviteTitle')}>
            <h3 className="vd-section-title">{t('dashboard.inviteTitle')}</h3>
            <WhatsAppGroupPreview t={tv} destinationName={destinationName} members={members}
                                  message={message} linkUrl={link}/>
            <div className="vd-invite-actions">
                <button type="button" className="sgv-btn sgv-btn--whatsapp" onClick={send}>
                    {tv('start.sendWhatsApp')}
                </button>
                <button type="button" className="sgv-btn vd-btn-outline" onClick={copy}>
                    {copied ? t('dashboard.copied') : t('dashboard.copyLink')}
                </button>
            </div>
        </section>
    );
}

/**
 * What friends recommended, most recommended first, in a drawer: closed while
 * there is nothing in it, opened when the first recommendation arrives. Add
 * puts one in the plan and the vote.
 */
export function GroupRecommendations({recommendations, isAdded, onAdd, onOpen}) {
    const t = useT('tripBuilder');
    const recs = recommendations || [];
    const [open, setOpen] = useState(recs.length > 0);
    const hadRecs = useRef(recs.length > 0);
    useEffect(() => {
        if (recs.length > 0 && !hadRecs.current) {
            setOpen(true);
        }
        hadRecs.current = recs.length > 0;
    }, [recs.length]);

    return (
        <section className={`vd-recs${open ? ' is-open' : ''}`}>
            <button type="button" className="vd-recs-toggle" aria-expanded={open} onClick={() => setOpen(o => !o)}>
                <span className="vd-recs-head">
                    <span className="vd-section-title">{t('dashboard.recsTitle')}</span>
                    <span className="vd-muted">{t('dashboard.recsSubtitle')}</span>
                </span>
                {recs.length > 0 && <span className="vd-recs-count">+{recs.length}</span>}
                <span className="vd-recs-chevron" aria-hidden="true"/>
            </button>
            {open && (recs.length === 0 ? (
                <p className="vd-muted vd-recs-empty">{t('dashboard.recsEmpty')}</p>
            ) : (
                <ul className="vd-recs-list">
                    {recs.map(rec => {
                        const added = isAdded(rec.activityId);
                        return (
                            <li key={rec.activityId} className="vd-rec">
                                <div className="vd-rec-text">
                                    <div className="vd-rec-name">
                                        {onOpen ? (
                                            <button type="button" className="activity-open" aria-haspopup="dialog"
                                                    onClick={() => onOpen(rec)}>
                                                {rec.name}
                                            </button>
                                        ) : rec.name}
                                    </div>
                                    <div className="vd-muted">
                                        {t(rec.recommendationCount === 1 ? 'dashboard.recByOne' : 'dashboard.recByOther',
                                            {count: rec.recommendationCount})}
                                    </div>
                                </div>
                                <button type="button" className={`vd-rec-btn${added ? ' is-added' : ''}`}
                                        onClick={() => onAdd(rec)} disabled={added}>
                                    {added ? t('dashboard.added') : t('dashboard.add')}
                                </button>
                            </li>
                        );
                    })}
                </ul>
            ))}
        </section>
    );
}
