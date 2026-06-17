import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HowItWorksSection from './HowItWorksSection';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
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
