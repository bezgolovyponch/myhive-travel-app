import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import AiPlannerPage from './AiPlannerPage';
import aiPlannerApi from '../services/aiPlannerApi';
import {TripContext} from '../context/TripContext';
import {CatalogContext} from '../context/CatalogContext';
import {SESSION_STORAGE_KEY} from '../hooks/useAiPlanner';

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
    totalPrice: pricePerPerson * 10, currency: 'EUR',
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

function renderPage() {
    return render(
        <HelmetProvider>
            <CatalogContext.Provider value={{state: catalogState, dispatch: jest.fn()}}>
                <TripContext.Provider value={{state: {tripItems: []}, dispatch: tripDispatch}}>
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

test('once packages land they fill the page and the chat retracts into the dock', async () => {
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
    expect(await screen.findByRole('heading', {name: 'Prague · Medium'})).toBeInTheDocument();
    expect(screen.getByRole('tab', {name: /Medium/})).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByText('Karting')).toBeInTheDocument();
    expect(screen.getByText('Afternoon')).toBeInTheDocument(); // no startHint: the slot name
    // No prices anywhere: the Prague planner quotes on the call.
    expect(screen.queryByText(/€/)).not.toBeInTheDocument();
    expect(screen.getByText('10 people · 3 days · 2 activities')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', {name: /Premium/}));
    expect(screen.getByRole('heading', {name: 'Prague · Premium'})).toBeInTheDocument();
    expect(screen.queryByText('Karting')).not.toBeInTheDocument();

    // Collapsed pill by default; it opens the drawer with the same transcript.
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', {name: /Ask Stag Do AI to change anything/}));
    const drawer = screen.getByRole('dialog', {name: 'Stag Do AI'});
    expect(within(drawer).getByText('Building your three options…')).toBeInTheDocument();
    // The trims are cards under the reply too; tapping one switches the package behind.
    await userEvent.click(within(drawer).getByRole('button', {name: /Basic.*Essentials/}));
    expect(screen.getByRole('heading', {name: 'Prague · Basic'})).toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole('button', {name: 'Collapse chat'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
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

    await userEvent.click(await screen.findByRole('button', {name: /Ask Stag Do AI to change anything/}));
    await userEvent.type(screen.getByRole('textbox', {name: 'Message Stag Do AI'}), 'swap karting for a cruise{Enter}');

    // Both assistant lines of an edit turn are shown, not only `message`.
    expect(await screen.findByText('Swapped Karting for River Cruise in the Medium package.')).toBeInTheDocument();
    expect(screen.getByText('Swapping it now.')).toBeInTheDocument();
    const row = screen.getByText('River Cruise').closest('.aip-item');
    expect(within(row).getByText('AI added')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Swapped Karting for River Cruise');
});

test('× asks the planner to drop the activity, and Undo goes back to the generation before', async () => {
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
    aiPlannerApi.sendMessage.mockResolvedValue({
        messages: [{role: 'ASSISTANT', content: 'Dropped Karting from the Medium package.', at}],
        suggestedReplies: [], generation: edited, edit: edited.editReport,
    });
    aiPlannerApi.getGeneration.mockResolvedValue(readyGeneration());
    aiPlannerApi.selectPackage.mockResolvedValue({packageKey: 'MEDIUM', groupSize: 10, tripItems: []});
    renderPage();

    await userEvent.click(await screen.findByRole('button', {name: 'Remove Karting'}));

    expect(aiPlannerApi.sendMessage).toHaveBeenCalledWith('tok-1', 'Remove Karting from the Medium package');
    expect(await screen.findByRole('status')).toHaveTextContent('Removed Karting');
    expect(screen.queryByText('Karting')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

    expect(aiPlannerApi.getGeneration).toHaveBeenCalledWith('gen-1');
    expect(aiPlannerApi.selectPackage).toHaveBeenCalledWith('gen-1', 'MEDIUM');
    expect(await screen.findByText('Karting')).toBeInTheDocument();
});

test('"Send to a Prague planner" picks the trim, fills the cart and opens the contact step', async () => {
    window.localStorage.setItem(SESSION_STORAGE_KEY, 'tok-1');
    aiPlannerApi.getSession.mockResolvedValue(session({latestReadyGeneration: readyGeneration(), status: 'READY'}));
    aiPlannerApi.selectPackage.mockResolvedValue({
        packageKey: 'MEDIUM', groupSize: 10,
        tripItems: [{activityId: 'id-Karting', name: 'Karting', price: 45}],
    });
    renderPage();

    await userEvent.click(await screen.findByRole('button', {name: 'Send to a Prague planner'}));

    expect(aiPlannerApi.selectPackage).toHaveBeenCalledWith('gen-1', 'MEDIUM');
    expect(tripDispatch).toHaveBeenCalledWith({
        type: 'SET_TRIP_ITEMS',
        tripItems: [{activityId: 'id-Karting', id: 'id-Karting', name: 'Karting', price: 45}],
    });
    expect(tripDispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_TRAVELERS', travelers: 10});
    // The existing vote modal is the contact step: it creates the session and opens the dashboard.
    expect(await screen.findByText('Start the vote for Prague')).toBeInTheDocument();
    expect(screen.getByText('Send the results to')).toBeInTheDocument();
});
