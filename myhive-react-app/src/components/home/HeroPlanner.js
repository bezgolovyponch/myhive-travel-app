import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import DateRangePicker from '../DateRangePicker';
import {useTrip} from '../../context/TripContext';
import {pushEvent} from '../../utils/analytics';
import {
    addDays, formatDayRange, formatMonth, formatWeekday, formatWeekdayDay, nightsBetween, parseISODate,
} from '../../utils/format';
import {useLocalePath, useT} from '../../i18n';
import './HeroPlanner.css';

// What the homepage hands to /plan: read once by AiPlannerPage, which opens a
// fresh chat with it. sessionStorage, not the URL — it is free text the
// visitor typed, and it must survive the full page load a Next mount makes.
export const PLANNER_DRAFT_KEY = 'myhive-ai-draft';

// The planner's brief limits (Brief.MIN_GROUP / MAX_GROUP / MAX_DAYS).
const MIN_PEOPLE = 2;
const MAX_PEOPLE = 30;
const MAX_DAYS = 7;
// Shown until the visitor sets a head-count (the cart's default is 1).
const DEFAULT_PEOPLE = 10;

// The results card is a showcase, not live data: three sample activities and
// how ten friends voted on them.
const VOTE_GROUP = 10;
const VOTE_ROWS = [
    {key: 'shooting', yes: 8, no: 1},
    {key: 'steak', yes: 7, no: 2},
    {key: 'tank', yes: 3, no: 6},
];
const VOTED = Math.max(...VOTE_ROWS.map((row) => row.yes + row.no));

const Sparkle = () => (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <path d="M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4z"/>
    </svg>
);

// v3 landing, 3a: what the group vote ends in (the results card), then dates
// and head-count and the chat box with next-step phrases built from them. Sending (or the Stag Do AI button) hands the brief
// to /plan; dates and head-count live in the trip so the planner, the vote and
// the Trip Builder all see the same ones.
function HeroPlanner({explorePath}) {
    const t = useT('home');
    const lp = useLocalePath();
    const navigate = useNavigate();
    const {state: trip, dispatch} = useTrip();
    const [text, setText] = useState('');

    const people = trip.tripTravelers >= MIN_PEOPLE ? trip.tripTravelers : DEFAULT_PEOPLE;
    const from = parseISODate(trip.tripStartDate);
    const to = parseISODate(trip.tripEndDate);
    const hasDates = Boolean(from && to && to >= from);
    const nights = hasDates ? nightsBetween(from, to) : 0;
    const nightsLabel = nights === 0 ? t('planner.nights.zero')
        : t(`planner.nights.${nights === 1 ? 'one' : 'other'}`, {count: nights});

    const chips = hasDates
        ? [
            t('planner.chips.plan', {nights: nightsLabel, people}),
            t('planner.chips.big', {day: formatWeekdayDay(nights > 0 ? addDays(from, 1) : from)}),
            t('planner.chips.chill', {day: formatWeekday(to)}),
            t('planner.chips.compare'),
        ]
        : [
            t('planner.chips.anyWeekend', {people, month: formatMonth(new Date())}),
            t('planner.chips.flexible'),
            t('planner.chips.popular', {people}),
        ];

    const setPeople = (n) => {
        const next = Math.min(MAX_PEOPLE, Math.max(MIN_PEOPLE, n));
        if (next !== trip.tripTravelers) dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: next});
    };

    const openPlanner = (message) => {
        if (message) {
            const draft = {message, groupSize: people};
            if (hasDates) draft.days = Math.min(MAX_DAYS, nights + 1);
            try {
                window.sessionStorage.setItem(PLANNER_DRAFT_KEY, JSON.stringify(draft));
            } catch (e) {
                // storage blocked: /plan opens on its own intro instead
            }
            // The head-count shown is the one planned for, even if never touched.
            if (people !== trip.tripTravelers) dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: people});
        }
        navigate('/plan');
    };

    const defaultMessage = hasDates
        ? t('planner.defaultMessage', {nights: nightsLabel, people, range: formatDayRange(from, to)})
        : '';

    const send = (e) => {
        e.preventDefault();
        const message = text.trim() || defaultMessage;
        if (!message) return;
        pushEvent('cta_click', {cta_label: 'Stag Do AI', block: 'hero_planner'});
        openPlanner(message);
    };

    return (
        <div className="hero-planner">
            {/* One h1 for both lines: "Prague stag do planner" is what search
                engines should read as the page's subject. */}
            <h1 className="hero-title">
                <span className="hero-eyebrow">{t('planner.eyebrow')}</span>
                <span className="hero-title-text">{t('planner.title')}</span>
            </h1>
            <p className="hero-subtitle">{t('planner.subtitle')}</p>

            <div className="hp-votes" aria-hidden="true">
                {VOTE_ROWS.map((row) => {
                    const pct = Math.round((row.yes / (row.yes + row.no)) * 100);
                    return (
                        <div key={row.key} className={`hp-vote${pct < 50 ? ' hp-vote--out' : ''}`}>
                            <div className="hp-vote-top">
                                <span className="hp-vote-name">{t(`planner.votes.${row.key}`)}</span>
                                <span className="hp-vote-yes">✓ {t('planner.votes.yes', {count: row.yes})}</span>
                                <span className="hp-vote-no">✕ {t('planner.votes.no', {count: row.no})}</span>
                            </div>
                            <span className="hp-vote-bar"><span style={{width: `${pct}%`}}/></span>
                        </div>
                    );
                })}
                <div className="hp-votes-foot">
                    <span>
                        <b>{t('planner.votes.count', {voted: VOTED, total: VOTE_GROUP})}</b> {t('planner.votes.voted')}
                    </span>
                    <span className="hp-votes-dots">
                        {Array.from({length: VOTE_GROUP}, (_, i) => (
                            <span key={i} className={i < VOTED ? 'is-in' : undefined}/>
                        ))}
                    </span>
                </div>
            </div>

            <form className="hp-form" onSubmit={send}>
                <div className="hp-form-row">
                    <div className="hp-field">
                        <span className="hp-field-label">
                            {t('planner.datesLabel')} <span>{t('planner.datesHint')}</span>
                        </span>
                        <DateRangePicker
                            single
                            from={trip.tripStartDate || ''}
                            to={trip.tripEndDate || ''}
                            placeholder={t('planner.datesPlaceholder')}
                            onChange={(startDate, endDate) => dispatch({type: 'UPDATE_TRIP_DATES', startDate, endDate})}
                        />
                    </div>
                    <div className="hp-field">
                        <span className="hp-field-label">
                            {t('planner.peopleLabel')} <span>{t('planner.peopleHint')}</span>
                        </span>
                        <div className="hp-stepper">
                            <button type="button" aria-label={t('planner.fewer')} disabled={people <= MIN_PEOPLE}
                                    onClick={() => setPeople(people - 1)}>−</button>
                            <span aria-live="polite">{people}</span>
                            <button type="button" aria-label={t('planner.more')} disabled={people >= MAX_PEOPLE}
                                    onClick={() => setPeople(people + 1)}>+</button>
                        </div>
                    </div>
                </div>

                <div className="hp-input">
                    <Sparkle/>
                    <input
                        type="text"
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        placeholder={t('planner.inputPlaceholder')}
                        aria-label={t('planner.inputPlaceholder')}
                        maxLength={1000}
                        enterKeyHint="send"
                    />
                    <button type="submit" className="hp-input-send" aria-label={t('planner.sendAria')}
                            disabled={!text.trim() && !defaultMessage}>
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true">
                            <path d="M5 12h14M13 6l6 6-6 6"/>
                        </svg>
                    </button>
                </div>

                <div className="hp-chips">
                    <span className="hp-chips-head">{hasDates ? t('planner.chipsHead') : t('planner.chipsHeadEmpty')}</span>
                    <div className="hp-chips-list">
                        {chips.map((chip) => (
                            <button key={chip} type="button" className="hp-chip" onClick={() => setText(chip)}>
                                {chip}
                            </button>
                        ))}
                    </div>
                </div>
            </form>

            <div className="hero-cta-group">
                <a
                    className="hp-btn-secondary"
                    href={lp(explorePath)}
                    onClick={(e) => {
                        e.preventDefault();
                        pushEvent('cta_click', {cta_label: 'Browse activities', block: 'hero'});
                        navigate(explorePath);
                    }}
                >
                    {t('hero.exploreCta')}
                </a>
                <a
                    className="hp-btn-primary"
                    href={lp('/plan')}
                    onClick={(e) => {
                        e.preventDefault();
                        pushEvent('cta_click', {cta_label: 'Stag Do AI', block: 'hero'});
                        openPlanner(text.trim() || defaultMessage);
                    }}
                >
                    <Sparkle/> {t('hero.aiPlannerCta')}
                </a>
            </div>
        </div>
    );
}

export default HeroPlanner;
