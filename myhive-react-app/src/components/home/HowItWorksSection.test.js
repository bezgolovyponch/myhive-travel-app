import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HowItWorksSection from './HowItWorksSection';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
});

test('renders the three steps in order with the swipe moment and no CDN images', () => {
    render(<HowItWorksSection onStartVote={jest.fn()}/>);
    expect(document.querySelector('.tm2')).toBeInTheDocument();
    expect(document.querySelector('.vm')).toBeInTheDocument();
    expect(document.querySelector('.booked')).toBeInTheDocument();
    expect(screen.getAllByRole('heading', {level: 3}).map(h => h.textContent)).toEqual([
        'Pick activities to vote on',
        'Share the vote link',
        'Book the winners',
    ]);
    for (const img of document.querySelectorAll('img')) {
        expect(img.src).not.toContain('cdn.jsdelivr.net');
    }
});

test('step visual wrappers carry the v12 modifiers', () => {
    render(<HowItWorksSection onStartVote={jest.fn()}/>);
    const wrappers = document.querySelectorAll('.step-img');
    expect(wrappers).toHaveLength(3);
    expect(wrappers[0]).toHaveClass('step-img--component'); // SwipeMomentCard
    expect(wrappers[1]).toHaveClass('step-img--component'); // VoteMomentCard
    expect(wrappers[2]).toHaveClass('step-img--photo'); // limo photo + booked chip
});

test('clicking "Start Group Vote" fires cta_click with block trip_builder before calling onStartVote', async () => {
    const user = userEvent.setup();
    const onStartVote = jest.fn();

    render(<HowItWorksSection onStartVote={onStartVote}/>);

    await user.click(screen.getByRole('button', {name: 'Start Group Vote'}));

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Start Group Vote', block: 'trip_builder'});
    expect(onStartVote).toHaveBeenCalledTimes(1);
});

test('pushEvent fires before onStartVote', async () => {
    const user = userEvent.setup();
    const callOrder = [];
    pushEvent.mockImplementation(() => callOrder.push('pushEvent'));
    const onStartVote = jest.fn(() => callOrder.push('onStartVote'));

    render(<HowItWorksSection onStartVote={onStartVote}/>);

    await user.click(screen.getByRole('button', {name: 'Start Group Vote'}));

    expect(callOrder).toEqual(['pushEvent', 'onStartVote']);
});
