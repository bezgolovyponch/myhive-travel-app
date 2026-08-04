import {act, render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SuccessModal from './SuccessModal';
import {WHATSAPP_URL} from '../services/config';

jest.mock('../services/paymentApi', () => ({
    paymentApi: { createBookingDepositSession: jest.fn() },
}));

const {paymentApi} = require('../services/paymentApi');

// Captures the Turnstile success callback so tests can simulate a solved captcha.
let turnstileCallback;

beforeEach(() => {
    turnstileCallback = undefined;
    window.turnstile = {
        render: (el, opts) => { turnstileCallback = opts.callback; return 'w1'; },
        remove: jest.fn(),
    };
});

afterEach(() => {
    delete window.turnstile;
});

test('shows the WhatsApp contact CTA with heading and link', () => {
  render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" />);

  expect(screen.getByText(/Contact us to get details about your trip/i)).toBeInTheDocument();
  const link = screen.getByRole('link', {name: /whatsapp/i});
  expect(link).toHaveAttribute('href', WHATSAPP_URL);
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
});

test('no deposit CTA without a bookingId (other flows keep the old success screen)', () => {
    render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" />);

    expect(screen.queryByRole('button', {name: /pay 30% deposit/i})).not.toBeInTheDocument();
});

test('deposit CTA unlocks after Turnstile and redirects to Stripe Checkout', async () => {
    const user = userEvent.setup();
    const expectedCheckoutUrl = 'https://checkout/cs_dep';
    paymentApi.createBookingDepositSession.mockResolvedValue({bookingId: 'b-1', checkoutUrl: expectedCheckoutUrl});
    const assign = jest.fn();
    Object.defineProperty(window, 'location', {configurable: true, value: {assign, href: '', hostname: 'localhost'}});

    render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" bookingId="b-1" />);

    const depositBtn = screen.getByRole('button', {name: /pay 30% deposit/i});
    expect(depositBtn).toBeDisabled(); // no captcha yet

    act(() => { turnstileCallback('tok-xyz'); });
    expect(depositBtn).toBeEnabled();

    await user.click(depositBtn);

    await waitFor(() => expect(paymentApi.createBookingDepositSession).toHaveBeenCalledWith('b-1', 'tok-xyz'));
    expect(assign).toHaveBeenCalledWith(expectedCheckoutUrl);
});

test('a failed deposit call shows the error and re-enables the button', async () => {
    const user = userEvent.setup();
    paymentApi.createBookingDepositSession.mockRejectedValue(new Error('Trip total is too small to take a deposit'));

    render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" bookingId="b-1" />);

    act(() => { turnstileCallback('tok-xyz'); });
    await user.click(screen.getByRole('button', {name: /pay 30% deposit/i}));

    expect(await screen.findByText('Trip total is too small to take a deposit')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /pay 30% deposit/i})).toBeEnabled();
});
