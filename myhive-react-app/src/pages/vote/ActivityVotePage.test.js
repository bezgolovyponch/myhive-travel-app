import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ActivityVotePage from './ActivityVotePage';
import voteApi from '../../services/voteApi';
import api from '../../services/api';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi');
jest.mock('../../services/api', () => ({
    __esModule: true,
    default: {
        getDestinationBySlug: jest.fn(),
        getActivities: jest.fn(),
        getCategoriesForDestination: jest.fn(),
    },
}));
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

const TWO_ACTIVITIES = [
    { id: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'prague' },
    { id: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'prague' },
];

const CATALOG = [
    ...TWO_ACTIVITIES,
    { id: 'act9', name: 'Pub golf', imageUrl: null, categories: [{ name: 'nightlife', slug: 'nightlife' }] },
    { id: 'act8', name: 'Beer spa', imageUrl: null, categories: [{ name: 'chillout', slug: 'chillout' }] },
];

const SESSION = {
    shareToken: 'tok-abc', status: 'ACTIVE', voteMode: 'CART', destinationName: 'Prague',
    destinationSlug: 'prague', startDate: '2026-10-16', endDate: '2026-10-18',
};

function renderAt(entry) {
    return render(
        <MemoryRouter initialEntries={[entry]}>
            <Routes>
                <Route path="/vote/:shareToken/activities" element={<ActivityVotePage/>}/>
                <Route path="/destination/:slug" element={<div>organiser dashboard</div>}/>
            </Routes>
        </MemoryRouter>
    );
}

async function swipeAll(likes) {
    for (const like of likes) {
        await userEvent.click(await screen.findByLabelText(like ? 'Like' : 'Dislike'));
    }
}

beforeEach(() => {
    localStorage.clear();
    voteApi.getSession.mockResolvedValue(SESSION);
    voteApi.getActivities.mockResolvedValue(TWO_ACTIVITIES);
    voteApi.castVotes.mockResolvedValue();
    api.getDestinationBySlug.mockResolvedValue({ id: 'dest-1' });
    api.getActivities.mockResolvedValue(CATALOG);
    api.getCategoriesForDestination.mockResolvedValue([
        { name: 'nightlife', slug: 'nightlife' }, { name: 'chillout', slug: 'chillout' },
    ]);
});

test('shows a friendly message when the vote session is not found', async () => {
    voteApi.getSession.mockRejectedValue(new Error('Failed to fetch vote session'));
    voteApi.getActivities.mockRejectedValue(new Error('Vote session not found'));

    renderAt('/vote/tok-404/activities');

    expect(await screen.findByText(/this vote session no longer exists/i)).toBeInTheDocument();
});

test('the swipe is full screen and has no invite link', async () => {
    const { container } = renderAt('/vote/tok-abc/activities');

    await screen.findByLabelText('Like');

    expect(container.querySelector('.swipe-card-page--fullscreen')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /invite link/i })).not.toBeInTheDocument();
    expect(pushEvent).toHaveBeenCalledWith('vote_opened', { trip_id: 'tok-abc', user_role: 'participant' });
});

test('after the last card the friend reviews, recommends and sends once; then only the thank-you', async () => {
    renderAt('/vote/tok-abc/activities');

    await swipeAll([true, false]);

    expect(await screen.findByRole('heading', { name: 'My votes' })).toBeInTheDocument();
    expect(screen.getByText(/^Prague stag · .*16.*18 Oct$/)).toBeInTheDocument();
    expect(screen.getByText('You kept 1 of 2 · Browse activities and add more')).toBeInTheDocument();
    expect(voteApi.castVotes).not.toHaveBeenCalled();

    // Flip the drop to a keep, recommend one activity off the ballot.
    await userEvent.click(screen.getAllByRole('button', { name: 'Keep' })[1]);
    await userEvent.click(await screen.findByRole('button', { name: 'Add Pub golf' }));
    expect(screen.getByText('You kept 2 of 2 · 1 recommended · Browse activities and add more')).toBeInTheDocument();
    expect(screen.getByText('Your recommendation')).toBeInTheDocument();
    // Ballot activities are not offered as recommendations.
    expect(screen.queryByRole('button', { name: 'Add Tank Driving' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Send to group' }));

    expect(await screen.findByText('Thanks, your vote is in.')).toBeInTheDocument();
    expect(voteApi.castVotes).toHaveBeenCalledTimes(1);
    expect(voteApi.castVotes).toHaveBeenCalledWith('tok-abc', expect.objectContaining({
        votes: [{ activityId: 'act1', liked: true }, { activityId: 'act2', liked: true }],
        recommendedActivityIds: ['act9'],
    }));
    expect(localStorage.getItem('myhive-voted-tok-abc')).toBe('true');
    expect(screen.queryByText(/invite/i)).not.toBeInTheDocument();
});

test('the category chips filter what can be recommended', async () => {
    renderAt('/vote/tok-abc/activities');
    await swipeAll([true, true]);
    await screen.findByRole('button', { name: 'Add Pub golf' });

    await userEvent.click(screen.getByRole('button', { name: 'Chillout' }));

    expect(screen.queryByRole('button', { name: 'Add Pub golf' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add Beer spa' })).toBeInTheDocument();
});

test('a friend who already voted only sees the thank-you, nothing is loaded', async () => {
    localStorage.setItem('myhive-voted-tok-abc', 'true');

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByText('Thanks, your vote is in.')).toBeInTheDocument();
    expect(voteApi.getActivities).not.toHaveBeenCalled();
});

test('a second ballot rejected by the server still ends on the thank-you', async () => {
    voteApi.castVotes.mockRejectedValue(new Error('Already voted'));
    renderAt('/vote/tok-abc/activities');
    await swipeAll([true, true]);

    await userEvent.click(await screen.findByRole('button', { name: 'Send to group' }));

    expect(await screen.findByText('Thanks, your vote is in.')).toBeInTheDocument();
});

test('a failed send keeps the review with an error so it can be sent again', async () => {
    voteApi.castVotes.mockRejectedValueOnce(new Error('Failed to cast votes'));
    renderAt('/vote/tok-abc/activities');
    await swipeAll([true, true]);

    await userEvent.click(await screen.findByRole('button', { name: 'Send to group' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Failed to submit votes. Please try again.');
    await userEvent.click(screen.getByRole('button', { name: 'Send to group' }));
    expect(await screen.findByText('Thanks, your vote is in.')).toBeInTheDocument();
});

test('voting already closed: the friend is told so', async () => {
    voteApi.getSession.mockResolvedValue({ ...SESSION, status: 'COMPLETED' });

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByText('Voting has closed.')).toBeInTheDocument();
});

test('the organiser opening the friend link goes to their dashboard', async () => {
    localStorage.setItem('myhive-manager-tok-abc', 'mgr');

    renderAt('/vote/tok-abc/activities');

    expect(await screen.findByText('organiser dashboard')).toBeInTheDocument();
    expect(voteApi.getActivities).not.toHaveBeenCalled();
});

test('undo on the swipe takes the last card back', async () => {
    renderAt('/vote/tok-abc/activities');
    await swipeAll([false]);

    await userEvent.click(screen.getByLabelText('Undo last swipe'));
    await swipeAll([true, true]);

    await screen.findByRole('heading', { name: 'My votes' });
    await waitFor(() => expect(screen.getByText('You kept 2 of 2 · Browse activities and add more')).toBeInTheDocument());
});
