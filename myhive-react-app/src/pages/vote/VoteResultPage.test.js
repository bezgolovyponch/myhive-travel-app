import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import VoteResultPage from './VoteResultPage';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { resolveUserRole } from '../../utils/userRole';
import { TripContext } from '../../context/TripContext';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));
jest.mock('../../utils/userRole', () => ({ resolveUserRole: jest.fn() }));

const DEFAULT_RESULT = {
    result: [
        { activityId: 'act1', name: 'Tank Driving', price: 150, likeCount: 3, skipCount: 0 },
        { activityId: 'act2', name: 'Spa Day', price: 80, likeCount: 2, skipCount: 1 },
    ],
    totalPrice: 230,
    budget: 500,
    numberOfTravelers: 2,
    suggestions: [],
    destinationSlug: 'bali',
    startDate: '2026-08-01',
    endDate: '2026-08-10',
};

function renderAt(entry, tripState = { tripItems: [] }) {
    return render(
        <TripContext.Provider value={{ state: tripState, dispatch: jest.fn() }}>
            <MemoryRouter initialEntries={[entry]}>
                <Routes>
                    <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
                </Routes>
            </MemoryRouter>
        </TripContext.Provider>
    );
}

beforeEach(() => {
    localStorage.clear();
    pushEvent.mockClear();
    resolveUserRole.mockReturnValue('organizer');
    voteApi.getResult.mockResolvedValue(DEFAULT_RESULT);
});

// --- A16: checkout_viewed ---

test('A16: checkout_viewed fires once after successful getResult with correct params', async () => {
    renderAt('/vote/tok-abc/result');

    // Wait for result data to appear on screen
    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', {
        trip_id: 'tok-abc',
        user_role: 'organizer',
        items_count: 2,
        value: 230,
        currency: 'EUR',
    });
    expect(resolveUserRole).toHaveBeenCalledWith('tok-abc');
});

test('A16: checkout_viewed does NOT fire when getResult rejects', async () => {
    voteApi.getResult.mockRejectedValue(new Error('Result not available yet'));

    renderAt('/vote/tok-err/result');

    // Wait for the error to render
    expect(await screen.findByText(/Result not available yet/i)).toBeInTheDocument();

    expect(pushEvent).not.toHaveBeenCalledWith('checkout_viewed', expect.anything());
});

test('A16: checkout_viewed fires only once even if the component re-renders', async () => {
    const { rerender } = renderAt('/vote/tok-once/result');

    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();
    expect(pushEvent).toHaveBeenCalledTimes(1);

    // Trigger a re-render by providing a new tripState reference (simulates context update)
    rerender(
        <TripContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
            <MemoryRouter initialEntries={['/vote/tok-once/result']}>
                <Routes>
                    <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
                </Routes>
            </MemoryRouter>
        </TripContext.Provider>
    );

    // Still only one call
    expect(pushEvent).toHaveBeenCalledTimes(1);
});

test('A16: items_count reflects actual number of result activities', async () => {
    voteApi.getResult.mockResolvedValue({
        ...DEFAULT_RESULT,
        result: [
            { activityId: 'act1', name: 'Bungee Jumping', price: 99, likeCount: 4, skipCount: 0 },
        ],
        totalPrice: 99,
    });

    renderAt('/vote/tok-single/result');

    expect(await screen.findByText('Bungee Jumping')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        items_count: 1,
        value: 99,
    }));
});

test('A16: value is 0 and items_count is 0 when result list is empty', async () => {
    voteApi.getResult.mockResolvedValue({
        ...DEFAULT_RESULT,
        result: [],
        totalPrice: 0,
    });

    renderAt('/vote/tok-empty/result');

    // Empty-state message is shown
    expect(await screen.findByText(/The group didn't agree on anything/i)).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        items_count: 0,
        value: 0,
    }));
});

test('A16: user_role comes from resolveUserRole with the shareToken', async () => {
    resolveUserRole.mockReturnValue('participant');

    renderAt('/vote/tok-part/result');

    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        user_role: 'participant',
        trip_id: 'tok-part',
    }));
});
