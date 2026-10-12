import {useCallback, useEffect, useRef, useState} from 'react';
import aiPlannerApi from '../services/aiPlannerApi';

// Where the contract asks the token to live (docs/api/ai-planner-api.md, Basics).
export const SESSION_STORAGE_KEY = 'myhive-ai-session';
const POLL_MS = 2000;
// The backend itself closes every build: a run that has not reported back after three minutes is failed
// by its sweep (STALE). Polling stops a little after that, never before - a build with a repair call and
// the copy can honestly take longer than a minute and a half, and giving up at 90 s told the organizer
// "took too long" while their packages were still being made.
const POLL_GIVE_UP_MS = 240000;
// A stored token the backend no longer knows, or cannot even read (a 400): drop it and offer a fresh
// chat. Only for a token read back from storage - see `send` for one that is in use.
const DEAD_TOKEN_CODES = new Set(['SESSION_NOT_FOUND', 'Bad Request']);
// Sending the same text again cannot help: the chat or the day has run out, or the planner is off.
const NO_RETRY_CODES = new Set(['SESSION_TURN_LIMIT', 'GENERATION_LIMIT', 'SESSION_DAILY_LIMIT', 'AI_DISABLED',
    'SESSION_NOT_FOUND']);

const readToken = () => {
    try {
        return window.localStorage.getItem(SESSION_STORAGE_KEY);
    } catch (e) {
        return null;
    }
};
const writeToken = (token) => {
    try {
        if (token) window.localStorage.setItem(SESSION_STORAGE_KEY, token);
        else window.localStorage.removeItem(SESSION_STORAGE_KEY);
    } catch (e) {
        // storage blocked: the chat still works, it just won't survive a reload
    }
};

let nextLocalId = 0;
const toEntry = (m) => ({
    id: `m${nextLocalId++}`,
    role: m.role === 'USER' ? 'user' : 'assistant',
    content: m.content,
    at: m.at,
});

const errorCode = (e) => e?.body?.error || null;
// What to tell the organizer. "No connection" is only for a request that got no answer at all: a
// server that answered without a code of its own (a proxy's 403 or 502) is something going wrong
// on our side, and telling them to check their internet sends them looking in the wrong place.
const shownCode = (e) => errorCode(e) || (e?.status ? `HTTP_${e.status}` : 'NETWORK');
// The server is there but could not take it (a restart, a bad gateway): the same text can be sent again.
const serverSide = (e) => e?.status >= 500;
const hasPackages = (generation) => Boolean(generation?.packages?.length);

/**
 * The organizer's planner chat against the public /ai API.
 *
 * `generation` is the one whose packages are on screen: the newest READY one,
 * or a RUNNING one that already carries packages (textsPending). A failed
 * regeneration never replaces it — it only sets `error`.
 */
export function useAiPlanner({destinationSlug, locale, api = aiPlannerApi, pollMs}) {
    const pollInterval = pollMs || POLL_MS;
    const [token, setToken] = useState(null);
    const [restoring, setRestoring] = useState(true);
    const [messages, setMessages] = useState([]);
    const [suggestedReplies, setSuggestedReplies] = useState([]);
    const [sending, setSending] = useState(false);
    const [generation, setGeneration] = useState(null);
    const [building, setBuilding] = useState(false);
    const [watchedId, setWatchedId] = useState(null);
    const [error, setError] = useState(null); // {code, retryText?}
    // Activities the latest turn offered for the draft, best match first.
    const [recommendations, setRecommendations] = useState([]);
    // The trim the chat asked to show ({key: 'BASIC'..|'ALL', at}); `at` makes a repeat a new request.
    const [showRequest, setShowRequest] = useState(null);
    // The activity whose draft tap is in flight, if any.
    const [editing, setEditing] = useState(null);
    // What the latest turn changed in the plan ({applied, at}), so the page can follow an add up.
    const [lastEdit, setLastEdit] = useState(null);
    // Per package key, the kinds of activity it could take next, each with what it offers:
    // [{categorySlug, name, options: [recommendation]}].
    const [gaps, setGaps] = useState({});
    // Per package: activities from the ready-made packages that fit it (one tap adds one).
    const [suggestions, setSuggestions] = useState({});
    // The trim on screen: every message tells the planner it is the one draft to change.
    const workingPackageRef = useRef(null);
    const setWorkingPackage = useCallback((key) => {
        workingPackageRef.current = key || null;
    }, []);
    const tokenRef = useRef(null);

    const adoptSession = useCallback((state) => {
        tokenRef.current = state.token;
        setToken(state.token);
        writeToken(state.token);
        setMessages((state.messages || []).map(toEntry));
        setSuggestedReplies(state.suggestedReplies || []);
        setRecommendations(state.recommendations || []);
        setGaps(state.gaps || {});
        setSuggestions(state.suggestions || {});
        // The trim the organizer made their draft, so a reload lands on it and not on the planner's pick.
        if (state.workingPackage) setShowRequest({key: state.workingPackage, at: Date.now()});
        const ready = state.latestReadyGeneration;
        const latest = state.latestGeneration;
        const inFlight = latest && (latest.status === 'QUEUED' || latest.status === 'RUNNING');
        setGeneration(inFlight && hasPackages(latest) ? latest : ready || null);
        setBuilding(Boolean(inFlight));
        setWatchedId(inFlight ? latest.id : null);
    }, []);

    const reset = useCallback(() => {
        tokenRef.current = null;
        setToken(null);
        writeToken(null);
        setMessages([]);
        setSuggestedReplies([]);
        setGeneration(null);
        setRecommendations([]);
        setGaps({});
        setSuggestions({});
        setShowRequest(null);
        setLastEdit(null);
        autoRebuilt.current = false;
        setBuilding(false);
        setWatchedId(null);
        setError(null);
    }, []);

    // Returning visitor: rebuild the whole screen from the stored token.
    useEffect(() => {
        const stored = readToken();
        if (!stored) {
            setRestoring(false);
            return;
        }
        let cancelled = false;
        api.getSession(stored)
            .then((state) => {
                if (!cancelled) adoptSession(state);
            })
            .catch((e) => {
                if (cancelled) return;
                if (e.status === 404 || DEAD_TOKEN_CODES.has(errorCode(e))) writeToken(null);
                else setError({code: shownCode(e)});
            })
            .finally(() => {
                if (!cancelled) setRestoring(false);
            });
        return () => {
            cancelled = true;
        };
    }, [api, adoptSession]);

    const applyTurn = useCallback(async (turn) => {
        if (turn.edit?.applied?.length) setLastEdit({applied: turn.edit.applied, at: Date.now()});
        setRecommendations(turn.recommendations || []);
        if (turn.gaps) setGaps(turn.gaps);
        if (turn.suggestions) setSuggestions(turn.suggestions);
        if (turn.showPackage) setShowRequest({key: turn.showPackage, at: Date.now()});
        setMessages((prev) => [...prev, ...(turn.messages || []).map(toEntry)]);
        setSuggestedReplies(turn.suggestedReplies || []);
        const gen = turn.generation;
        if (gen && gen.status === 'READY') {
            // An edit: already applied, nothing to poll.
            setGeneration(gen);
        } else if (gen) {
            setBuilding(true);
            setWatchedId(gen.id);
        } else if (turn.edit?.applied?.length) {
            // The edit landed but reading it back failed: the session has it.
            adoptSession(await api.getSession(tokenRef.current));
        }
    }, [api, adoptSession]);

    // `preset` ({days, groupSize}) only counts when this message opens the
    // session: what the homepage pickers chose, so the chat never asks again.
    const send = useCallback(async (text, preset = {}) => {
        const content = text.trim();
        if (!content || sending) return;
        setError(null);
        setSuggestedReplies([]);
        // The backend de-dupes an identical consecutive message, so a retry
        // must not show the user's line twice either.
        setMessages((prev) => {
            const last = prev[prev.length - 1];
            if (last && last.role === 'user' && last.content === content) return prev;
            return [...prev, toEntry({role: 'USER', content, at: new Date().toISOString()})];
        });
        setSending(true);
        try {
            if (!tokenRef.current) {
                const state = await api.createSession(destinationSlug, locale, {...preset, initialMessage: content});
                adoptSession(state);
                if (state.firstTurnError) {
                    setError({code: state.firstTurnError.code, retryText: content});
                }
            } else {
                const draftKey = workingPackageRef.current;
                await applyTurn(await (draftKey
                    ? api.sendMessage(tokenRef.current, content, draftKey)
                    : api.sendMessage(tokenRef.current, content)));
            }
        } catch (e) {
            const code = shownCode(e);
            // "This chat has expired" only when there was a chat and the backend says it is gone. A request
            // that opens one has no chat to expire, and a 400 is the request being refused, not the
            // chat: both used to wipe the conversation and blame its age - which is how a destination
            // the catalog does not have read as an expired chat in a browser that had never had one.
            const hadChat = Boolean(tokenRef.current);
            if (hadChat && (code === 'SESSION_NOT_FOUND' || e.status === 404)) {
                reset();
                setError({code: 'SESSION_NOT_FOUND'});
            } else {
                // Whatever went wrong, the text is still theirs: one tap sends it again, nothing is retyped.
                setError({code, retryText: NO_RETRY_CODES.has(code) ? null : content, retryPreset: preset});
            }
        } finally {
            setSending(false);
        }
    }, [sending, api, destinationSlug, locale, adoptSession, applyTurn, reset]);

    // A build that failed or was lost (the server restarted under it) is started again for the same
    // brief: nothing has to be typed twice.
    const rebuild = useCallback(async () => {
        if (!tokenRef.current) return;
        setError(null);
        try {
            const gen = await api.startGeneration(tokenRef.current);
            setBuilding(true);
            setWatchedId(gen.id);
        } catch (e) {
            setError({code: shownCode(e), retryGeneration: serverSide(e) || !e?.status});
        }
    }, [api]);

    const retry = useCallback(() => {
        if (error?.retryGeneration) rebuild();
        else if (error?.retryText) send(error.retryText, error.retryPreset);
    }, [error, send, rebuild]);

    // A build lost to a server restart (STALE) is started again without being asked, once per chat: the
    // organizer did nothing wrong and should not have to notice. A second loss is shown, with "Try again".
    const autoRebuilt = useRef(false);

    // Poll the generation a turn started until it is READY or FAILED.
    useEffect(() => {
        if (!watchedId) return undefined;
        let timer = null;
        let stopped = false;
        const startedAt = Date.now();
        // Why the latest poll got no generation back, if it did not: said at the end instead of "took
        // too long" when the backend was unreachable the whole time.
        let unreachable = null;
        const tick = async () => {
            try {
                const gen = await api.getGeneration(watchedId);
                if (stopped) return;
                unreachable = null;
                if (gen.status === 'READY') {
                    setGeneration(gen);
                    setBuilding(false);
                    setWatchedId(null);
                    // The session now has chips for "what next", the draft's gaps and fresh limits.
                    // Not guarded by `stopped`: clearing watchedId above ends this effect before the
                    // answer is back. Only a chat that has moved on since must not take it.
                    const token = tokenRef.current;
                    api.getSession(token)
                        .then((state) => {
                            if (tokenRef.current !== token) return;
                            setSuggestedReplies(state.suggestedReplies || []);
                            setGaps(state.gaps || {});
                            setSuggestions(state.suggestions || {});
                        })
                        .catch(() => {});
                    return;
                }
                if (gen.status === 'FAILED') {
                    if (gen.error?.code === 'STALE' && !autoRebuilt.current && tokenRef.current) {
                        autoRebuilt.current = true;
                        try {
                            const again = await api.startGeneration(tokenRef.current);
                            if (!stopped) setWatchedId(again.id);
                            return;
                        } catch (e) {
                            // could not start it again: fall through and say so
                        }
                    }
                    setBuilding(false);
                    setWatchedId(null);
                    setError({code: gen.error?.code || 'INTERNAL', failedGeneration: true,
                        retryGeneration: gen.error?.retryable !== false});
                    return;
                }
                if (hasPackages(gen)) setGeneration(gen);
            } catch (e) {
                // a dropped poll is not fatal; the next tick tries again
                unreachable = shownCode(e);
            }
            if (Date.now() - startedAt > POLL_GIVE_UP_MS) {
                setBuilding(false);
                setWatchedId(null);
                setError({code: unreachable || 'LLM_TIMEOUT', failedGeneration: true, retryGeneration: true});
                return;
            }
            timer = setTimeout(tick, pollInterval);
        };
        timer = setTimeout(tick, pollInterval);
        return () => {
            stopped = true;
            clearTimeout(timer);
        };
    }, [watchedId, api, pollInterval]);

    // Picking a package; also the undo when `id` is an older READY generation.
    const select = useCallback(async (generationId, packageKey) => {
        return api.selectPackage(generationId, packageKey);
    }, [api]);

    // A tap in the draft: add or remove one activity in `packageKey` with no
    // chat turn. The answer is applied like a chat edit (new draft, one line).
    // `extras` is a confirmed card: every code ticked on it (with ADD the activity goes in with them,
    // with ADD_EXTRA the activity's extras become exactly those).
    const editDraft = useCallback(async (op, activityId, packageKey, dayNumber, extras) => {
        if (!tokenRef.current || editing) return;
        setError(null);
        setEditing(activityId);
        try {
            const edit = {op, activityId, packageKey};
            if (dayNumber != null) edit.dayNumber = dayNumber;
            if (extras) edit.extras = extras;
            await applyTurn(await api.editDraft(tokenRef.current, edit));
        } catch (e) {
            setError({code: shownCode(e)});
        } finally {
            setEditing(null);
        }
    }, [api, applyTurn, editing]);

    const undo = useCallback(async (packageKey) => {
        if (!generation?.parentId) return;
        setError(null);
        try {
            const parent = await api.getGeneration(generation.parentId);
            await api.selectPackage(parent.id, packageKey);
            setGeneration(parent);
        } catch (e) {
            setError({code: shownCode(e)});
        }
    }, [api, generation]);

    return {
        token,
        restoring,
        messages,
        suggestedReplies,
        sending,
        generation,
        recommendations,
        gaps,
        suggestions,
        setWorkingPackage,
        showRequest,
        editing,
        lastEdit,
        building,
        error,
        send,
        retry,
        select,
        undo,
        editDraft,
        newChat: reset,
    };
}
