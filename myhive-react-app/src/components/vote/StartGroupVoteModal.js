import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppModal from '../AppModal';
import DateRangePicker from '../DateRangePicker';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { clearTripLead } from '../../utils/tripLead';
import { emailFormat } from '../../utils/validators';
import { generateUuid } from '../../utils/uuid';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import { openWhatsApp } from '../../utils/openWhatsApp';
import {
    COUNTRY_CODES, dashboardPath, groupMessage, inviteUrl, toE164, whatsappShareUrls,
} from '../../utils/groupVote';
import { useT, useLocalePath } from '../../i18n';
import './StartGroupVoteModal.css';

function datesError({ needsDates, voteStartDate, voteEndDate }, t) {
    if (!needsDates) {
        return undefined;
    }
    if (!voteStartDate || !voteEndDate) {
        return t('start.errors.datesRequired');
    }
    if (voteEndDate < voteStartDate) {
        return t('start.errors.endBeforeStart');
    }
    return undefined;
}

function WhatsAppIcon() {
    return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M17.5 14.4c-.3-.15-1.7-.85-2-.94-.26-.1-.46-.15-.65.15s-.75.94-.9 1.13c-.17.2-.34.22-.63.07-.3-.15-1.24-.46-2.36-1.46-.87-.78-1.46-1.74-1.63-2.03-.17-.3-.02-.46.13-.6.14-.14.3-.34.44-.51.14-.17.19-.3.29-.5s.05-.36-.02-.51c-.07-.15-.65-1.58-.9-2.16-.23-.56-.47-.49-.65-.5h-.55c-.19 0-.51.07-.78.36-.27.3-1.03 1.01-1.03 2.46s1.05 2.85 1.2 3.05c.15.2 2.08 3.17 5.03 4.44.7.3 1.25.49 1.68.62.7.22 1.35.19 1.86.12.57-.09 1.7-.7 1.94-1.36.24-.66.24-1.23.17-1.36-.07-.13-.27-.2-.55-.35zM12 2C6.5 2 2 6.5 2 12c0 1.85.5 3.6 1.4 5.1L2 22l5-1.3c1.44.8 3.07 1.24 4.83 1.24H12c5.5 0 10-4.5 10-10S17.5 2 12 2z"/>
        </svg>
    );
}

function GroupIcon() {
    return (
        <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <circle cx="8" cy="9" r="3"/>
            <circle cx="16" cy="9" r="3"/>
            <path d="M2 19c0-3 3-5 6-5s6 2 6 5zM12 19c0-3 2-5 4-5 3 0 6 2 6 5z"/>
        </svg>
    );
}

function ReadTicks() {
    return (
        <svg width="16" height="10" viewBox="0 0 16 10" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
            <path d="M1 5.5l3 3L10 2"/>
            <path d="M6 8.5l1 0L13 2"/>
        </svg>
    );
}

/**
 * The group's chat as WhatsApp shows it, with the message the organiser is
 * about to send. Shared with the organiser dashboard, where `linkUrl` shows the
 * real link under the preview.
 */
export function WhatsAppGroupPreview({ t, destinationName, members, message, linkUrl }) {
    const time = useMemo(() => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), []);
    return (
        <div className="sgv-chat">
            <div className="sgv-chat-head">
                <span className="sgv-chat-avatar" aria-hidden="true"><GroupIcon/></span>
                <span className="sgv-chat-title">
                    <b>{t('start.chat.title', { destination: destinationName })}</b>
                    {members > 0 && <> · {t('start.chat.members', { count: members })}</>}
                </span>
                <svg className="sgv-chat-menu" width="4" height="14" viewBox="0 0 4 14" fill="currentColor" aria-hidden="true">
                    <circle cx="2" cy="2" r="1.5"/><circle cx="2" cy="7" r="1.5"/><circle cx="2" cy="12" r="1.5"/>
                </svg>
            </div>
            <div className="sgv-chat-body">
                <div className="sgv-bubble">
                    <p className="sgv-bubble-text">{message}</p>
                    <div className="sgv-link-preview">
                        <span className="sgv-link-logo" aria-hidden="true">t</span>
                        <div>
                            <div className="sgv-link-title">
                                {t('start.chat.linkTitle', { destination: destinationName })}
                                {!linkUrl && <> · {t('start.chat.linkHost')}</>}
                            </div>
                            {linkUrl && <div className="sgv-link-host">{linkUrl}</div>}
                        </div>
                    </div>
                    <div className="sgv-bubble-meta">{time}<ReadTicks/></div>
                </div>
            </div>
        </div>
    );
}

// What the organiser gets back, shown before they ask: a sample of three
// activities with the group's yes and no, the last one voted out.
const CHOICE_GROUP = 10;
const CHOICE_ROWS = [
    { key: 'shooting', yes: 8, no: 1 },
    { key: 'steak', yes: 7, no: 2 },
    { key: 'tank', yes: 3, no: 6 },
];
const CHOICE_VOTED = Math.max(...CHOICE_ROWS.map((row) => row.yes + row.no));
const CHOICE_RECOMMENDED = 7;
// The picker's last entry: a code typed by hand.
const OTHER_CODE = 'other';

function GroupChoice({ t }) {
    return (
        <div className="sgv-choice" aria-hidden="true">
            <div className="sgv-choice-head">
                <span className="sgv-choice-title">{t('start.choice.title')}</span>
                <span className="sgv-choice-dots">
                    {Array.from({ length: CHOICE_GROUP }, (_, i) => (
                        <span key={i} className={i < CHOICE_VOTED ? 'is-in' : undefined}/>
                    ))}
                </span>
                <span>{t('start.choice.voted', { voted: CHOICE_VOTED, total: CHOICE_GROUP })}</span>
            </div>
            {CHOICE_ROWS.map((row) => (
                <div key={row.key} className={`sgv-choice-row${row.yes < row.no ? ' is-out' : ''}`}>
                    <div className="sgv-choice-top">
                        <span className="sgv-choice-name">{t(`start.choice.rows.${row.key}`)}</span>
                        <span className="sgv-choice-yes">✓ {t('start.choice.yes', { count: row.yes })}</span>
                        <span className="sgv-choice-no">✕ {t('start.choice.no', { count: row.no })}</span>
                    </div>
                    <span className="sgv-choice-bar">
                        <span style={{ width: `${Math.round(row.yes / (row.yes + row.no) * 100)}%` }}/>
                    </span>
                </div>
            ))}
            <div className="sgv-choice-foot">
                <span>{t('start.choice.recommendations')}</span>
                <b>+{CHOICE_RECOMMENDED}</b>
            </div>
        </div>
    );
}

/** The message as WhatsApp shows one the organiser has sent: no chat around it. */
function SentMessage({ t, destinationName, message }) {
    const time = useMemo(() => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), []);
    return (
        <div className="sgv-msg">
            <p className="sgv-msg-text">{message}</p>
            <div className="sgv-msg-link">
                <span className="sgv-msg-logo" aria-hidden="true">t</span>
                <span>{t('start.chat.linkTitle', { destination: destinationName })}</span>
            </div>
            <span className="sgv-msg-meta">{time}<ReadTicks/></span>
        </div>
    );
}

// The organiser's contact modal (v3 4a, "Contact Modal Light"). It shows what
// the vote gives back and the message for the group, then takes a WhatsApp
// number or an email: each button needs its own contact, and either one
// creates the vote and opens the organiser's dashboard. The vote's link token
// is picked here, so WhatsApp opens with the link in the same tap, before the
// server answers. Dates are asked for only when the trip never captured them.
function StartGroupVoteModal({
    isOpen, onClose, destinationId, destinationName, destinationSlug, activityIds, numberOfTravelers,
    startDate, endDate, voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    const t = useT('voteComponents');
    const lp = useLocalePath();
    const navigate = useNavigate();
    const [voteStartDate, setVoteStartDate] = useState(startDate || '');
    const [voteEndDate, setVoteEndDate] = useState(endDate || '');
    const [countryCode, setCountryCode] = useState(COUNTRY_CODES[0].code);
    const [typedCode, setTypedCode] = useState(null); // "+351" while the code is typed by hand, else null
    const [phone, setPhone] = useState('');
    const [email, setEmail] = useState('');
    const [apiError, setApiError] = useState(null);
    const [submitting, setSubmitting] = useState(null); // 'whatsapp' | 'email' while creating
    const [sent, setSent] = useState(false);
    const launchedRef = useRef(false);
    // One pair of tokens per opening, the link token and the manager token, so a
    // retry after a failed create is the same vote to the server: a message already
    // sent to the group still points at it, and a create whose response was lost
    // comes back as the vote already created instead of a conflict.
    const shareTokenRef = useRef(null);
    const managerTokenRef = useRef(null);

    const needsDates = !startDate || !endDate;
    const tripStart = needsDates ? voteStartDate : startDate;
    const tripEnd = needsDates ? voteEndDate : endDate;
    const e164 = toE164(typedCode ?? countryCode, phone);
    const emailOk = !emailFormat(email);
    const dateProblem = datesError({ needsDates, voteStartDate, voteEndDate }, t);
    const message = groupMessage(t, { destinationName, startDate: tripStart, endDate: tripEnd });

    // The modal stays mounted between openings (TripBuilder renders it with
    // isOpen), so every open drops stale errors and counts one more view of the
    // contact screen. Typed values survive as a draft, as in TripSetupModal.
    useEffect(() => {
        if (isOpen) {
            setApiError(null);
            setSent(false);
            shareTokenRef.current = generateUuid();
            managerTokenRef.current = generateUuid();
            pushEvent('email_screen_view', { vote_mode: voteMode });
        }
    }, [isOpen, voteMode]);

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode,
                has_email: email.trim() !== '', has_phone: phone.trim() !== '',
            });
        }
        onClose();
    };

    const createVote = async (channel) => {
        const shareToken = shareTokenRef.current;
        const managerToken = managerTokenRef.current;
        // Both contacts when both were typed: the more ways to reach the organiser, the better.
        const contact = {
            initiatorPhone: e164 || undefined,
            initiatorEmail: emailOk ? email.trim() : undefined,
        };
        pushEvent('organizer_voted', { vote_mode: voteMode, selected_count: activityIds.length });
        setSubmitting(channel);
        setApiError(null);
        try {
            const session = voteMode === 'QUIZ'
                ? await voteApi.createSession({
                    destinationId, ...contact, shareToken, managerToken, numberOfTravelers,
                    startDate: tripStart, endDate: tripEnd, budget,
                    voterToken: getOrCreateVoterToken(), quizResponses, activityIds,
                })
                : await voteApi.createCartSession({
                    destinationId, ...contact, shareToken, managerToken, numberOfTravelers,
                    startDate: tripStart, endDate: tripEnd, activityIds,
                });
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            if (session.managerToken) {
                localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            }
            if (voteMode === 'CART') {
                // QUIZ parity: quiz sessions intentionally do not set this key.
                localStorage.setItem('myhive-trip-vote-session', session.shareToken);
            }
            clearTripLead();
            pushEvent('contact_captured', {
                trip_id: session.shareToken, vote_mode: voteMode, channel, source: 'vote_email_screen',
            });
            pushEvent('vote_launched', {
                trip_id: session.shareToken, user_role: 'organizer', selected_count: activityIds.length,
            });
            pushEvent('link_revealed', { trip_id: session.shareToken, vote_mode: voteMode });
            launchedRef.current = true;
            if (onLaunched) onLaunched();
            // The dashboard is the Trip Builder tab this modal may sit on: close
            // it, so the organiser (back from WhatsApp, too) lands on the dashboard.
            setSubmitting(null);
            onClose();
            navigate(dashboardPath(destinationSlug, session.shareToken));
        } catch (e) {
            setApiError(t('start.errors.createFailed'));
            setSubmitting(null);
        }
    };

    const handleWhatsApp = () => {
        if (!e164 || dateProblem || submitting) {
            return;
        }
        const link = inviteUrl(window.location.origin, lp, shareTokenRef.current);
        // Inside the tap, before the request: a phone only lets a user gesture open WhatsApp.
        openWhatsApp(whatsappShareUrls(message, link));
        pushEvent('group_message_sent', { vote_mode: voteMode, source: 'vote_email_screen' });
        setSent(true);
        createVote('whatsapp');
    };

    const handleEmail = () => {
        if (!emailOk || dateProblem || submitting) {
            return;
        }
        createVote('email');
    };

    const whatsappReady = Boolean(e164) && !dateProblem && !submitting;
    const emailReady = emailOk && !dateProblem && !submitting;

    return (
        <AppModal
            isOpen={isOpen}
            onClose={handleClose}
            closeOnBackdrop
            title={`${t('start.headlineLead')} ${t('start.headlineResult')}`}
            contentClassName={`start-vote-modal${needsDates ? ' has-dates' : ''}`}
        >
            <GroupChoice t={t}/>

            {needsDates && (
                <div className="sgv-dates">
                    <div className="sgv-dates-label">{t('start.tripDates')}</div>
                    {/* The site's dark range calendar (as in TripSetupModal), not the
                        browser's white native date popup. */}
                    <DateRangePicker
                        from={voteStartDate}
                        to={voteEndDate}
                        onChange={(from, to) => {
                            setVoteStartDate(from);
                            setVoteEndDate(to);
                        }}
                        popover
                    />
                    {voteStartDate && voteEndDate && dateProblem && (
                        <span className="error-message" role="alert">{dateProblem}</span>
                    )}
                </div>
            )}

            <div className="sgv-whatsapp">
                <SentMessage t={t} destinationName={destinationName} message={message}/>
                <div className={`sgv-field sgv-phone${e164 ? ' is-valid-phone' : ''}`}>
                    {typedCode == null ? (
                        <select
                            aria-label={t('start.phone.countryLabel')}
                            value={countryCode}
                            onChange={(e) => {
                                if (e.target.value === OTHER_CODE) {
                                    setTypedCode('+');
                                } else {
                                    setCountryCode(e.target.value);
                                }
                                setSent(false);
                            }}
                            className="sgv-code"
                        >
                            {COUNTRY_CODES.map(c => <option key={c.code} value={c.code}>{c.label}</option>)}
                            <option value={OTHER_CODE}>{t('start.phone.otherCode')}</option>
                        </select>
                    ) : (
                        // Any other country: the code is typed. Emptied, the picker comes back.
                        <input
                            type="tel"
                            inputMode="tel"
                            autoFocus
                            aria-label={t('start.phone.countryLabel')}
                            placeholder="+00"
                            value={typedCode}
                            maxLength={5}
                            onChange={(e) => {
                                const digits = e.target.value.replace(/\D/g, '').slice(0, 4);
                                setTypedCode(e.target.value === '' ? null : `+${digits}`);
                                setSent(false);
                            }}
                            className="sgv-code"
                        />
                    )}
                    <input
                        type="tel"
                        inputMode="tel"
                        autoComplete="tel-national"
                        aria-label={t('start.phone.placeholder')}
                        placeholder={t('start.phone.placeholder')}
                        value={phone}
                        onChange={(e) => {
                            setPhone(e.target.value);
                            setSent(false);
                        }}
                        className="sgv-input"
                    />
                </div>
                <button
                    type="button"
                    className="sgv-btn sgv-btn--whatsapp"
                    onClick={handleWhatsApp}
                    disabled={!whatsappReady}
                >
                    <WhatsAppIcon/>
                    {submitting === 'whatsapp' ? t('start.creating') : sent ? t('start.sent') : t('start.sendWhatsApp')}
                </button>
            </div>

            <div className="sgv-or"><span>{t('start.or')}</span></div>

            <div className={`sgv-field sgv-email${emailOk ? ' is-valid-email' : ''}`}>
                <input
                    type="email"
                    inputMode="email"
                    autoComplete="email"
                    autoCapitalize="none"
                    spellCheck={false}
                    aria-label={t('start.email.placeholder')}
                    placeholder={t('start.email.placeholder')}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                            handleEmail();
                        }
                    }}
                    className="sgv-input"
                />
            </div>
            <button
                type="button"
                className="sgv-btn sgv-btn--primary"
                onClick={handleEmail}
                disabled={!emailReady}
            >
                {submitting === 'email' ? t('start.creating') : t('start.startPlanning')}
            </button>
            {apiError && <p className="error-message" role="alert">{apiError}</p>}
            <p className="sgv-footer">{t('start.footer')}</p>
        </AppModal>
    );
}

export default StartGroupVoteModal;
