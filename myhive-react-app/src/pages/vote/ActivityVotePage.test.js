import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, RouterProvider, createMemoryRouter } from 'react-router-dom';
import ActivityVotePage from './ActivityVotePage';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

const TWO_ACTIVITIES = [
    { id: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    { id: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: [] },
];

function renderAt(entry) {
    return render(
        <MemoryRouter initialEntries={[entry]}>
            <Routes>
                <Route path="/vote/:shareToken/activities" element={<ActivityVotePage />} />
                <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
            </Routes>
        </MemoryRouter>
    );
}

beforeEach(() => {
    localStorage.clear();
    pushEvent.mockClear();
    voteApi.getSession.mockResolvedValue({ voteMode: 'QUIZ' });
});

test('shows a friendly message when the vote session is not found', async () => {
    voteApi.getActivities.mockRejectedValue(new Error('Vote session not found'));

    renderAt('/vote/tok-404/activities');

    expect(await screen.findByText(/this vote session no longer exists/i)).toBeInTheDocument();
    expect(screen.getByText(/ask the organiser for a new link/i)).toBeInTheDocument();
});

test('shows the generic error message on other failures', async () => {
    voteApi.getActivities.mockRejectedValue(new Error('Failed to fetch vote activities'));

    renderAt('/vote/tok-500/activities');

    expect(await screen.findByText(/failed to fetch vote activities/i)).toBeInTheDocument();
});

// --- A14: vote_opened ---

test('A14: vote_opened fires once on mount with correct params', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);

    renderAt('/vote/tok-abc/activities');

    await screen.findByLabelText('Like');

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('vote_opened', {
        trip_id: 'tok-abc',
        user_role: 'participant',
    });
});

test('A14: vote_opened fires once even when already-voted redirect triggers', async () => {
    localStorage.setItem('myhive-voted-tok-abc', 'true');
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);

    renderAt('/vote/tok-abc/activities');

    await screen.findByText('waiting page');

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('vote_opened', {
        trip_id: 'tok-abc',
        user_role: 'participant',
    });
});

test('A14: vote_opened does not fire multiple times on re-render', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);

    const { rerender } = renderAt('/vote/tok-abc/activities');

    await screen.findByLabelText('Like');

    rerender(
        <MemoryRouter initialEntries={['/vote/tok-abc/activities']}>
            <Routes>
                <Route path="/vote/:shareToken/activities" element={<ActivityVotePage />} />
                <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
            </Routes>
        </MemoryRouter>
    );

    await waitFor(() => {
        expect(pushEvent).toHaveBeenCalledTimes(1);
    });
});

test('A14: vote_opened fires again for a different shareToken when component stays mounted', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);

    const router = createMemoryRouter(
        [
            { path: '/vote/:shareToken/activities', element: <ActivityVotePage /> },
            { path: '/vote/:shareToken/waiting', element: <div>waiting page</div> },
        ],
        { initialEntries: ['/vote/tok-first/activities'] }
    );

    render(<RouterProvider router={router} />);

    await screen.findByLabelText('Like');

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('vote_opened', {
        trip_id: 'tok-first',
        user_role: 'participant',
    });

    // Navigate to a different shareToken — same component instance
    await act(async () => {
        router.navigate('/vote/tok-second/activities');
    });

    await waitFor(() => {
        expect(pushEvent).toHaveBeenCalledTimes(2);
    });

    expect(pushEvent).toHaveBeenCalledWith('vote_opened', {
        trip_id: 'tok-second',
        user_role: 'participant',
    });

    // Navigate back to tok-first — must NOT fire again (already in the Set)
    await act(async () => {
        router.navigate('/vote/tok-first/activities');
    });

    await waitFor(() => {
        // Still only 2 total calls
        expect(pushEvent).toHaveBeenCalledTimes(2);
    });
});

// --- A15: vote_completed ---

test('A15: vote_completed fires once after all cards are swiped and castVotes resolves', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);
    voteApi.castVotes.mockResolvedValue({});

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByLabelText('Like')).toBeInTheDocument();

    pushEvent.mockClear(); // clear the vote_opened call

    await userEvent.click(screen.getByLabelText('Like'));    // act1 → right
    await userEvent.click(screen.getByLabelText('Dislike')); // act2 → left (last card)

    await screen.findByText('waiting page');

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('vote_completed', {
        trip_id: 'tok-abc',
        user_role: 'participant',
    });
});

test('A15: vote_completed does NOT fire if castVotes rejects', async () => {
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);
    voteApi.castVotes.mockRejectedValue(new Error('Network error'));

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByLabelText('Like')).toBeInTheDocument();

    pushEvent.mockClear(); // clear the vote_opened call

    await userEvent.click(screen.getByLabelText('Like'));
    await userEvent.click(screen.getByLabelText('Dislike'));

    await screen.findByText(/failed to submit votes/i);

    expect(pushEvent).not.toHaveBeenCalledWith('vote_completed', expect.anything());
});

// --- CART voteMode branch ---

test('renders the list voting UI for CART sessions', async () => {
    voteApi.getSession.mockResolvedValue({ voteMode: 'CART', status: 'ACTIVE' });
    voteApi.getActivities.mockResolvedValue([
        { id: 'a-1', name: 'Bar Crawl', price: 45 },
    ]);

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByRole('button', { name: /Submit vote/ })).toBeInTheDocument();
});
