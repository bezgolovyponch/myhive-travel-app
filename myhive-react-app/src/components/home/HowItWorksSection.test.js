import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HowItWorksSection from './HowItWorksSection';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
});

test('renders the tinder moment, the vote demo card, and no CDN images', () => {
    render(<HowItWorksSection onStartVote={jest.fn()}/>);
    expect(document.querySelector('.tinder-moment')).toBeInTheDocument();
    expect(document.querySelector('.vote-card')).toBeInTheDocument();
    for (const img of document.querySelectorAll('img')) {
        expect(img.src).not.toContain('cdn.jsdelivr.net');
    }
});

test('component steps get the auto-height step-img modifier; image steps do not', () => {
    render(<HowItWorksSection onStartVote={jest.fn()}/>);
    const wrappers = document.querySelectorAll('.step-img');
    expect(wrappers).toHaveLength(4);
    expect(wrappers[0]).not.toHaveClass('step-img--component'); // style image
    expect(wrappers[1]).toHaveClass('step-img--component'); // TinderMomentCard
    expect(wrappers[2]).toHaveClass('step-img--component'); // VoteDemoCard
    expect(wrappers[3]).not.toHaveClass('step-img--component'); // review image
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
