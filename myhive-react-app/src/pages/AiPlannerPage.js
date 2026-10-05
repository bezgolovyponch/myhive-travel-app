import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import PageHead from '../components/PageHead';
import AiThread from '../components/ai/AiThread';
import AiPackageView, {AI_PICK_KEY, TrimCards, plural} from '../components/ai/AiPackageView';
import StartGroupVoteModal from '../components/vote/StartGroupVoteModal';
import {useAiPlanner} from '../hooks/useAiPlanner';
import {useTrip} from '../context/TripContext';
import {useCatalog} from '../context/CatalogContext';
import {DEFAULT_DESTINATION_SLUG} from '../services/config';
import {pushEvent} from '../utils/analytics';
import {addDays, formatShortRange, nightsBetween, parseISODate} from '../utils/format';
import {PLANNER_DRAFT_KEY} from '../components/home/HeroPlanner';
import {useLocale, useT} from '../i18n';
import './AiPlannerPage.css';

const STARTER_KEYS = ['one', 'two', 'three'];

// The brief the homepage hero handed over, read once (see HeroPlanner).
function takeDraft() {
    try {
        const raw = window.sessionStorage.getItem(PLANNER_DRAFT_KEY);
        window.sessionStorage.removeItem(PLANNER_DRAFT_KEY);
        const draft = raw ? JSON.parse(raw) : null;
        return draft?.message ? draft : null;
    } catch (e) {
        return null;
    }
}

// The trip's dates, when they cover exactly the days the plan has: then a
// package day is a calendar day ("Fri 16 Oct"), otherwise it stays "Day 1".
function planStart(trip, brief) {
    const from = parseISODate(trip.tripStartDate);
    const to = parseISODate(trip.tripEndDate);
    if (!from || !to || !brief?.days) return null;
    return nightsBetween(from, to) + 1 === brief.days ? from : null;
}

// Stag Do AI (v3 landing, 2d/2e). Result-first: until there are packages the
// chat is the page; once there are, the packages are the page and the chat
// retracts into a bottom bar that opens a sheet over them.
function AiPlannerPage({pollIntervalMs}) {
    const t = useT('aiPlanner');
    const locale = useLocale();
    const [params] = useSearchParams();
    const destinationSlug = params.get('destination') || DEFAULT_DESTINATION_SLUG;
    const planner = useAiPlanner({destinationSlug, locale, pollMs: pollIntervalMs});
    const {state: trip, dispatch} = useTrip();
    const {state: catalog} = useCatalog();
    const destination = catalog.destinations.find((d) => d.slug === destinationSlug);
    const [activeKey, setActiveKey] = useState(AI_PICK_KEY);
    const [dockOpen, setDockOpen] = useState(false);
    const [removing, setRemoving] = useState(null);
    const [handoff, setHandoff] = useState(null); // {activityIds, groupSize} once a trim is picked
    const [handingOff, setHandingOff] = useState(false);
    const [handoffError, setHandoffError] = useState(false);
    const {generation, sending} = planner;
    const hasResult = Boolean(generation?.packages?.length);

    // A brief from the homepage starts a fresh chat with its days and head-count
    // preset, even over an older chat in storage: the visitor just asked anew.
    const draftHandled = useRef(false);
    useEffect(() => {
        if (planner.restoring || draftHandled.current) return;
        draftHandled.current = true;
        const draft = takeDraft();
        if (!draft) return;
        if (planner.token || planner.messages.length) planner.newChat();
        const preset = {};
        if (draft.days) preset.days = draft.days;
        if (draft.groupSize) preset.groupSize = draft.groupSize;
        planner.send(draft.message, preset);
    }, [planner]);

    // First packages land: the chat steps aside so the result gets the screen.
    const hadResult = useRef(hasResult);
    useEffect(() => {
        if (hasResult && !hadResult.current) setDockOpen(false);
        hadResult.current = hasResult;
    }, [hasResult]);

    useEffect(() => {
        if (!sending) setRemoving(null);
    }, [sending]);

    useEffect(() => {
        if (!dockOpen) return undefined;
        const onKey = (e) => e.key === 'Escape' && setDockOpen(false);
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [dockOpen]);

    // × is a chat edit (REMOVE) so the planner's packages stay the truth.
    const removeItem = (pkg, item) => {
        setRemoving(item.name);
        planner.send(t('result.removeMessage', {name: item.name, tier: t(`tiers.${pkg.key}`)}));
    };

    // "Ask the group": the picked trim replaces the cart (so the Trip Builder
    // dashboard shows the same plan), then the organizer leaves their contact
    // in the vote modal — which creates the vote and opens the dashboard.
    const askTheGroup = async () => {
        setHandingOff(true);
        setHandoffError(false);
        pushEvent('cta_click', {cta_label: 'Ask the group', block: 'ai_planner'});
        try {
            const picked = await planner.select(generation.id, activeKey);
            dispatch({type: 'SET_TRIP_ITEMS', tripItems: picked.tripItems.map((a) => ({...a, id: a.activityId}))});
            dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: picked.groupSize});
            setDockOpen(false);
            setHandoff({activityIds: picked.tripItems.map((a) => a.activityId), groupSize: picked.groupSize});
        } catch (e) {
            setHandoffError(true);
        } finally {
            setHandingOff(false);
        }
    };

    const starters = (
        <div className="aip-intro">
            <span className="aip-intro-mark" aria-hidden="true"><i className="ph ph-sparkle"/></span>
            <h1 className="aip-intro-title">{t('intro.title')}</h1>
            <p className="aip-intro-sub">{t('intro.subtitle')}</p>
            <div className="aip-starters">
                {STARTER_KEYS.map((key) => (
                    <button key={key} type="button" className="aip-starter"
                            onClick={() => planner.send(t(`intro.starters.${key}`))}>
                        {t(`intro.starters.${key}`)}
                    </button>
                ))}
            </div>
        </div>
    );

    const newChat = (
        <button type="button" className="aip-link-btn" onClick={planner.newChat}>
            <i className="ph ph-plus" aria-hidden="true"/> {t('newChat')}
        </button>
    );

    // "Ask the group", under the collapsed chat bar and inside the open chat
    // (v3 2d) — the hand-off must never need the chat closed first.
    const sendBlock = hasResult && (
        <>
            {handoffError && <div className="ai-error" role="alert">{t('errors.handoff')}</div>}
            <button
                type="button"
                className="aip-cta"
                onClick={askTheGroup}
                disabled={!destination || handingOff || sending || planner.building || generation.textsPending}
            >
                {t('result.sendToPlanner')}
            </button>
        </>
    );

    const brief = generation?.brief || {};
    const startDate = planStart(trip, brief);
    const drawerMeta = [
        brief.groupSize && t('result.people', {count: brief.groupSize}),
        startDate ? formatShortRange(startDate, addDays(startDate, brief.days - 1))
            : brief.days && plural(t, 'result.days', brief.days),
    ].filter(Boolean).join(' · ');

    return (
        <div className={`aip-page ${hasResult ? 'has-result' : 'is-chat'}`}>
            <PageHead>
                <title>{t('meta.title')}</title>
                <meta name="robots" content="noindex"/>
            </PageHead>

            {planner.restoring ? (
                <div className="aip-loading" role="status">{t('loading')}</div>
            ) : !hasResult ? (
                <div className="aip-chat-screen">
                    <AiThread planner={planner} variant="full" emptyState={starters}
                              footer={planner.messages.length > 0 ? newChat : null}/>
                </div>
            ) : (
                <>
                    <main className={`aip-canvas ${dockOpen ? 'is-dimmed' : ''}`}>
                        <AiPackageView
                            generation={generation}
                            destinationName={destination?.name}
                            startDate={startDate}
                            activeKey={activeKey}
                            onTierChange={setActiveKey}
                            onRemove={removeItem}
                            removing={removing}
                            onAskAi={() => setDockOpen(true)}
                            onUndo={planner.undo}
                            busy={sending || planner.building}
                        />
                    </main>

                    {dockOpen && <div className="aip-scrim" onClick={() => setDockOpen(false)} aria-hidden="true"/>}
                    <div className={`aip-dock ${dockOpen ? 'is-open' : ''}`}>
                        {dockOpen ? (
                            <div className="aip-drawer" role="dialog" aria-label={t('dock.title')}>
                                <div className="aip-drawer-head">
                                    <span className="ai-avatar" aria-hidden="true"><i className="ph ph-sparkle"/></span>
                                    <div className="aip-drawer-title">
                                        {t('dock.title')}
                                        {drawerMeta && <span>{drawerMeta}</span>}
                                    </div>
                                    {newChat}
                                    <button type="button" className="aip-icon-btn" aria-label={t('dock.collapse')}
                                            onClick={() => setDockOpen(false)}>
                                        <i className="ph ph-caret-down" aria-hidden="true"/>
                                    </button>
                                </div>
                                <AiThread
                                    planner={planner}
                                    variant="drawer"
                                    placeholder={t('dock.placeholder')}
                                    afterMessages={(
                                        <>
                                            <TrimCards generation={generation} activeKey={activeKey}
                                                       onTierChange={setActiveKey}/>
                                            <div className="aip-drawer-send">{sendBlock}</div>
                                        </>
                                    )}
                                />
                            </div>
                        ) : (
                            <>
                                <button type="button" className="aip-pill" onClick={() => setDockOpen(true)}>
                                    <i className="ph ph-sparkle aip-pill-mark" aria-hidden="true"/>
                                    <span className="aip-pill-text">
                                        {sending || planner.building ? t('dock.working') : t('dock.pill')}
                                    </span>
                                    <i className="ph ph-caret-up aip-pill-caret" aria-hidden="true"/>
                                </button>
                                {sendBlock}
                            </>
                        )}
                    </div>
                </>
            )}

            <StartGroupVoteModal
                isOpen={Boolean(handoff)}
                onClose={() => setHandoff(null)}
                destinationId={destination?.id}
                destinationName={destination?.name}
                destinationSlug={destination?.slug}
                activityIds={handoff?.activityIds || []}
                numberOfTravelers={handoff?.groupSize}
                startDate={trip.tripStartDate}
                endDate={trip.tripEndDate}
            />
        </div>
    );
}

export default AiPlannerPage;
