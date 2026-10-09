import {useEffect, useRef, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import PageHead from '../components/PageHead';
import AiThread from '../components/ai/AiThread';
import AiPackageView, {AI_PICK_KEY} from '../components/ai/AiPackageView';
import AiOffers from '../components/ai/AiOffers';
import ActivityPreviewModal from '../components/ActivityPreviewModal';
import StartGroupVoteModal from '../components/vote/StartGroupVoteModal';
import {useAiPlanner} from '../hooks/useAiPlanner';
import {useTrip} from '../context/TripContext';
import {useCatalog} from '../context/CatalogContext';
import {DEFAULT_DESTINATION_SLUG} from '../services/config';
import {pushEvent} from '../utils/analytics';
import {formatDayRange, nightsBetween, parseISODate} from '../utils/format';
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

// What the page itself said in a chat (a tapped theme, the question back, an undo), kept with the
// chat's token so a reload shows the same conversation.
const SAID_KEY = 'myhive-ai-said';

function readSaid(token) {
    try {
        const stored = JSON.parse(window.localStorage.getItem(SAID_KEY) || 'null');
        return stored && stored.token === token && Array.isArray(stored.lines) ? stored.lines : [];
    } catch (e) {
        return [];
    }
}

function writeSaid(token, lines) {
    try {
        if (token) window.localStorage.setItem(SAID_KEY, JSON.stringify({token, lines}));
    } catch (e) {
        // Private mode: the lines are kept for this visit only.
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
    const tHome = useT('home');
    const navigate = useNavigate();
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
    const [handoff, setHandoff] = useState(null); // {activityIds, groupSize, activityDays} once a trim is picked
    const [handingOff, setHandingOff] = useState(false);
    const [handoffError, setHandoffError] = useState(false);
    const [preview, setPreview] = useState(null); // the draft row or recommendation whose card is open
    const [asked, setAsked] = useState(null); // the theme tag whose activities are listed in the chat
    // What the page itself says in the chat - a tapped theme, the question back, an undo - each placed
    // after the planner message count it was said at: [{id, role, content, after}].
    const [said, setSaid] = useState([]);
    const [offersClosed, setOffersClosed] = useState(false); // the list a typed wish got has done its job
    const {generation, sending} = planner;
    const hasResult = Boolean(generation?.packages?.length);

    // A brief from the homepage starts a fresh chat with its days and head-count
    // preset, even over an older chat in storage: the visitor just asked anew.
    // Dates and a head-count picked without a message (the pickers on the homepage, the cart) are a
    // brief too: with no chat under way they go in as its first message, so the chat never asks for
    // what the visitor has already said.
    const pickedTrip = () => {
        const from = parseISODate(trip.tripStartDate);
        const to = parseISODate(trip.tripEndDate);
        if (planner.token || planner.messages.length || !from || !to || to < from || !(trip.tripTravelers >= 2)) {
            return null;
        }
        const nights = nightsBetween(from, to);
        const nightsLabel = nights === 0 ? tHome('planner.nights.zero')
            : tHome(`planner.nights.${nights === 1 ? 'one' : 'other'}`, {count: nights});
        return {
            message: tHome('planner.defaultMessage', {
                nights: nightsLabel, people: trip.tripTravelers, range: formatDayRange(from, to),
            }),
            days: Math.min(7, nights + 1),
            groupSize: trip.tripTravelers,
        };
    };
    const draftHandled = useRef(false);
    useEffect(() => {
        // The trip is read back from storage after the first render: wait for it.
        if (planner.restoring || !trip.restored || draftHandled.current) return;
        draftHandled.current = true;
        const draft = takeDraft() || pickedTrip();
        if (!draft) return;
        if (planner.token || planner.messages.length) planner.newChat();
        const preset = {};
        if (draft.days) preset.days = draft.days;
        if (draft.groupSize) preset.groupSize = draft.groupSize;
        planner.send(draft.message, preset);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [planner, trip.restored]);

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
            // A chat that comes back brings the page's own lines with it.
            setSaid(readSaid(planner.token).filter((line) => line.after >= from));
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
            setSaid([]);
            writeSaid(planner.token, []);
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
        // Escape puts the chat away - unless a dialog is open over it: then the key is the dialog's.
        // Looked at on the way down (capture), before the dialog's own handler closes and removes it.
        const onKey = (e) => e.key === 'Escape' && !document.querySelector('.app-modal') && setDockOpen(false);
        window.addEventListener('keydown', onKey, true);
        return () => window.removeEventListener('keydown', onKey, true);
    }, [dockOpen]);

    // What the docked chat talks to: the same planner, but a message sent from
    // here makes the plan the organizer's own draft and opens the chat on it.
    const say = (...lines) => setSaid((prev) => {
        const next = [...prev, ...lines.map(([role, content], i) => ({
            id: `said-${prev.length + i}`, role, content, after: planner.messages.length,
        }))];
        writeSaid(planner.token, next);
        return next;
    });
    // The planner's messages since the plan landed, with the page's own lines where they were said.
    const transcript = [];
    for (let i = draftFrom; i <= planner.messages.length; i++) {
        transcript.push(...said.filter((line) => line.after === i || (i === draftFrom && line.after < i)));
        if (i < planner.messages.length) transcript.push(planner.messages[i]);
    }
    const dockPlanner = {
        ...planner,
        messages: transcript,
        send: async (text, preset) => {
            setChatted(true);
            setShowAll(false);
            setDockOpen(true);
            setAsked(null);
            setOffersClosed(false);
            await planner.send(text, preset);
        },
    };

    // Looking at a trim is not choosing it: all three stay up until the organizer changes something
    // (adds, removes, moves or writes), and only then is the one on screen their own plan.
    const pickTier = (key) => setActiveKey(key);

    // × and the recommendation row edit the draft directly - no chat turn - so
    // the planner's packages stay the truth and the next turn sees the change.
    const removeItem = (pkg, item) => {
        setChatted(true);
        planner.editDraft('REMOVE', item.activityId, pkg.key);
    };
    // A row dragged onto another day: the activity stays, its day changes.
    const moveItem = (pkg, item, dayNumber) => {
        setChatted(true);
        planner.editDraft('MOVE', item.activityId, pkg.key, dayNumber);
    };

    // The trim on screen replaces the cart, so the Trip Builder shows the same plan.
    const pickIntoCart = async () => {
        const picked = await planner.select(generation.id, activeKey);
        dispatch({type: 'SET_TRIP_ITEMS', tripItems: picked.tripItems.map((a) => ({...a, id: a.activityId}))});
        dispatch({type: 'UPDATE_TRIP_TRAVELERS', travelers: picked.groupSize});
        return picked;
    };

    // "Ask the group": the organizer leaves their contact in the vote modal - which creates the vote
    // and opens the dashboard.
    const askTheGroup = async () => {
        setHandingOff(true);
        setHandoffError(false);
        pushEvent('cta_click', {cta_label: 'Ask the group', block: 'ai_planner'});
        try {
            const picked = await pickIntoCart();
            setDockOpen(false);
            // The day each activity is planned for goes with the vote, so the group sees the plan by day.
            const activityDays = {};
            (activePkg?.days || []).forEach((day) => day.items.forEach((item) => {
                activityDays[item.activityId] = day.dayNumber;
            }));
            setHandoff({
                activityIds: picked.tripItems.map((a) => a.activityId), groupSize: picked.groupSize, activityDays,
            });
        } catch (e) {
            setHandoffError(true);
        } finally {
            setHandingOff(false);
        }
    };

    // "Complete booking": no vote - straight to the Trip Builder's booking form with this plan.
    const completeBooking = async () => {
        setHandingOff(true);
        setHandoffError(false);
        pushEvent('cta_click', {cta_label: 'Complete Booking', block: 'ai_planner'});
        try {
            await pickIntoCart();
            navigate(`/destination/${destination.slug}?tab=trip-builder&book=1`);
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
    const planIsTheirs = (chatted || generation?.kind === 'EDITED') && !showAll;
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
            {/* Once the plan is the organizer's own: price it and go to the booking page, no vote. */}
            {planIsTheirs && <p className="aip-cta-note">{t('result.bookWithoutVote')}</p>}
            {planIsTheirs && (
                <button
                    type="button"
                    className="aip-cta aip-cta--quiet"
                    onClick={completeBooking}
                    disabled={!destination || handingOff || sending || planner.building || generation.textsPending}
                >
                    {t('result.completeBooking')}
                </button>
            )}
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
    const notInPlan = (offer) => !inDraft.has(offer.activityId);
    const kindLabel = (gap) => {
        const label = t(`dock.kinds.${gap.categorySlug}`);
        // A category without a wording of its own goes by its catalog name.
        return label.includes('dock.kinds.') ? gap.name.toLowerCase() : label;
    };
    // A tapped theme is said in the chat like a typed line, with the question back; its activities
    // are listed under that, and the themes step aside until one is added or the list is closed.
    const askKind = (gap) => {
        setDockOpen(true);
        const asking = ['user', t('dock.addKind', {name: kindLabel(gap)})];
        if (!(gap.options || []).some(notInPlan)) {
            // Nothing of that kind is left to add: said, and the themes stay.
            say(asking, ['assistant', t('dock.allIn')]);
            return;
        }
        setAsked(gap);
        say(asking, ['assistant', t('dock.whichOne')]);
    };
    const keepChatting = () => {
        setAsked(null);
        setOffersClosed(true);
        document.querySelector('.aip-dock .ai-composer-input')?.focus();
    };
    const undoLast = async () => {
        await planner.undo(activePkg.key);
        say(['assistant', t('dock.undone')]);
    };
    // The list is about the trim on screen: another trim ends it.
    useEffect(() => {
        setAsked(null);
    }, [workingKey]);
    // Activities on offer in the chat right now: a tapped theme's, or what a typed wish got back.
    // What is in the plan already is not offered again.
    const typedOffers = offersClosed ? [] : planner.recommendations || [];
    const listing = (asked ? asked.options || [] : typedOffers).filter(notInPlan);
    // A list opens with what led to it in view - what was asked and the line back - not scrolled to
    // its own end: the chat is read from there down.
    const listKey = listing.map((offer) => offer.activityId).join();
    useEffect(() => {
        if (!listKey) return undefined;
        const timer = setTimeout(() => {
            const lines = document.querySelectorAll('.aip-dock .ai-thread-viewport .ai-msg:not(.aip-opening)');
            const from = lines[lines.length - 2] || lines[lines.length - 1];
            from?.scrollIntoView?.({block: 'start'});
        }, 80);
        return () => clearTimeout(timer);
    }, [listKey]);
    // The themes the plan could take next, in two rows right over the input - never together with a
    // list of activities: a list is the answer to a theme, and the themes come back once it is done.
    const capital = (text) => text.charAt(0).toUpperCase() + text.slice(1);
    const tags = gaps.length > 0 && listing.length === 0 && !busy && (
        <div className="aip-themes">
            <p className="aip-themes-ask">{t('dock.whatToAdd')}</p>
            <div className="aip-gaps" role="group" aria-label={t('dock.gapsAria')}>
                {gaps.map((gap) => (
                    <button key={gap.categorySlug} type="button"
                            aria-label={`+ ${t('dock.addKind', {name: kindLabel(gap)})}`}
                            onClick={() => askKind(gap)}>
                        + {capital(kindLabel(gap))}
                    </button>
                ))}
            </div>
        </div>
    );
    const toggleRecommendation = async (rec, added) => {
        setChatted(true);
        setShowAll(false);
        await planner.editDraft(added ? 'REMOVE' : 'ADD', rec.activityId, activePkg.key);
        // Added: the list has done its job. The chat says what went in and the themes are back.
        if (!added) {
            setAsked(null);
            setOffersClosed(true);
        }
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
                {/* How to move things about, said once, where there is somewhere to move them to. */}
                {activePkg?.days.length > 1 && <p className="ai-thread-text">{t('dock.moveHint')}</p>}
            </div>
        </div>
    );
    // What is offered sits in the chat itself, one under the other, each with its own Add: the
    // activities of a tapped tag, or what the chat recommended for a typed wish. Nothing unasked.
    const offerList = (offers) => (
        <AiOffers
            offers={offers}
            isAdded={(id) => inDraft.has(id)}
            onOpen={setPreview}
            onToggle={toggleRecommendation}
            pendingId={planner.editing}
            disabled={busy}
        />
    );
    // The list sits under the line that announced it, with the way back to typing; the way back from
    // the last change is offered there too, not on the page.
    const offersInChat = listing.length > 0 ? (
        <>
            {offerList(listing)}
            <button type="button" className="ai-chip aip-answer-quiet" onClick={keepChatting}>
                {t('dock.keepChatting')}
            </button>
        </>
    ) : generation?.kind === 'EDITED' && generation.parentId && !busy ? (
        <button type="button" className="ai-chip aip-answer-quiet" onClick={undoLast}>
            {t('dock.undo')}
        </button>
    ) : null;

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
                            onMove={moveItem}
                            removing={removing}
                            busy={busy}
                            cta={sendBlock}
                        />
                    </main>

                    <section className={`aip-dock ${dockOpen ? 'is-open' : ''}`} aria-label={t('dock.title')}>
                        <button type="button" className="aip-dock-bar" aria-expanded={dockOpen}
                                aria-label={dockOpen ? t('dock.collapse') : t('dock.expand')}
                                onClick={() => setDockOpen((open) => !open)}>
                            {/* The handle says which way the chat goes: a wide arrow up to open it,
                                down to put it away. */}
                            <svg className="aip-dock-arrow" width="44" height="12" viewBox="0 0 44 12" fill="none"
                                 stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"
                                 aria-hidden="true">
                                <path d={dockOpen ? 'M3 2.5L22 9.5L41 2.5' : 'M3 9.5L22 2.5L41 9.5'}/>
                            </svg>
                            <span className="aip-dock-row">
                                <span className="aip-dock-mark" aria-hidden="true"><i className="ph ph-sparkle"/></span>
                                {/* Collapsed, the bar is all there is of the chat: its last line. */}
                                <span className="aip-dock-line">{dockOpen ? t('dock.title') : dockLine}</span>
                            </span>
                        </button>
                        <AiThread
                            planner={dockPlanner}
                            variant="drawer"
                            placeholder={t('dock.placeholder')}
                            beforeMessages={opening}
                            afterMessages={offersInChat}
                            aboveComposer={tags}
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
                activityDays={handoff?.activityDays}
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
