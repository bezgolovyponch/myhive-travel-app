import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import HowBookingWorksSection from './HowBookingWorksSection';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
});

test('clicking the WhatsApp link fires contact_click with channel whatsapp', async () => {
    const user = userEvent.setup();

    render(<HowBookingWorksSection/>);

    await user.click(screen.getByRole('link', {name: 'WhatsApp'}));

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('contact_click', {channel: 'whatsapp'});
});

test('clicking the Facebook Messenger link fires contact_click with channel messenger', async () => {
    const user = userEvent.setup();

    render(<HowBookingWorksSection/>);

    await user.click(screen.getByRole('link', {name: 'Facebook Messenger'}));

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('contact_click', {channel: 'messenger'});
});

test('pushEvent does not fire on render', () => {
    render(<HowBookingWorksSection/>);

    expect(pushEvent).not.toHaveBeenCalled();
});
