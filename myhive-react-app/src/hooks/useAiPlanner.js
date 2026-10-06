import {useCallback, useEffect, useRef, useState} from 'react';
import aiPlannerApi from '../services/aiPlannerApi';

// Where the contract asks the token to live (docs/api/ai-planner-api.md, Basics).
export const SESSION_STORAGE_KEY = 'myhive-ai-session';
const POLL_MS = 2000;
const POLL_GIVE_UP_MS = 90000;
// A token the backend no longer knows: drop it and offer a fresh chat.
const DEAD_TOKEN_CODES = new Set(['SESSION_NOT_FOUND', 'Bad Request']);
// A reply that failed while the user's message was saved: re-send the same text.
const RETRYABLE_CODES = new Set(['LLM_UNAVAILABLE', 'LLM_TIMEOUT', 'SESSION_BUSY', 'AI_BUSY']);

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
    // Catalog names the latest turn offered instead of something it could not do.
    const [alternatives, setAlternatives] = useState([]);
    const tokenRef = useRef(null);

    const adoptSession = useCallback((state) => {
        tokenRef.current = state.token;
        setToken(state.token);
        writeToken(state.token);
        setMessages((state.messages || []).map(toEntry));
        setSuggestedReplies(state.suggestedReplies || []);
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
        setAlternatives([]);
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
                else setError({code: errorCode(e) || 'NETWORK'});
            })
            .finally(() => {
                if (!cancelled) setRestoring(false);
            });
        return () => {
            cancelled = true;
        };
    }, [api, adoptSession]);

    const applyTurn = useCallback(async (turn) => {
        setAlternatives([...new Set((turn.edit?.rejected || []).flatMap((r) => r.alternatives || []))]);
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
                await applyTurn(await api.sendMessage(tokenRef.current, content));
            }
        } catch (e) {
            const code = errorCode(e) || 'NETWORK';
            if (DEAD_TOKEN_CODES.has(code) || e.status === 404) {
                reset();
                setError({code: 'SESSION_NOT_FOUND'});
            } else {
                setError({code, retryText: RETRYABLE_CODES.has(code) || code === 'NETWORK' ? content : null, retryPreset: preset});
            }
        } finally {
            setSending(false);
        }
    }, [sending, api, destinationSlug, locale, adoptSession, applyTurn, reset]);

    const retry = useCallback(() => {
        if (error?.retryText) send(error.retryText, error.retryPreset);
    }, [error, send]);

    // Poll the generation a turn started until it is READY or FAILED.
    useEffect(() => {
        if (!watchedId) return undefined;
        let timer = null;
        let stopped = false;
        const startedAt = Date.now();
        const tick = async () => {
            try {
                const gen = await api.getGeneration(watchedId);
                if (stopped) return;
                if (gen.status === 'READY') {
                    setGeneration(gen);
                    setBuilding(false);
                    setWatchedId(null);
                    // The session now has chips for "what next" and fresh limits.
                    api.getSession(tokenRef.current)
                        .then((state) => !stopped && setSuggestedReplies(state.suggestedReplies || []))
                        .catch(() => {});
                    return;
                }
                if (gen.status === 'FAILED') {
                    setBuilding(false);
                    setWatchedId(null);
                    setError({code: gen.error?.code || 'INTERNAL', failedGeneration: true});
                    return;
                }
                if (hasPackages(gen)) setGeneration(gen);
            } catch (e) {
                // a dropped poll is not fatal; the next tick tries again
            }
            if (Date.now() - startedAt > POLL_GIVE_UP_MS) {
                setBuilding(false);
                setWatchedId(null);
                setError({code: 'LLM_TIMEOUT', failedGeneration: true});
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

    const undo = useCallback(async (packageKey) => {
        if (!generation?.parentId) return;
        setError(null);
        try {
            const parent = await api.getGeneration(generation.parentId);
            await api.selectPackage(parent.id, packageKey);
            setGeneration(parent);
        } catch (e) {
            setError({code: errorCode(e) || 'NETWORK'});
        }
    }, [api, generation]);

    return {
        token,
        restoring,
        messages,
        suggestedReplies,
        sending,
        generation,
        alternatives,
        building,
        error,
        send,
        retry,
        select,
        undo,
        newChat: reset,
    };
}
