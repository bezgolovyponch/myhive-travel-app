import {useState} from 'react';
import {WhatsAppGroupPreview} from './StartGroupVoteModal';
import {copyToClipboard} from '../../utils/clipboard';
import {openWhatsApp} from '../../utils/openWhatsApp';
import {pushEvent} from '../../utils/analytics';
import {groupMessage, inviteUrl, whatsappShareUrls} from '../../utils/groupVote';
import {useLocalePath, useT} from '../../i18n';
import './VoteDashboard.css';

// The pieces of the organiser dashboard (v3 4b) the Trip Builder places around
// its itinerary while a vote runs: the header with who has voted, the yes/no
// counts on each activity, the invite link for the group chat, and what the
// group recommended.

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

/** "✓ 8 yes  ✗ 1 no" and the split bar under an itinerary line. */
export function VoteCounts({row}) {
    const t = useT('tripBuilder');
    const yes = row.likeCount;
    const no = row.skipCount;
    if (yes + no === 0) {
        return <div className="vd-counts vd-muted">{t('dashboard.noVotesYet')}</div>;
    }
    return (
        <div className="vd-counts">
            <span className="vd-pills">
                <span className="vd-pill vd-pill--yes">✓ {t('dashboard.yes', {count: yes})}</span>
                <span className="vd-pill vd-pill--no">✗ {t('dashboard.no', {count: no})}</span>
            </span>
            <span className="vd-split" aria-hidden="true">
                <span style={{width: `${Math.round(yes / (yes + no) * 100)}%`}}/>
            </span>
        </div>
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

/** What friends recommended, most recommended first; Add puts it in the plan and the vote. */
export function GroupRecommendations({recommendations, isAdded, onAdd}) {
    const t = useT('tripBuilder');
    if (!recommendations || recommendations.length === 0) {
        return null;
    }
    return (
        <section className="vd-recs">
            <h3 className="vd-section-title">{t('dashboard.recsTitle')}</h3>
            <p className="vd-muted vd-recs-sub">{t('dashboard.recsSubtitle')}</p>
            <ul className="vd-recs-list">
                {recommendations.map(rec => {
                    const added = isAdded(rec.activityId);
                    return (
                        <li key={rec.activityId} className="vd-rec">
                            {rec.imageUrl
                                ? <img src={rec.imageUrl} alt="" className="vd-rec-img" loading="lazy"/>
                                : <span className="vd-rec-img" aria-hidden="true"/>}
                            <div className="vd-rec-text">
                                <div className="vd-rec-name">{rec.name}</div>
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
        </section>
    );
}
