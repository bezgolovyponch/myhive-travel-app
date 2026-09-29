import {useCallback, useEffect, useRef, useState} from 'react';
import {Alert, Badge, Button, Card, Col, Form, Row} from 'react-bootstrap';
import {useAdminApi} from '../hooks/useAdminApi';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';

// The planner test bench for colleagues: the same /ai/** endpoints the public will use, called with
// the console's own token (which lifts the captcha, the daily cap and the public kill switch).
// State is kept the way the endpoints hand it back, so what a tester sees is what the API says.

const STORAGE_KEY = 'trivlu-admin-ai-planner-session';
const DEFAULT_POLL_MS = 2000;
const EDGES = ['MORNING', 'AFTERNOON', 'EVENING'];
// Staff test prompts; the organizer's own chips come from the server as suggestedReplies.
const QUICK_PROMPTS = [
    'We like beer, karting and a big night out. Mid budget.',
    'Arriving Friday evening, leaving Sunday afternoon.',
    'Remove the karting from all packages.',
    'Add a beer tasting to the medium package.',
    'Swap the club night in premium for a boat party.',
    'Actually we are 12 people now.',
];

function money(value, currency) {
    return value == null ? '—' : `${Number(value).toFixed(2)} ${currency || 'EUR'}`;
}

function errorText(err) {
    const code = err.body && err.body.error;
    return [err.status, code, err.message].filter(Boolean).join(' ');
}

function readSavedToken() {
    try {
        return localStorage.getItem(STORAGE_KEY);
    } catch (e) {
        return null; // private mode: resume just will not work
    }
}

const MAX_VIOLATIONS_SHOWN = 8;

// One line per planner draft that did not pass as written. A failed call (timeout, unavailable,
// unparseable JSON) leaves an empty draft, so its error code is the cause, not the MISSING_TIER violations
// that follow from it. A draft with fixes was kept: Java corrected it and the plan is still the model's.
function draftLines(diagnostics) {
    return (diagnostics || []).map((a) => {
        const label = a.attempt === 0 ? 'compose' : 'repair';
        if (a.errorCode) {
            return `${label}: model call failed (${a.errorCode})`;
        }
        const fixes = a.fixes || [];
        if (fixes.length > 0) {
            return `${label}: kept, Java corrected ${fixes.length} slip(s) — ${fixes.join('; ')}`;
        }
        const shown = a.violations.slice(0, MAX_VIOLATIONS_SHOWN).join('; ');
        const more = a.violations.length > MAX_VIOLATIONS_SHOWN ? ` (+${a.violations.length - MAX_VIOLATIONS_SHOWN} more)` : '';
        return `${label}: ${a.violations.length} rule violation(s) — ${shown}${more}`;
    });
}

// Only what is set: an empty picker leaves the field to the chat.
function presetBody(preset) {
    const body = {};
    if (preset.days) body.days = Number(preset.days);
    if (preset.groupSize) body.groupSize = Number(preset.groupSize);
    if (preset.arrival) body.arrival = preset.arrival;
    if (preset.departure) body.departure = preset.departure;
    return body;
}

function saveToken(token) {
    try {
        localStorage.setItem(STORAGE_KEY, token);
    } catch (e) {
        // private mode: nothing to do
    }
}

function AdminAiPlanner({pollIntervalMs = DEFAULT_POLL_MS}) {
    const adminApi = useAdminApi();
    const handleAuthError = useAuthErrorHandler();

    const [destinations, setDestinations] = useState([]);
    const [destinationSlug, setDestinationSlug] = useState('prague');
    const [locale, setLocale] = useState('en');
    // What the organizer's entry screen will pick before the chat; empty means "let the chat ask".
    const [preset, setPreset] = useState({days: '3', groupSize: '8', arrival: 'EVENING', departure: 'MORNING'});
    const [session, setSession] = useState(null);
    const [log, setLog] = useState([]);
    const [input, setInput] = useState('');
    const [busy, setBusy] = useState('');
    const [sending, setSending] = useState(false);
    const [watched, setWatched] = useState(null);
    const [generation, setGeneration] = useState(null);
    const [edit, setEdit] = useState(null);
    const [selection, setSelection] = useState(null);
    const [raw, setRaw] = useState(null);
    const [timing, setTiming] = useState('');

    const tokenRef = useRef(null);
    const queuedRef = useRef(null);
    const logRef = useRef(null);
    const sendRef = useRef(null);

    const say = useCallback((role, content) => {
        setLog(entries => [...entries, {id: entries.length, role, content}]);
    }, []);

    const fail = useCallback((err) => {
        if (handleAuthError(err)) return;
        say('error', errorText(err));
    }, [handleAuthError, say]);

    const track = useCallback((label, promise) => {
        const started = Date.now();
        return promise.then(result => {
            setRaw({label, result});
            setTiming(`${label}: ${Date.now() - started} ms`);
            return result;
        });
    }, []);

    useEffect(() => {
        if (logRef.current) {
            logRef.current.scrollTop = logRef.current.scrollHeight;
        }
    }, [log]);

    useEffect(() => {
        let cancelled = false;
        adminApi.aiDestinations()
            .then(rows => {
                if (cancelled) return;
                setDestinations(rows);
                if (rows.length && !rows.some(d => d.slug === 'prague')) {
                    setDestinationSlug(rows[0].slug);
                }
            })
            .catch(fail);
        return () => {
            cancelled = true;
        };
    }, [adminApi, fail]);

    const refresh = useCallback(async () => {
        const state = await track('session', adminApi.aiGetSession(tokenRef.current));
        setSession(state);
        return state;
    }, [adminApi, track]);

    const adopt = useCallback((state) => {
        tokenRef.current = state.token;
        saveToken(state.token);
        setSession(state);
        setLog((state.messages || []).map((m, index) => ({id: index, role: m.role, content: m.content})));
        setEdit(null);
        setSelection(null);
        setGeneration(state.latestReadyGeneration || null);
        if (state.firstTurnError) {
            say('error', `first turn failed: ${state.firstTurnError.code} — send the message again`);
        }
        const latest = state.latestGeneration;
        const inFlight = latest && ['QUEUED', 'RUNNING'].includes(latest.status);
        if (inFlight || (latest && !state.latestReadyGeneration)) {
            setGeneration(latest);
            setWatched(inFlight ? latest : null);
        }
        setBusy('');
    }, [say]);

    const newChat = useCallback(async () => {
        setBusy('creating');
        try {
            adopt(await track('create', adminApi.aiCreateSession(destinationSlug, locale, presetBody(preset))));
            return true;
        } catch (err) {
            setBusy('');
            fail(err);
            return false;
        }
    }, [adminApi, adopt, destinationSlug, fail, locale, preset, track]);

    const resume = useCallback(async () => {
        const saved = readSavedToken();
        if (!saved) {
            say('system', 'no saved chat in this browser');
            return;
        }
        try {
            adopt(await track('session', adminApi.aiGetSession(saved)));
        } catch (err) {
            fail(err);
        }
    }, [adminApi, adopt, fail, say, track]);

    const send = useCallback(async (text) => {
        const content = (text ?? input).trim();
        if (!content) return;
        if (watched) {
            // A generation is running: every message would get 409 GENERATION_IN_PROGRESS, so keep
            // the text and send it by itself once the packages land (see the polling effect).
            queuedRef.current = content;
            setInput('');
            say('system', 'queued — will be sent as soon as the packages are ready');
            return;
        }
        if (!tokenRef.current && !(await newChat())) {
            return;
        }
        setInput('');
        say('USER', content);
        setSending(true);
        setBusy('thinking');
        try {
            const turn = await track('message', adminApi.aiSendMessage(tokenRef.current, content));
            const replies = turn.messages && turn.messages.length ? turn.messages : (turn.message ? [turn.message] : []);
            replies.forEach(m => say(m.role, m.content));
            if (turn.edit) {
                setEdit(turn.edit);
            }
            if (turn.generation) {
                setGeneration(turn.generation);
                if (turn.generation.status === 'READY') {
                    say('system', 'packages edited inline — no regeneration');
                } else {
                    say('system', 'generation started');
                    setWatched(turn.generation);
                }
            }
            await refresh();
        } catch (err) {
            fail(err);
            // A failed turn is free on the server and nothing was answered: hand the text back.
            setInput(current => current || content);
        } finally {
            setSending(false);
            setBusy(current => (current === 'thinking' ? '' : current));
        }
    }, [adminApi, fail, input, newChat, refresh, say, track, watched]);
    sendRef.current = send;

    // Polls a generation in flight. Packages published before their copy (textsPending) are shown as
    // soon as they arrive; the queued message, if any, goes out once the row is READY.
    useEffect(() => {
        if (!watched) return undefined;
        let cancelled = false;
        let timer = null;
        const started = Date.now();
        const tick = async () => {
            try {
                const fresh = await track('generation', adminApi.aiGetGeneration(watched.id));
                if (cancelled) return;
                if (fresh.status === 'QUEUED' || fresh.status === 'RUNNING') {
                    if (fresh.packages && fresh.packages.length) {
                        setGeneration(fresh);
                    }
                    const seconds = Math.round((Date.now() - started) / 1000);
                    setBusy(`${fresh.textsPending ? 'writing the texts' : 'building packages'} ${seconds} s`);
                    timer = setTimeout(tick, pollIntervalMs);
                    return;
                }
                setWatched(null);
                setBusy('');
                setGeneration(fresh);
                const seconds = Math.round((Date.now() - started) / 1000);
                say('system', fresh.status === 'READY'
                    ? `packages ready in ${seconds} s`
                    + (fresh.degraded ? ' — both model drafts were rejected, so Java composed these (degraded)' : '')
                    : `generation failed: ${fresh.error ? fresh.error.code : '?'}`);
                if (fresh.status === 'READY') {
                    draftLines(fresh.diagnostics).forEach((line) => say('system', line));
                }
                await refresh();
                if (queuedRef.current) {
                    const text = queuedRef.current;
                    queuedRef.current = null;
                    say('system', 'sending the message you typed during the build');
                    await sendRef.current(text);
                }
            } catch (err) {
                if (cancelled) return;
                setWatched(null);
                setBusy('');
                fail(err);
            }
        };
        timer = setTimeout(tick, pollIntervalMs);
        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [adminApi, fail, pollIntervalMs, refresh, say, track, watched]);

    const rebuild = async () => {
        setBusy('queueing');
        try {
            say('system', 'manual generation requested');
            const started = await track('generate', adminApi.aiRequestGeneration(tokenRef.current));
            setGeneration(started);
            setWatched(started);
        } catch (err) {
            setBusy('');
            fail(err);
        }
    };

    const select = async (generationId, packageKey) => {
        try {
            const picked = await track('select', adminApi.aiSelectPackage(generationId, packageKey));
            setSelection(picked);
            say('system', `selected ${packageKey}: ${picked.tripItems.length} Trip Builder items for ${picked.groupSize} people`);
            const state = await refresh();
            if (state.latestReadyGeneration) {
                setGeneration(state.latestReadyGeneration);
            }
        } catch (err) {
            fail(err);
        }
    };

    const onKeyDown = (event) => {
        if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            send();
        }
    };

    const idle = !busy && !sending;
    const sendLabel = watched ? 'Queue for after the build' : (sending ? 'Sending…' : 'Send');

    return (
        <>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
                <div className="me-2">
                    <h4 className="fw-bold mb-0">AI planner</h4>
                    <span className="text-muted small">Test bench: the same chat and packages the organizer will get.</span>
                </div>
                <Form.Select size="sm" style={{width: 'auto'}} value={destinationSlug} aria-label="Destination"
                             onChange={(e) => setDestinationSlug(e.target.value)}>
                    {destinations.length === 0 && <option value="prague">prague</option>}
                    {destinations.map(d => <option key={d.slug} value={d.slug}>{d.name} ({d.slug})</option>)}
                </Form.Select>
                <Form.Select size="sm" style={{width: 'auto'}} value={locale} aria-label="Locale"
                             onChange={(e) => setLocale(e.target.value)}>
                    <option value="en">en</option>
                    <option value="de">de</option>
                </Form.Select>
                <Form.Control size="sm" type="number" min={1} max={7} style={{width: 72}} aria-label="Days"
                              placeholder="days" value={preset.days}
                              onChange={(e) => setPreset(p => ({...p, days: e.target.value}))}/>
                <Form.Control size="sm" type="number" min={2} max={30} style={{width: 80}} aria-label="Group size"
                              placeholder="people" value={preset.groupSize}
                              onChange={(e) => setPreset(p => ({...p, groupSize: e.target.value}))}/>
                {['arrival', 'departure'].map(edge => (
                    <Form.Select key={edge} size="sm" style={{width: 'auto'}} aria-label={edge} value={preset[edge]}
                                 onChange={(e) => setPreset(p => ({...p, [edge]: e.target.value}))}>
                        <option value="">{edge}: ask</option>
                        {EDGES.map(v => <option key={v} value={v}>{edge} {v.toLowerCase()}</option>)}
                    </Form.Select>
                ))}
                <Button size="sm" onClick={newChat} disabled={!idle}>New chat</Button>
                <Button size="sm" variant="outline-secondary" onClick={resume} disabled={!idle}
                        title="Reload the last chat kept in this browser">Resume last</Button>
                <span className="text-muted small font-monospace ms-auto">
                    {session ? `${session.destinationSlug} · ${session.locale} · ${session.token}` : 'no chat yet'}
                </span>
            </div>

            <Row className="g-3">
                <Col lg={5}>
                    <Card className="shadow-sm">
                        <Card.Header className="d-flex align-items-center gap-2 small text-uppercase text-muted">
                            Chat {busy && <span className="text-warning text-lowercase">· {busy}</span>}
                        </Card.Header>
                        <div ref={logRef} className="p-3 d-flex flex-column gap-2"
                             style={{height: '52vh', minHeight: 360, overflowY: 'auto'}} data-testid="chat-log">
                            {log.length === 0 && <span className="text-muted small">Start a chat, or just write the first message.</span>}
                            {log.map(entry => <ChatLine key={entry.id} entry={entry}/>)}
                        </div>
                        <Card.Footer className="d-grid gap-2">
                            {(session?.suggestedReplies || []).length > 0 && (
                                <div className="d-flex flex-wrap gap-1" aria-label="Suggested replies">
                                    {session.suggestedReplies.map(text => (
                                        <Button key={text} size="sm" variant="outline-primary" disabled={!idle}
                                                onClick={() => send(text)}>{text}</Button>
                                    ))}
                                </div>
                            )}
                            <div className="d-flex flex-wrap gap-1">
                                {QUICK_PROMPTS.map(text => (
                                    <Button key={text} size="sm" variant="outline-secondary" title={text}
                                            onClick={() => setInput(text)}>
                                        {text.length > 34 ? `${text.slice(0, 32)}…` : text}
                                    </Button>
                                ))}
                            </div>
                            <Form.Control as="textarea" rows={3} value={input} onKeyDown={onKeyDown}
                                          aria-label="Message"
                                          placeholder="Write like the organizer would… (Ctrl+Enter sends; the first message opens a chat)"
                                          onChange={(e) => setInput(e.target.value)}/>
                            <div className="d-flex flex-wrap align-items-center gap-2">
                                <Button size="sm" onClick={() => send()} disabled={sending || (!!busy && !watched)}
                                        title={watched ? 'The planner is building the packages; a message now would get 409. Your text is sent as soon as they are ready.' : ''}>
                                    {sendLabel}
                                </Button>
                                <Button size="sm" variant="outline-secondary" onClick={rebuild}
                                        disabled={!session || !idle} title="POST /ai/sessions/{token}/generations">
                                    Rebuild packages
                                </Button>
                                <span className="text-muted small">{timing}</span>
                            </div>
                        </Card.Footer>
                    </Card>
                </Col>
                <Col lg={7} className="d-grid gap-3 align-content-start">
                    <Card className="shadow-sm">
                        <Card.Header className="small text-uppercase text-muted">Brief &amp; limits</Card.Header>
                        <Card.Body>
                            {session ? <BriefPanel state={session}/> : <span className="text-muted">Start a chat.</span>}
                        </Card.Body>
                    </Card>
                    <Card className="shadow-sm">
                        <Card.Header className="d-flex flex-wrap align-items-center gap-1 small text-uppercase text-muted">
                            Packages {generation && <GenerationMeta generation={generation}/>}
                        </Card.Header>
                        <Card.Body>
                            {generation
                                ? <Packages generation={generation} onSelect={select}/>
                                : <span className="text-muted">Nothing generated yet.</span>}
                        </Card.Body>
                    </Card>
                    <Card className="shadow-sm">
                        <Card.Header className="small text-uppercase text-muted">Last edit report</Card.Header>
                        <Card.Body>
                            {edit ? <EditReport edit={edit}/> : <span className="text-muted">No edit turn yet.</span>}
                        </Card.Body>
                    </Card>
                    <Card className="shadow-sm">
                        <details>
                            <summary className="px-3 py-2 small text-uppercase text-muted">Last selection (Trip Builder items)</summary>
                            <pre className="px-3 pb-3 small mb-0" style={{maxHeight: 300, overflow: 'auto'}}>
                                {selection ? JSON.stringify(selection, null, 2) : '—'}
                            </pre>
                        </details>
                    </Card>
                    <Card className="shadow-sm">
                        <details>
                            <summary className="px-3 py-2 small text-uppercase text-muted">Last raw response</summary>
                            <pre className="px-3 pb-3 small mb-0" style={{maxHeight: 340, overflow: 'auto'}}>
                                {raw ? `${raw.label}\n${JSON.stringify(raw.result, null, 2)}` : '—'}
                            </pre>
                        </details>
                    </Card>
                </Col>
            </Row>
        </>
    );
}

function ChatLine({entry}) {
    if (entry.role === 'system') {
        return <div className="text-muted small text-center">{entry.content}</div>;
    }
    if (entry.role === 'error') {
        return <Alert variant="danger" className="py-1 px-2 small mb-0 align-self-center">{entry.content}</Alert>;
    }
    const mine = entry.role === 'USER';
    // The admin console is dark (tokens.css: --surface/--text), so the assistant bubble is a lighter
    // tint of the surface rather than a Bootstrap light-theme grey, which would swallow the light text.
    const bubble = mine
        ? {background: 'var(--primary)', color: 'var(--primary-text)'}
        : {background: 'rgba(255, 255, 255, 0.08)', color: 'var(--text)', border: '1px solid var(--border)'};
    return (
        <div className={`rounded-3 px-3 py-2 ${mine ? 'align-self-end' : 'align-self-start'}`}
             style={{maxWidth: '88%', whiteSpace: 'pre-wrap', wordBreak: 'break-word', ...bubble}}>
            {entry.content}
        </div>
    );
}

function BriefPanel({state}) {
    const brief = state.brief || {};
    const rows = Object.entries(brief)
        .filter(([, value]) => value != null && !(Array.isArray(value) && value.length === 0));
    return (
        <>
            {rows.length === 0
                ? <div className="text-muted small mb-2">brief: empty</div>
                : (
                    <dl className="row small mb-2">
                        {rows.map(([name, value]) => (
                            <div key={name} className="d-contents">
                                <dt className="col-4 text-muted fw-normal">{name}</dt>
                                <dd className="col-8 mb-1">{Array.isArray(value) ? value.join(', ') : String(value)}</dd>
                            </div>
                        ))}
                    </dl>
                )}
            <div className="d-flex flex-wrap gap-1">
                {state.status && <Badge bg="secondary">session {state.status}</Badge>}
                {(state.missingFields || []).map(field => <Badge key={field} bg="warning" text="dark">missing: {field}</Badge>)}
                {state.readyToGenerate && <Badge bg="success">ready to generate</Badge>}
                {state.limits && (
                    <>
                        <Badge bg="light" text="dark">messages left {state.limits.messagesLeft}</Badge>
                        <Badge bg="light" text="dark">generations left {state.limits.generationsLeft}</Badge>
                        <Badge bg="light" text="dark">edit turns left {state.limits.editsLeft}</Badge>
                    </>
                )}
            </div>
        </>
    );
}

function GenerationMeta({generation}) {
    const statusVariant = generation.status === 'READY' ? 'success' : generation.status === 'FAILED' ? 'danger' : 'warning';
    return (
        <>
            <Badge bg="secondary">{generation.kind || 'GENERATED'}</Badge>
            <Badge bg={statusVariant} text={statusVariant === 'warning' ? 'dark' : undefined}>{generation.status}</Badge>
            {generation.degraded && <Badge bg="warning" text="dark">degraded (fallback)</Badge>}
            {generation.textsPending && <Badge bg="warning" text="dark">texts pending</Badge>}
            {generation.parentId && <Badge bg="light" text="dark">parent {generation.parentId.slice(0, 8)}</Badge>}
            <Badge bg="light" text="dark">id {generation.id.slice(0, 8)}</Badge>
        </>
    );
}

function Packages({generation, onSelect}) {
    const packages = generation.packages || [];
    if (generation.status !== 'READY' && packages.length === 0) {
        return generation.status === 'FAILED'
            ? (
                <span className="text-danger">
                    failed: {generation.error ? generation.error.code : '?'}
                    {generation.error && generation.error.retryable ? ' (retryable — use Rebuild)' : ''}
                </span>
            )
            : <span className="text-warning">building packages…</span>;
    }
    return (
        <>
            {generation.textsPending && (
                <div className="text-muted small mb-2">Structure and prices are final; the copy is still being written.</div>
            )}
            <Row className="g-3">
                {packages.map(pkg => (
                    <Col key={pkg.key} md={6} xl={4}>
                        <PackageCard pkg={pkg} generation={generation} onSelect={onSelect}/>
                    </Col>
                ))}
            </Row>
        </>
    );
}

function PackageCard({pkg, generation, onSelect}) {
    const chosen = generation.selectedPackageKey === pkg.key;
    return (
        <Card className={`h-100 ${chosen ? 'border-primary' : ''}`}>
            <Card.Body className="d-flex flex-column gap-2">
                <div><Badge bg="secondary">{pkg.key}</Badge></div>
                <h5 className="mb-0">{pkg.title}</h5>
                {pkg.tagline && <div className="text-muted fst-italic small">{pkg.tagline}</div>}
                {pkg.description && <div className="small" style={{whiteSpace: 'pre-line'}}>{pkg.description}</div>}
                <div className="fw-bold">{money(pkg.pricePerPerson, pkg.currency)} pp · {money(pkg.totalPrice, pkg.currency)} total</div>
                <div className="text-muted small">
                    {pkg.nights != null ? `${pkg.nights} night${pkg.nights === 1 ? '' : 's'} · ` : ''}
                    {Math.round((pkg.totalDurationMinutes || 0) / 60 * 10) / 10} h of activities
                </div>
                {(pkg.days || []).map(day => (
                    <div key={day.dayNumber} className="border-top pt-2 small">
                        <b>Day {day.dayNumber} — {day.title || ''}</b>
                        {day.summary && <div className="text-muted">{day.summary}</div>}
                        {(day.items || []).map(item => (
                            <div key={`${item.slot}-${item.activityId}`} className="d-flex flex-wrap gap-2 py-1">
                                <span className="text-muted" style={{minWidth: 86}}>{item.slot}{item.startHint ? ` ${item.startHint}` : ''}</span>
                                <span>{item.name} ({item.durationMinutes} min)</span>
                                <span className="ms-auto">{money(item.lineTotal, pkg.currency)}{item.groupMinApplied ? ' *min' : ''}</span>
                                {item.includes && <div className="w-100 text-muted">Includes: {item.includes}</div>}
                                {item.why && <div className="w-100 text-muted">{item.why}</div>}
                            </div>
                        ))}
                    </div>
                ))}
                <Button size="sm" className="mt-auto" variant={chosen ? 'secondary' : 'primary'}
                        disabled={!!generation.textsPending} onClick={() => onSelect(generation.id, pkg.key)}>
                    {chosen ? 'Chosen' : `Choose ${pkg.key}`}
                </Button>
            </Card.Body>
        </Card>
    );
}

function EditReport({edit}) {
    return (
        <div className="small">
            <div className="d-flex flex-wrap gap-1 mb-2">
                <Badge bg={edit.applied.length ? 'success' : 'danger'}>{edit.applied.length} applied</Badge>
                <Badge bg={edit.rejected.length ? 'warning' : 'light'} text="dark">{edit.rejected.length} rejected</Badge>
                <Badge bg="light" text="dark">tierRulesRelaxed {String(edit.tierRulesRelaxed)}</Badge>
                <Badge bg="light" text="dark">textsRefreshed {String(edit.textsRefreshed)}</Badge>
            </div>
            {edit.applied.map((a, index) => (
                <div key={`a${index}`}>
                    ✔ {a.op} {a.activity}{a.replacement ? ` → ${a.replacement}` : ''} · {a.packageKey} · day {a.dayNumber}{a.slot ? ` ${a.slot}` : ''}
                </div>
            ))}
            {edit.rejected.map((r, index) => (
                <div key={`r${index}`}>
                    ✘ {r.op || '?'} {r.activity || '?'} · {r.packageKey || 'any'} · {r.reason}{r.detail ? ` — ${r.detail}` : ''}
                    {r.alternatives && r.alternatives.length ? ` · closest: ${r.alternatives.join(', ')}` : ''}
                </div>
            ))}
        </div>
    );
}

export default AdminAiPlanner;
