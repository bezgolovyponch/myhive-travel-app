import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ReviewsSection from './ReviewsSection';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
});

test('clicking "Start Group Vote" fires cta_click with block reviews before calling onStartVote', async () => {
    const user = userEvent.setup();
    const onStartVote = jest.fn();

    render(<ReviewsSection onStartVote={onStartVote}/>);

    await user.click(screen.getByRole('button', {name: 'Start Group Vote'}));

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Start Group Vote', block: 'reviews'});
    expect(onStartVote).toHaveBeenCalledTimes(1);
});

test('pushEvent fires before onStartVote', async () => {
    const user = userEvent.setup();
    const callOrder = [];
    pushEvent.mockImplementation(() => callOrder.push('pushEvent'));
    const onStartVote = jest.fn(() => callOrder.push('onStartVote'));

    render(<ReviewsSection onStartVote={onStartVote}/>);

    await user.click(screen.getByRole('button', {name: 'Start Group Vote'}));

    expect(callOrder).toEqual(['pushEvent', 'onStartVote']);
});
