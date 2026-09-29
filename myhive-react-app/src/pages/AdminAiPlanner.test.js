import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminAiPlanner from './AdminAiPlanner';

const mockApi = {
    aiDestinations: jest.fn(),
    aiCreateSession: jest.fn(),
    aiGetSession: jest.fn(),
    aiSendMessage: jest.fn(),
    aiRequestGeneration: jest.fn(),
    aiGetGeneration: jest.fn(),
    aiSelectPackage: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
// Stable reference: the page keeps it in useCallback dependencies.
jest.mock('../hooks/useAuthErrorHandler', () => {
    const stable = () => false;
    return {useAuthErrorHandler: () => stable};
});

const TOKEN = '8624fc4c-dbce-4012-b70b-7e99eeef5902';

function sessionState(overrides = {}) {
    return {
        token: TOKEN,
        destinationSlug: 'prague',
        locale: 'en',
        status: 'COLLECTING',
        messages: [{role: 'ASSISTANT', content: 'Hey! How many days are you coming for?'}],
        brief: {days: null, groupSize: null, categorySlugs: []},
        missingFields: ['days', 'groupSize'],
        readyToGenerate: false,
        limits: {messagesLeft: 30, generationsLeft: 5, editsLeft: 20},
        latestGeneration: null,
        latestReadyGeneration: null,
        ...overrides,
    };
}

function readyGeneration(overrides = {}) {
    return {
        id: 'f181762b-776c-4574-b7a1-0d4d9381755b',
        status: 'READY',
        kind: 'GENERATED',
        degraded: false,
        textsPending: false,
        selectedPackageKey: null,
        packages: [{
            key: 'BASIC',
            title: 'Beer, Bikes and Bad Decisions',
            tagline: 'Two loud nights',
            description: 'You wanted beer.',
            pricePerPerson: 200,
            totalPrice: 1600,
            currency: 'EUR',
            totalDurationMinutes: 360,
            days: [{
                dayNumber: 1,
                title: 'Landing day',
                summary: 'Easy start',
                items: [{
                    slot: 'EVENING', startHint: null, activityId: 'a1', name: 'Prague Pub Crawl',
                    durationMinutes: 180, lineTotal: 320, groupMinApplied: false, why: 'Because you asked for beer',
                }],
            }],
        }],
        ...overrides,
    };
}

beforeEach(() => {
    localStorage.clear();
    mockApi.aiDestinations.mockResolvedValue([{name: 'Prague', slug: 'prague'}]);
    mockApi.aiCreateSession.mockResolvedValue(sessionState());
    mockApi.aiGetSession.mockResolvedValue(sessionState());
});

test('New chat opens a session and shows the greeting, the missing fields and the limits', async () => {
    const user = userEvent.setup();
    render(<AdminAiPlanner/>);

    await user.click(screen.getByRole('button', {name: 'New chat'}));

    expect(await screen.findByText('Hey! How many days are you coming for?')).toBeInTheDocument();
    // The pickers stand in for the organizer's entry screen: days, group and the day edges travel with the create.
    expect(mockApi.aiCreateSession).toHaveBeenCalledWith('prague', 'en', {days: 3, groupSize: 8, arrival: 'EVENING', departure: 'MORNING'});
    expect(screen.getByText('missing: days')).toBeInTheDocument();
    expect(screen.getByText('messages left 30')).toBeInTheDocument();
    expect(localStorage.getItem('trivlu-admin-ai-planner-session')).toBe(TOKEN);
});

test('the first message opens the chat by itself, then shows the replies, the edit report and the packages', async () => {
    const user = userEvent.setup();
    mockApi.aiSendMessage.mockResolvedValue({
        message: {role: 'ASSISTANT', content: 'Added Nightclub VIP Experience to the Premium package.'},
        messages: [
            {role: 'ASSISTANT', content: "I'm doing it now."},
            {role: 'ASSISTANT', content: 'I could not find "strip shows" in the catalog. Closest to "strip shows": Nightclub VIP Experience - want one of those?'},
        ],
        edit: {
            generationId: null,
            applied: [{op: 'REMOVE', activity: 'Hot Air Balloon Ride', replacement: null, packageKey: 'PREMIUM', dayNumber: 1, slot: 'EVENING'}],
            rejected: [{op: 'ADD', activity: 'strip shows', packageKey: null, reason: 'UNKNOWN_ACTIVITY', detail: 'strip shows', alternatives: ['Nightclub VIP Experience']}],
            tierRulesRelaxed: true,
            textsRefreshed: true,
        },
        generation: readyGeneration({kind: 'EDITED'}),
    });
    render(<AdminAiPlanner/>);

    await user.type(screen.getByLabelText('Message'), 'Remove hot air baloon and add some strip shows');
    await user.click(screen.getByRole('button', {name: 'Send'}));

    expect(await screen.findByText("I'm doing it now.")).toBeInTheDocument();
    expect(mockApi.aiCreateSession).toHaveBeenCalledTimes(1);
    expect(mockApi.aiSendMessage).toHaveBeenCalledWith(TOKEN, 'Remove hot air baloon and add some strip shows');
    expect(screen.getByText(/closest: Nightclub VIP Experience/)).toBeInTheDocument();
    expect(screen.getByText('1 applied')).toBeInTheDocument();
    expect(screen.getByText('Beer, Bikes and Bad Decisions')).toBeInTheDocument();
    expect(screen.getByText('200.00 EUR pp · 1600.00 EUR total')).toBeInTheDocument();
    expect(screen.getByText('packages edited inline — no regeneration')).toBeInTheDocument();
});

test('a turn that starts a generation is polled: the skeleton shows with texts pending, then the finished packages', async () => {
    const user = userEvent.setup();
    const queued = {id: readyGeneration().id, status: 'QUEUED', kind: 'GENERATED', degraded: false, textsPending: false, packages: null};
    const skeleton = readyGeneration({
        status: 'RUNNING',
        textsPending: true,
        packages: [{...readyGeneration().packages[0], title: 'Warm-up', tagline: null, description: null}],
    });
    mockApi.aiSendMessage.mockResolvedValue({
        message: {role: 'ASSISTANT', content: 'Perfect, building three options now.'},
        messages: [{role: 'ASSISTANT', content: 'Perfect, building three options now.'}],
        generation: queued,
    });
    mockApi.aiGetGeneration
        .mockResolvedValueOnce(skeleton)
        .mockResolvedValue(readyGeneration());
    render(<AdminAiPlanner pollIntervalMs={1}/>);

    await user.type(screen.getByLabelText('Message'), '3 days, 8 guys, beer and karting, landing Friday evening.');
    await user.click(screen.getByRole('button', {name: 'Send'}));

    expect(await screen.findByText('generation started')).toBeInTheDocument();
    expect(await screen.findByText('Beer, Bikes and Bad Decisions')).toBeInTheDocument();
    expect(await screen.findByText(/packages ready in/)).toBeInTheDocument();
    await waitFor(() => expect(mockApi.aiGetGeneration).toHaveBeenCalledTimes(2));
    // The skeleton went through the same card while the copy was being written.
    expect(mockApi.aiGetGeneration).toHaveBeenCalledWith(queued.id);
    expect(screen.queryByText('texts pending')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Choose BASIC'})).toBeEnabled();
});

test('a degraded generation says why each draft was rejected: a failed call by its error, a broken rule by its violations', async () => {
    const user = userEvent.setup();
    const queued = {id: readyGeneration().id, status: 'QUEUED', kind: 'GENERATED', degraded: false, textsPending: false, packages: null};
    mockApi.aiSendMessage.mockResolvedValue({
        message: {role: 'ASSISTANT', content: 'Perfect, building three options now.'},
        messages: [{role: 'ASSISTANT', content: 'Perfect, building three options now.'}],
        generation: queued,
    });
    mockApi.aiGetGeneration.mockResolvedValue(readyGeneration({
        degraded: true,
        diagnostics: [
            {attempt: 0, errorCode: 'LLM_INVALID_OUTPUT', violations: ['MISSING_TIER BASIC: package BASIC is missing']},
            {attempt: 1, errorCode: null, violations: ['SLOT_OUTSIDE_WINDOW BASIC d1: slot MORNING is outside the arrival/departure window on day 1']},
        ],
    }));
    render(<AdminAiPlanner pollIntervalMs={1}/>);

    await user.type(screen.getByLabelText('Message'), 'friday evening, leaving sunday morning');
    await user.click(screen.getByRole('button', {name: 'Send'}));

    expect(await screen.findByText(/both model drafts were rejected, so Java composed these \(degraded\)/)).toBeInTheDocument();
    expect(screen.getByText('compose: model call failed (LLM_INVALID_OUTPUT)')).toBeInTheDocument();
    expect(screen.getByText(/^repair: 1 rule violation\(s\) — SLOT_OUTSIDE_WINDOW BASIC d1/)).toBeInTheDocument();
    expect(screen.queryByText(/broke a scheduling rule twice/)).not.toBeInTheDocument();
});

test('the server\'s suggested replies are chips that send themselves', async () => {
    const user = userEvent.setup();
    const expectedReply = 'Steak dinner with a show';
    mockApi.aiGetSession.mockResolvedValue(sessionState({suggestedReplies: [expectedReply, 'Bar crawl + club night']}));
    mockApi.aiSendMessage.mockResolvedValue({
        message: {role: 'ASSISTANT', content: 'Noted.'},
        messages: [{role: 'ASSISTANT', content: 'Noted.'}],
        generation: null,
    });
    localStorage.setItem('trivlu-admin-ai-planner-session', TOKEN);
    render(<AdminAiPlanner/>);

    await user.click(await screen.findByRole('button', {name: 'Resume last'}));
    await user.click(await screen.findByRole('button', {name: expectedReply}));

    await waitFor(() => expect(mockApi.aiSendMessage).toHaveBeenCalledWith(TOKEN, expectedReply));
});

test('choosing a package posts the selection and shows the Trip Builder items', async () => {
    const user = userEvent.setup();
    mockApi.aiGetSession.mockResolvedValue(sessionState({status: 'READY', latestReadyGeneration: readyGeneration()}));
    mockApi.aiSelectPackage.mockResolvedValue({packageKey: 'BASIC', groupSize: 8, tripItems: [{activityId: 'a1', name: 'Prague Pub Crawl'}]});
    localStorage.setItem('trivlu-admin-ai-planner-session', TOKEN);
    render(<AdminAiPlanner/>);

    await user.click(screen.getByRole('button', {name: 'Resume last'}));
    await user.click(await screen.findByRole('button', {name: 'Choose BASIC'}));

    expect(mockApi.aiSelectPackage).toHaveBeenCalledWith(readyGeneration().id, 'BASIC');
    expect(await screen.findByText('selected BASIC: 1 Trip Builder items for 8 people')).toBeInTheDocument();
    const selection = screen.getByText('Last selection (Trip Builder items)').parentElement;
    expect(within(selection).getByText(/Prague Pub Crawl/)).toBeInTheDocument();
});

test('an API error lands in the chat as an error line and hands the text back', async () => {
    const user = userEvent.setup();
    const err = new Error('Your packages are being built, one moment');
    err.status = 409;
    err.body = {error: 'GENERATION_IN_PROGRESS'};
    mockApi.aiSendMessage.mockRejectedValue(err);
    render(<AdminAiPlanner/>);

    await user.type(screen.getByLabelText('Message'), 'hello');
    await user.click(screen.getByRole('button', {name: 'Send'}));

    expect(await screen.findByText('409 GENERATION_IN_PROGRESS Your packages are being built, one moment')).toBeInTheDocument();
    expect(screen.getByLabelText('Message')).toHaveValue('hello');
});
