import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import PageHead from '../components/PageHead';
import AiThread from '../components/ai/AiThread';
import AiPackageView, {AI_PICK_KEY} from '../components/ai/AiPackageView';
import AiRecommendations from '../components/ai/AiRecommendations';
import ActivityPreviewModal from '../components/ActivityPreviewModal';
import StartGroupVoteModal from '../components/vote/StartGroupVoteModal';
import {useAiPlanner} from '../hooks/useAiPlanner';
import {useTrip} from '../context/TripContext';
import {useCatalog} from '../context/CatalogContext';
import {DEFAULT_DESTINATION_SLUG} from '../services/config';
import {pushEvent} from '../utils/analytics';
import {nightsBetween, parseISODate} from '../utils/format';
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

// Where the docked chat's transcript starts, per chat: the number of messages said before its packages
// landed. Kept in the browser so a reload cuts at the same place.
const DRAFT_FROM_KEY = 'myhive-ai-draft-from';

function readDraftFrom(token) {
    try {
        const stored = JSON.parse(window.localStorage.getItem(DRAFT_FROM_KEY) || 'null');
        return stored && stored.token === token && Number.isInteger(stored.from) ? stored.from : null;
    } catch (e) {
        return null;
    }
}

function writeDraftFrom(token, from) {
    try {
        if (token) window.localStorage.setItem(DRAFT_FROM_KEY, JSON.stringify({token, from}));
    } catch (e) {
        // Private mode: the cut is kept for this visit only.
    }
}

// Stag Do AI (v3 landing, 2d/2e). Result-first: until there are packages the
// chat is the page; once there are, the trip draft is the page and the chat is
// docked to the bottom edge, where its top bar opens and collapses it. The
// three trims are offered - tapped through, compared - until the organizer
// starts changing the plan; from then on there is one draft, theirs - the chat
// can still bring a trim, or all three, back ("show me Premium", "what were the
// other options?").
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
    // How many messages were said before the packages landed. The docked chat starts after them: what
    // was said to gather the brief - down to "building your three options now" - is not about the draft.
    const [draftFrom, setDraftFrom] = useState(0);
    const [chatted, setChatted] = useState(false); // the organizer has asked for a change (a message, an edit)
    const [showAll, setShowAll] = useState(false); // the chat brought every trim back
    const [handoff, setHandoff] = useState(null); // {activityIds, groupSize} once a trim is picked
    const [handingOff, setHandingOff] = useState(false);
    const [handoffError, setHandoffError] = useState(false);
    const [preview, setPreview] = useState(null); // the draft row or recommendation whose card is open
    const [asked, setAsked] = useState(null); // {gap, answer}: the "+ Add ..." tag being answered
    const [offerTurn, setOfferTurn] = useState(0); // messages sent under the draft: each may bring a new offer
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

    // Packages land, or come back with a stored chat: the chat opens on them, docked under the draft
    // with what could go in next. A chat that comes back keeps the cut it was given when they landed.
    const hadResult = useRef(false);
    useEffect(() => {
        if (hasResult && !hadResult.current) {
            setDockOpen(true);
            const stored = readDraftFrom(planner.token);
            const from = stored == null ? planner.messages.length : Math.min(stored, planner.messages.length);
            setDraftFrom(from);
            if (stored == null) writeDraftFrom(planner.token, from);
        }
        if (!hasResult) setDraftFrom(0);
        hadResult.current = hasResult;
    }, [hasResult, planner.token, planner.messages.length]);

    // A rebuild (another ready-made weekend, a longer trip) lands as a new plan: the talk that asked
    // for it is done with, and the chat starts afresh on it like it did on the first one.
    const builtId = generation && generation.kind !== 'EDITED' ? generation.id : null;
    const lastBuiltId = useRef(null);
    useEffect(() => {
        if (!builtId) return;
        if (lastBuiltId.current && lastBuiltId.current !== builtId) {
            setDraftFrom(planner.messages.length);
            writeDraftFrom(planner.token, planner.messages.length);
        }
        lastBuiltId.current = builtId;
        // Only a new plan moves the cut; messages written after it stay in view.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [builtId]);

    // The chat asked to switch the draft to a trim or to show all three again; a chat that comes back
    // says the same way which trim was the draft.
    const {showRequest} = planner;
    useEffect(() => {
        if (!showRequest) return;
        if (showRequest.key === 'ALL') {
            setShowAll(true);
        } else {
            setActiveKey(showRequest.key);
            setShowAll(false);
            setChatted(true);
        }
    }, [showRequest]);

    useEffect(() => {
        if (!dockOpen) return undefined;
        const onKey = (e) => e.key === 'Escape' && setDockOpen(false);
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [dockOpen]);

    // What the docked chat talks to: the same planner, but a message sent from
    // here makes the plan the organizer's own draft and opens the chat on it.
    const dockPlanner = {
        ...planner,
        messages: planner.messages.slice(draftFrom),
        send: (text, preset) => {
            setChatted(true);
            setShowAll(false);
            setDockOpen(true);
            setOfferTurn((turn) => turn + 1);
            return planner.send(text, preset);
        },
    };

    // A tap on a trim only brings it on screen: the organizer compares the three by tapping through
    // them, and the trims stay until they change the plan (a message, an edit) - a tap used to count
    // as that change and took the other two trims away with nothing chosen yet.
    const pickTier = (key) => {
        setActiveKey(key);
    };

    // × and the recommendation row edit the draft directly - no chat turn - so
    // the planner's packages stay the truth and the next turn sees the change.
    const removeItem = (pkg, item) => {
        setChatted(true);
        planner.editDraft('REMOVE', item.activityId, pkg.key);
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

    // "Ask the group", right under the draft it sends.
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
    const custom = (chatted || generation?.kind === 'EDITED') && !showAll;
    const activePkg = hasResult
        ? generation.packages.find((p) => p.key === activeKey) || generation.packages[0] : null;
    const inDraft = new Set(activePkg ? activePkg.days.flatMap((day) => day.items.map((item) => item.activityId)) : []);
    const removing = activePkg && planner.editing
        ? activePkg.days.flatMap((day) => day.items).find((item) => item.activityId === planner.editing)?.name
        : null;
    const busy = sending || planner.building || Boolean(planner.editing);
    // The trim on screen is the one draft: messages change it and nothing else.
    const workingKey = activePkg?.key || null;
    const {setWorkingPackage} = planner;
    useEffect(() => {
        setWorkingPackage(workingKey);
    }, [workingKey, setWorkingPackage]);
    // What the plan could take next, by kind - "+ Add shooting", "+ Add strippers" - most wanted first.
    // A tap asks which one, with the kind's activities as the answers; the answer is shown as a card
    // with Add. None of it is a chat turn: the answers are already here.
    const gaps = (workingKey && planner.gaps?.[workingKey]) || [];
    const suggested = ((workingKey && planner.suggestions?.[workingKey]) || [])
        .filter((rec) => !inDraft.has(rec.activityId));
    const kindLabel = (gap) => {
        const label = t(`dock.kinds.${gap.categorySlug}`);
        // A category without a wording of its own goes by its catalog name.
        return label.includes('dock.kinds.') ? gap.name.toLowerCase() : label;
    };
    const askKind = (gap) => {
        setDockOpen(true);
        setAsked({gap, answer: null});
    };
    const answerKind = (rec) => setAsked((current) => current && {...current, answer: rec});
    const keepChatting = () => {
        setAsked(null);
        document.querySelector('.aip-dock .ai-composer-input')?.focus();
    };
    // The question is about the plan as it was: a reply, an edit or another trim ends it.
    const transcriptLength = planner.messages.length;
    useEffect(() => {
        setAsked(null);
    }, [transcriptLength, workingKey]);
    const tags = gaps.length > 0 && (
        <div className="aip-gaps" role="group" aria-label={t('dock.gapsAria')}>
            {gaps.map((gap) => (
                <button key={gap.categorySlug} type="button" disabled={busy}
                        aria-pressed={asked?.gap.categorySlug === gap.categorySlug}
                        aria-label={`+ ${t('dock.addKind', {name: kindLabel(gap)})}`}
                        onClick={() => askKind(gap)}>
                    {/* Narrow phones drop the verb so three tags fit a row: "+ Shooting". */}
                    + <span className="aip-gap-verb">{t('dock.addVerb')} </span>
                    <span className="aip-gap-kind">{kindLabel(gap)}</span>
                </button>
            ))}
        </div>
    );
    // The tag's question in the chat: what was tapped, "which one?", and the answers to tap - or, once
    // one is picked, where to find it.
    const kindOptions = asked ? (asked.gap.options || []).filter((rec) => !inDraft.has(rec.activityId)) : [];
    const question = asked && (
        <>
            <div className="ai-msg ai-msg-user">
                <div className="ai-bubble">
                    <p className="ai-thread-text">{t('dock.addKind', {name: kindLabel(asked.gap)})}</p>
                </div>
            </div>
            <div className="ai-msg ai-msg-assistant">
                <div className="ai-bubble">
                    <p className="ai-thread-text">{t(asked.answer ? 'dock.cardAbove' : 'dock.whichOne')}</p>
                </div>
            </div>
            {!asked.answer && (
                <div className="aip-answers" role="group" aria-label={t('dock.whichOne')}>
                    {kindOptions.map((rec) => (
                        <button key={rec.activityId} type="button" className="ai-chip" onClick={() => answerKind(rec)}>
                            {rec.name}
                        </button>
                    ))}
                    <button type="button" className="ai-chip aip-answer-quiet" onClick={keepChatting}>
                        {t('dock.keepChatting')}
                    </button>
                </div>
            )}
        </>
    );
    const showRecommendations = (planner.recommendations || []).length > 0;
    const toggleRecommendation = (rec, added) => {
        setChatted(true);
        setShowAll(false);
        planner.editDraft(added ? 'REMOVE' : 'ADD', rec.activityId, activePkg.key);
    };
    // The chat's own first line on the draft, in place of the brief talk that came before it. Collapsed,
    // the bar shows the same line (or the latest reply once the organizer has written) over the tags.
    const people = generation?.brief?.groupSize;
    const openingText = !custom ? t('dock.optionsReady')
        : people ? t('dock.draftSet', {count: people}) : t('dock.draftSetNoCount');
    const lastDraftReply = [...planner.messages.slice(draftFrom)].reverse().find((m) => m.role === 'assistant');
    const dockLine = busy ? t('dock.working') : lastDraftReply?.content || openingText;
    const opening = (
        <div className="ai-msg ai-msg-assistant aip-opening">
            <div className="ai-bubble">
                <p className="ai-thread-text">{openingText}</p>
            </div>
        </div>
    );
    // Above the open chat: what the chat recommended, else what the ready-made packages suggest -
    // the first as a card with Add, the rest as tags.
    // A picked answer comes first, with the rest of its kind as the tags next to it.
    const offered = asked?.answer
        ? [asked.answer, ...kindOptions.filter((rec) => rec.activityId !== asked.answer.activityId)]
        : showRecommendations ? planner.recommendations : suggested;
    // A new offer - a chat turn, a tag answer, another trim - opens the sheet on its top match again;
    // an Add is none of these, so the open list stays open under the organizer's finger.
    const offerKey = `${offerTurn}:${asked?.answer?.activityId || ''}:${workingKey || ''}`;

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
                    <AiThread planner={planner} variant="full" emptyState={starters}/>
                </div>
            ) : (
                <>
                    <main className={`aip-canvas ${dockOpen ? 'is-chat-open' : ''}`}>
                        <AiPackageView
                            generation={generation}
                            destinationName={destination?.name}
                            destinationSlug={destination?.slug}
                            startDate={startDate}
                            activeKey={activeKey}
                            onTierChange={pickTier}
                            custom={custom}
                            onOpen={setPreview}
                            onRemove={removeItem}
                            removing={removing}
                            onUndo={planner.undo}
                            busy={busy}
                            cta={sendBlock}
                        />
                    </main>

                    <section className={`aip-dock ${dockOpen ? 'is-open' : ''}`} aria-label={t('dock.title')}>
                        <button type="button" className="aip-dock-bar" aria-expanded={dockOpen}
                                aria-label={dockOpen ? t('dock.collapse') : t('dock.expand')}
                                onClick={() => setDockOpen((open) => !open)}>
                            <span className="aip-dock-mark" aria-hidden="true"><i className="ph ph-sparkle"/></span>
                            <span className="aip-dock-grip" aria-hidden="true"/>
                            <span className="aip-dock-caret" aria-hidden="true">
                                <i className={`ph ph-caret-${dockOpen ? 'down' : 'up'}`}/>
                            </span>
                        </button>
                        {dockOpen && (
                            <AiRecommendations
                                recommendations={offered}
                                resetKey={offerKey}
                                isAdded={(id) => inDraft.has(id)}
                                onOpen={setPreview}
                                onToggle={toggleRecommendation}
                                pendingId={planner.editing}
                                disabled={busy}
                            />
                        )}
                        {/* Collapsed under a ready draft: the question and what could go in next.
                            While the planner is working, what it is doing. */}
                        {!dockOpen && <div className="aip-dock-line">{dockLine}</div>}
                        {!dockOpen && !busy && tags}
                        <AiThread
                            planner={dockPlanner}
                            variant="drawer"
                            placeholder={t('dock.placeholder')}
                            beforeMessages={opening}
                            afterMessages={question}
                            aboveComposer={dockOpen && !busy && !(asked && !asked.answer) ? tags : null}
                            buildingText={t('dock.working')}
                        />
                    </section>
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
            {/* A recommendation's card can add it; a draft row's card only goes back. */}
            <ActivityPreviewModal
                activity={preview && {
                    name: preview.name, imageUrl: preview.imageUrl, duration: preview.durationMinutes,
                }}
                activityId={preview?.activityId}
                onClose={() => setPreview(null)}
                closeLabel={t('result.backToDraft')}
                action={preview && !inDraft.has(preview.activityId) ? {
                    label: t('result.addToDraft'),
                    disabled: busy,
                    onClick: () => toggleRecommendation(preview, false),
                } : undefined}
            />
        </div>
    );
}

export default AiPlannerPage;
