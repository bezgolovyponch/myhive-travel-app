import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import VoteWaitingPage from './VoteWaitingPage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function Where() {
    const location = useLocation();
    return <div data-testid="where">{location.pathname + location.search}</div>;
}

function renderAt(entry) {
    return render(
        <MemoryRouter initialEntries={[entry]}>
            <Routes>
                <Route path="/vote/:shareToken/waiting" element={<VoteWaitingPage/>}/>
                <Route path="*" element={<Where/>}/>
            </Routes>
        </MemoryRouter>
    );
}

beforeEach(() => {
    localStorage.clear();
    voteApi.getSession.mockResolvedValue({ shareToken: 'tok-1', destinationSlug: 'prague', status: 'ACTIVE' });
});

test('a friend who voted only sees the thank-you: no tally, no link', async () => {
    localStorage.setItem('myhive-voted-tok-1', 'true');

    renderAt('/vote/tok-1/waiting');

    expect(await screen.findByText('Thanks, your vote is in.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByText(/copy/i)).not.toBeInTheDocument();
    expect(voteApi.getTally).not.toHaveBeenCalled();
});

test('a friend who has not voted goes to the swipe', async () => {
    renderAt('/vote/tok-1/waiting');

    expect(await screen.findByTestId('where')).toHaveTextContent('/vote/tok-1/activities');
});

test('the organiser goes to the Trip Builder dashboard', async () => {
    localStorage.setItem('myhive-manager-tok-1', 'mgr-1');

    renderAt('/vote/tok-1/waiting');

    expect(await screen.findByTestId('where'))
        .toHaveTextContent('/destination/prague?tab=trip-builder&voteSession=tok-1');
});

test('an older email link with ?manager= hands the token on to the dashboard', async () => {
    renderAt('/vote/tok-1/waiting?manager=mgr-email');

    expect(await screen.findByTestId('where'))
        .toHaveTextContent('/destination/prague?tab=trip-builder&voteSession=tok-1&manager=mgr-email');
});
