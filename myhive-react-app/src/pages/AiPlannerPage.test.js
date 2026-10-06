import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import AiPlannerPage from './AiPlannerPage';
import aiPlannerApi from '../services/aiPlannerApi';
import {TripContext} from '../context/TripContext';
import {CatalogContext} from '../context/CatalogContext';
import {SESSION_STORAGE_KEY} from '../hooks/useAiPlanner';
import {PLANNER_DRAFT_KEY} from '../components/home/HeroPlanner';

jest.mock('../services/aiPlannerApi');
jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}));

const at = '2026-09-30T10:00:00Z';
const greeting = {role: 'ASSISTANT', content: 'Hey! Who is coming?', at};

const session = (over = {}) => ({
    token: 'tok-1',
    destinationSlug: 'prague',
    status: 'COLLECTING',
    messages: [greeting],
    latestGeneration: null,
    latestReadyGeneration: null,
    firstTurnError: null,
    suggestedReplies: [],
    ...over,
});

const item = (name, over = {}) => ({
    slot: 'EVENING', startHint: '20:00', activityId: `id-${name}`, slug: name.toLowerCase(), name,
    imageUrl: null, durationMinutes: 120, price: 45, minPrice: 0, lineTotal: 450,
    groupMinApplied: false, why: `Why ${name}`, includes: null, ...over,
});

const pkg = (key, pricePerPerson, items) => ({
    key, title: `${key} weekend`, tagline: `${key} tagline`, pricePerPerson,
    // fromPrice is the backend's "from" (the total less its margin); the page shows it as is.
    totalPrice: pricePerPerson * 10, fromPrice: pricePerPerson * 9, currency: 'EUR',
    days: [{dayNumber: 1, title: 'Landing night', summary: null, items}],
});

const readyGeneration = (over = {}) => ({
    id: 'gen-1', status: 'READY', textsPending: false, degraded: false, kind: 'GENERATED', parentId: null,
    editReport: null, brief: {groupSize: 10, days: 3},
    packages: [
        pkg('BASIC', 125, [item('Bar Crawl')]),
        pkg('MEDIUM', 195, [item('Bar Crawl'), item('Karting', {slot: 'AFTERNOON', startHint: null})]),
        pkg('PREMIUM', 310, [item('VIP Club')]),
    ],
    ...over,
});

const tripDispatch = jest.fn();
const catalogState = {destinations: [{id: 'd1', slug: 'prague', name: 'Prague'}], loading: false, error: null};

function renderPage(trip = {}) {
    return render(
        <HelmetProvider>
            <CatalogContext.Provider value={{state: catalogState, dispatch: jest.fn()}}>
                <TripContext.Provider value={{state: {tripItems: [], ...trip}, dispatch: tripDispatch}}>
                    <MemoryRouter initialEntries={['/plan']}>
                        <Routes>
                            <Route path="/plan" element={<AiPlannerPage pollIntervalMs={5}/>}/>
                        </Routes>
                    </MemoryRouter>
                </TripContext.Provider>
            </CatalogContext.Provider>
        </HelmetProvider>
    );
}

beforeEach(() => {
    jest.clearAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
});

test('a brief handed over from the homepage opens a fresh chat with days and head-count preset', async () => {
    // An older chat is in storage: the homepage brief replaces it rather than landing in it.
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-old');
    aiPlannerApi.getSession.mockResolvedValue(session({token: 'tok-old'}));
    window.sessionStorage.setItem(PLANNER_DRAFT_KEY,
        JSON.stringify({message: 'Karting and a beer spa', days: 3, groupSize: 10}));
    aiPlannerApi.createSession.mockResolvedValue(session({
        messages: [greeting, {role: 'USER', content: 'Karting and a beer spa', at},
            {role: 'ASSISTANT', content: 'When do you land on Friday?', at}],
    }));
    renderPage();

    expect(await screen.findByText('When do you land on Friday?')).toBeInTheDocument();
    expect(aiPlannerApi.createSession).toHaveBeenCalledTimes(1);
    expect(aiPlannerApi.createSession).toHaveBeenCalledWith('prague', 'en',
        {initialMessage: 'Karting and a beer spa', days: 3, groupSize: 10});
    expect(aiPlannerApi.sendMessage).not.toHaveBeenCalled();
    // Used once: a reload does not send it again.
    expect(window.sessionStorage.getItem(PLANNER_DRAFT_KEY)).toBeNull();
});

test('with trip dates that match the plan, days are real dates', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    const gen = readyGeneration();
    gen.packages = gen.packages.map((p) => ({...p, days: [
        {dayNumber: 1, title: 'Landing night', summary: null, items: p.days[0].items},
        {dayNumber: 2, title: 'Big day', summary: null, items: []},
        {dayNumber: 3, title: 'Recovery', summary: null, items: []},
    ]}));
    aiPlannerApi.getSession.mockResolvedValue(session({status: 'READY', latestReadyGeneration: gen}));
    renderPage({tripStartDate: '2026-10-16', tripEndDate: '2026-10-18'});

    expect(await screen.findByText('2 h · Fri 16 Oct · 20:00')).toBeInTheDocument();
    expect(screen.getByText('2 h · Fri 16 Oct · Afternoon')).toBeInTheDocument();
    expect(screen.queryByText(/Day 1/)).not.toBeInTheDocument();
    expect(screen.getByText('Fri 16 – Sun 18 Oct · 10 people')).toBeInTheDocument();
});

test('trip dates that do not match the plan length keep Day 1, Day 2', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({status: 'READY', latestReadyGeneration: readyGeneration()}));
    renderPage({tripStartDate: '2026-10-16', tripEndDate: '2026-10-22'});

    expect(await screen.findByText('2 h · Day 1 · 20:00')).toBeInTheDocument();
    expect(screen.getByText('3 days · 10 people')).toBeInTheDocument();
});

test('first visit: the chat is the page, a starter opens a session with it as the first message', async () => {
    aiPlannerApi.createSession.mockResolvedValue(session({
        messages: [greeting, {role: 'USER', content: '10 of us, 3 days in Prague, karting and beer', at},
            {role: 'ASSISTANT', content: 'Nice. When do you land?', at}],
        suggestedReplies: ['Friday evening', 'Saturday morning'],
    }));
    renderPage();

    expect(screen.getByRole('heading', {name: 'Stag Do AI'})).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', {name: '10 of us, 3 days in Prague, karting and beer'}));

    expect(aiPlannerApi.createSession).toHaveBeenCalledWith('prague', 'en',
        {initialMessage: '10 of us, 3 days in Prague, karting and beer'});
    expect(await screen.findByText('Nice. When do you land?')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Friday evening'})).toBeInTheDocument();
    expect(window.localStorage.getItem(SESSION_STORAGE_KEY)).toBe('tok-1');
});

test('a returning visitor gets the chat back and a typed message goes to the same session', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session());
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Got it, 8 people.', at}],
        suggestedReplies: [], generation: null, edit: null,
    });
    renderPage();

    expect(await screen.findByText('Hey! Who is coming?')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), '8 of us{Enter}');

    expect(aiPlannerApi.sendMessage).toHaveBeenCalledWith('tok-1', '8 of us');
    expect(await screen.findByText('Got it, 8 people.')).toBeInTheDocument();
    expect(screen.getByText('8 of us')).toBeInTheDocument();
});

test('a pending reply shows one typing indicator, not an extra empty bubble', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session());
    let answer;
    aiPlannerApi.sendMessage.mockReturnValue(new Promise((resolve) => { answer = resolve; }));
    const {container} = renderPage();

    await userEvent.type(await screen.findByRole('textbox', {name: 'Message Stag Do AI'}), 'hi{Enter}');

    expect(await screen.findByText('Stag Do AI is typing')).toBeInTheDocument();
    expect(container.querySelectorAll('.ai-typing')).toHaveLength(1);
    // greeting + the pending placeholder, nothing else from the assistant
    expect(container.querySelectorAll('.ai-msg-assistant')).toHaveLength(2);

    answer({messages: [{role: 'ASSISTANT', content: 'Hello!', at}], suggestedReplies: [], generation: null});
    expect(await screen.findByText('Hello!')).toBeInTheDocument();
    expect(container.querySelectorAll('.ai-typing')).toHaveLength(0);
});

test('an expired token is dropped and the visitor starts fresh', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'dead');
    const err = Object.assign(new Error('gone'), {status: 404, body: {error: 'SESSION_NOT_FOUND'}});
    aiPlannerApi.getSession.mockRejectedValue(err);
    renderPage();

    expect(await screen.findByRole('button', {name: '8 guys, Friday to Sunday, something wild'})).toBeInTheDocument();
    expect(window.localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
});

test('a failed first reply keeps the message and "Try again" re-sends the same text', async () => {
    aiPlannerApi.createSession.mockResolvedValue(session({
        messages: [greeting, {role: 'USER', content: 'hi there', at}],
        firstTurnError: {code: 'LLM_TIMEOUT'},
    }));
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Sorry, I am back.', at}], suggestedReplies: [], generation: null,
    });
    renderPage();

    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), 'hi there{Enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('The assistant took too long');
    await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

    expect(aiPlannerApi.sendMessage).toHaveBeenCalledWith('tok-1', 'hi there');
    expect(await screen.findByText('Sorry, I am back.')).toBeInTheDocument();
    expect(screen.getAllByText('hi there')).toHaveLength(1);
});

test('once packages land the trip draft fills the page and the chat docks collapsed under it', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session());
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Building your three options…', at}],
        suggestedReplies: [], generation: {id: 'gen-1', status: 'QUEUED'},
    });
    aiPlannerApi.getGeneration
        .mockResolvedValueOnce({id: 'gen-1', status: 'RUNNING'})
        .mockResolvedValue(readyGeneration());
    renderPage();

    await userEvent.type(await screen.findByRole('textbox', {name: 'Message Stag Do AI'}),
        'Friday evening to Sunday{Enter}');

    // MEDIUM is the AI pick and opens first.
    expect(await screen.findByRole('heading', {name: 'Prague stag'})).toBeInTheDocument();
    expect(screen.getByRole('tab', {name: /Medium/})).toHaveAttribute('aria-selected', 'true');
    const draft = screen.getByRole('region', {name: 'Trip draft'});
    expect(within(draft).getByText('Karting')).toBeInTheDocument();
    expect(within(draft).getByText('2 h · Day 1 · Afternoon')).toBeInTheDocument(); // no startHint: the slot name
    expect(within(draft).getByText('2 activities')).toBeInTheDocument();
    // One price for the whole group at the foot of the draft, none per activity.
    expect(within(draft).getByText('from €1,755')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /Browse all/}))
        .toHaveAttribute('href', '/destination/prague?tab=activities');
    // Each trim shows its starting group total — the same number the cart shows
    // once it is picked; the planner confirms the final price on the call.
    expect(within(screen.getByRole('tab', {name: /Basic/})).getByText('from €1,125')).toBeInTheDocument();
    expect(within(screen.getByRole('tab', {name: /Medium/})).getByText('from €1,755')).toBeInTheDocument();
    expect(within(screen.getByRole('tab', {name: /Premium/})).getByText('from €2,790')).toBeInTheDocument();
    expect(screen.getByText('3 days · 10 people')).toBeInTheDocument();

    // Picking a trim makes it the draft: the other trims leave the screen.
    await userEvent.click(screen.getByRole('tab', {name: /Premium/}));
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText('Karting')).not.toBeInTheDocument();
    expect(within(draft).getByText('VIP Club')).toBeInTheDocument();
    expect(within(draft).getByText('from €2,790')).toBeInTheDocument();

    // Docked and collapsed by default: its top bar opens it on the same transcript and closes it again.
    const dock = screen.getByRole('region', {name: 'Stag Do AI'});
    const bar = within(dock).getByRole('button', {name: 'Open chat'});
    expect(bar).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(bar);
    expect(within(dock).getByRole('button', {name: 'Collapse chat'})).toHaveAttribute('aria-expanded', 'true');
    expect(within(dock).getByText('Building your three options…')).toBeInTheDocument();
    // The hand-off sits under the draft, reachable whether the chat is open or not.
    expect(screen.getByRole('button', {name: 'Ask the group'})).toBeInTheDocument();
    await userEvent.click(within(dock).getByRole('button', {name: 'Collapse chat'}));
    expect(within(dock).getByRole('button', {name: 'Open chat'})).toHaveAttribute('aria-expanded', 'false');
});

test('an applied edit updates the packages in place and marks what the AI added', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    const edited = readyGeneration({
        id: 'gen-2', kind: 'EDITED', parentId: 'gen-1',
        packages: [
            pkg('BASIC', 125, [item('Bar Crawl')]),
            pkg('MEDIUM', 195, [item('Bar Crawl'), item('River Cruise', {slot: 'AFTERNOON'})]),
            pkg('PREMIUM', 310, [item('VIP Club')]),
        ],
        editReport: {applied: [{op: 'REPLACE', activity: 'Karting', replacement: 'River Cruise', packageKey: 'MEDIUM'}], rejected: []},
    });
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Swapping it now.', at},
            {role: 'ASSISTANT', content: 'Swapped Karting for River Cruise in the Medium package.', at}],
        suggestedReplies: [], generation: edited, edit: edited.editReport,
    });
    renderPage();

    // Until the organizer asks for a change the three trims are on offer.
    expect(await screen.findByRole('tablist', {name: 'Package trims'})).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', {name: 'Open chat'}));
    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), 'swap karting for a cruise{Enter}');

    // From the first message on it is their own draft: no trims any more.
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.getByRole('region', {name: 'Trip draft'})).toBeInTheDocument();

    // Both assistant lines of an edit turn are shown, not only `message`.
    expect(await screen.findByText('Swapped Karting for River Cruise in the Medium package.')).toBeInTheDocument();
    expect(screen.getByText('Swapping it now.')).toBeInTheDocument();
    const row = screen.getByText('River Cruise').closest('.aip-item');
    expect(within(row).getByText('AI added')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Swapped Karting for River Cruise');
});

test('× drops the activity from the draft without a chat turn, and Undo goes back to the generation before', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    const edited = readyGeneration({
        id: 'gen-2', kind: 'EDITED', parentId: 'gen-1',
        packages: [
            pkg('BASIC', 125, [item('Bar Crawl')]),
            pkg('MEDIUM', 195, [item('Bar Crawl')]),
            pkg('PREMIUM', 310, [item('VIP Club')]),
        ],
        editReport: {applied: [{op: 'REMOVE', activity: 'Karting', replacement: null, packageKey: 'MEDIUM'}], rejected: []},
    });
    aiPlannerApi.editDraft.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Dropped Karting from the Medium package.', at}],
        suggestedReplies: [], recommendations: [], generation: edited, edit: edited.editReport,
    });
    aiPlannerApi.getGeneration.mockResolvedValue(readyGeneration());
    aiPlannerApi.selectPackage.mockResolvedValue({packageKey: 'MEDIUM', groupSize: 10, tripItems: []});
    renderPage();

    await userEvent.click(await screen.findByRole('button', {name: 'Remove Karting'}));

    expect(aiPlannerApi.editDraft).toHaveBeenCalledWith('tok-1', {op: 'REMOVE', activityId: 'id-Karting', packageKey: 'MEDIUM'});
    expect(aiPlannerApi.sendMessage).not.toHaveBeenCalled();
    expect(await screen.findByRole('status')).toHaveTextContent('Removed Karting');
    expect(screen.queryByText('Karting')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

    expect(aiPlannerApi.getGeneration).toHaveBeenCalledWith('gen-1');
    expect(aiPlannerApi.selectPackage).toHaveBeenCalledWith('gen-1', 'MEDIUM');
    expect(await screen.findByText('Karting')).toBeInTheDocument();
});

test('"Ask the group" picks the trim, fills the cart and opens the contact step', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    aiPlannerApi.selectPackage.mockResolvedValue({
        packageKey: 'MEDIUM', groupSize: 10,
        tripItems: [{activityId: 'id-Karting', name: 'Karting', price: 45}],
    });
    renderPage();

    await userEvent.click(await screen.findByRole('button', {name: 'Ask the group'}));

    expect(aiPlannerApi.selectPackage).toHaveBeenCalledWith('gen-1', 'MEDIUM');
    expect(tripDispatch).toHaveBeenCalledWith({
        type: 'SET_TRIP_ITEMS',
        tripItems: [{activityId: 'id-Karting', id: 'id-Karting', name: 'Karting', price: 45}],
    });
    expect(tripDispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_TRAVELERS', travelers: 10});
    // The existing vote modal is the contact step: it creates the session and opens the dashboard.
    expect(await screen.findByRole('heading', {name: /Your group votes\. You get the result\./})).toBeInTheDocument();
    expect(screen.getByLabelText('Your WhatsApp number')).toBeInTheDocument();
});

const rec = (name, over = {}) => ({
    activityId: `id-${name}`, name, oneLine: null, durationMinutes: 90, pricePerPerson: 89, imageUrl: null, ...over,
});

test('"we want to shoot": the top match and related tags sit above the open chat, one tap adds one', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'The top match is above. Add it, or try a mixed range.', at}],
        suggestedReplies: [], generation: null, edit: null,
        recommendations: [rec('AK-47 shooting'), rec('Pistol + AK combo', {pricePerPerson: 119}), rec('Paintball')],
    });
    const withAk = readyGeneration({
        id: 'gen-2', kind: 'EDITED', parentId: 'gen-1',
        packages: [
            pkg('BASIC', 125, [item('Bar Crawl')]),
            pkg('MEDIUM', 195, [item('Bar Crawl'), item('Karting'), item('AK-47 shooting')]),
            pkg('PREMIUM', 310, [item('VIP Club')]),
        ],
        editReport: {applied: [{op: 'ADD', activity: 'AK-47 shooting', packageKey: 'MEDIUM'}], rejected: []},
    });
    aiPlannerApi.editDraft.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Added AK-47 shooting to the Medium package.', at}],
        suggestedReplies: [], generation: withAk, edit: withAk.editReport,
        recommendations: [rec('AK-47 shooting'), rec('Pistol + AK combo', {pricePerPerson: 119}), rec('Paintball')],
    });
    renderPage();

    await userEvent.type(await screen.findByRole('textbox', {name: 'Message Stag Do AI'}),
        'We want to shoot kalashnikov{Enter}');
    // The trim on screen goes with the message: it is the one draft the turn may change.
    expect(aiPlannerApi.sendMessage).toHaveBeenCalledWith('tok-1', 'We want to shoot kalashnikov', 'MEDIUM');

    const offered = await screen.findByRole('group', {name: 'Suggested activities'});
    expect(within(offered).getByText('AK-47 shooting')).toBeInTheDocument();
    // No price in the draft's chat: duration only (the draft shows one "from" total).
    expect(within(offered).getByText('1 h 30 min')).toBeInTheDocument();
    expect(within(offered).getByRole('button', {name: 'Add Pistol + AK combo to the trip draft'}))
        .toHaveTextContent('+ Pistol + AK combo');
    // A wish is not an edit: the draft is unchanged until a tap.
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();

    await userEvent.click(within(offered).getByRole('button', {name: 'Add AK-47 shooting to the trip draft'}));

    expect(aiPlannerApi.editDraft).toHaveBeenCalledWith('tok-1',
        {op: 'ADD', activityId: 'id-AK-47 shooting', packageKey: 'MEDIUM'});
    expect(aiPlannerApi.sendMessage).toHaveBeenCalledTimes(1);
    // In the draft now; the card turns into the way back out.
    const draft = screen.getByRole('region', {name: 'Trip draft'});
    expect(await within(draft).findByText('AK-47 shooting')).toBeInTheDocument();
    const added = within(offered).getByRole('button', {name: 'Remove AK-47 shooting'});
    expect(added).toHaveTextContent('Added ✓');

    await userEvent.click(added);
    expect(aiPlannerApi.editDraft).toHaveBeenLastCalledWith('tok-1',
        {op: 'REMOVE', activityId: 'id-AK-47 shooting', packageKey: 'MEDIUM'});
});

test('the chat brings the trims back ("what were the other options?") and switches to one ("show me Premium")', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    aiPlannerApi.sendMessage
        .mockResolvedValueOnce({messages: [{role: 'ASSISTANT', content: 'Here they are.', at}],
            suggestedReplies: [], generation: null, showPackage: 'ALL'})
        .mockResolvedValueOnce({messages: [{role: 'ASSISTANT', content: 'Premium it is.', at}],
            suggestedReplies: [], generation: null, showPackage: 'PREMIUM'});
    renderPage();

    await userEvent.click(await screen.findByRole('tab', {name: /Medium/}));
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), 'what were the other options?{Enter}');
    expect(await screen.findByRole('tablist', {name: 'Package trims'})).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), 'show me premium{Enter}');
    const draft = await screen.findByRole('region', {name: 'Trip draft'});
    expect(await within(draft).findByText('VIP Club')).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
});

test('under a ready draft the chat asks what to add and offers what fits: an activity goes in with one tap, a theme asks the chat', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({
        latestReadyGeneration: readyGeneration(), status: 'READY',
        messages: [greeting, {role: 'ASSISTANT', content: 'On it - building your three options now.', at}],
        gaps: {MEDIUM: [{categorySlug: 'nightlife', name: 'Nightlife'}, {categorySlug: 'czech-beer', name: 'Czech Beer'}],
            PREMIUM: [{categorySlug: 'extreme', name: 'Extreme'}]},
        // Karting is in the Medium draft already: never offered again.
        suggestions: {
            MEDIUM: [{activityId: 'id-Paintball', name: 'Paintball', durationMinutes: 120},
                {activityId: 'id-Karting', name: 'Karting', durationMinutes: 45}],
            PREMIUM: [{activityId: 'id-Tank', name: 'Army Tank', durationMinutes: 60}],
        },
    }));
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'The top match is above.', at}], suggestedReplies: [],
        generation: null, recommendations: [],
    });
    aiPlannerApi.editDraft.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Added Paintball to the Medium package.', at}], suggestedReplies: [],
        generation: null, recommendations: [], suggestions: {MEDIUM: []},
    });
    renderPage();

    const dock = await screen.findByRole('region', {name: 'Stag Do AI'});
    // The question, not the stale last line of the chat.
    expect(within(dock).getByText('Would you like to add anything?')).toBeInTheDocument();
    const tags = within(dock).getByRole('group', {name: 'What the draft could use'});
    expect(within(tags).getByRole('button', {name: 'Add Paintball to the trip draft'})).toHaveTextContent('+ Paintball');
    expect(within(tags).queryByText(/Karting/)).not.toBeInTheDocument();
    expect(within(tags).queryByText(/Army Tank|Extreme/)).not.toBeInTheDocument(); // another trim's
    expect(within(tags).getByRole('button', {name: /Nightlife/})).toBeInTheDocument();

    await userEvent.click(within(tags).getByRole('button', {name: 'Add Paintball to the trip draft'}));
    expect(aiPlannerApi.editDraft).toHaveBeenCalledWith('tok-1',
        {op: 'ADD', activityId: 'id-Paintball', packageKey: 'MEDIUM'});

    await userEvent.click(await within(dock).findByRole('button', {name: /Czech Beer/}));
    expect(aiPlannerApi.sendMessage).toHaveBeenCalledWith('tok-1', 'What do you have for Czech Beer?', 'MEDIUM');
});
