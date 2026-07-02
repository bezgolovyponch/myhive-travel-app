import {act, render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ContactForm from './ContactForm';

afterEach(() => {
    delete window.turnstile;
});

function renderForm(props = {}) {
    const onSubmit = jest.fn();
    const onClose = jest.fn();
    render(
        <ContactForm
            isOpen
            onClose={onClose}
            onSubmit={onSubmit}
            tripData={{tripItems: [{id: 'a1', price: 45}]}}
            initialValues={{numberOfTravelers: 2, startDate: '2026-07-01', endDate: '2026-07-05'}}
            isSubmitting={false}
            submitError={null}
            {...props}
        />
    );
    return {onSubmit, onClose};
}

async function fillRequired(user, {phone = '+1 (555) 123-4567'} = {}) {
    await user.type(screen.getByLabelText(/Full Name/i), 'John Doe');
    await user.type(screen.getByLabelText(/Email Address/i), 'john@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), phone);
}

test('the footer Submit button (outside the form) submits via the form attribute', async () => {
    const user = userEvent.setup();
    const {onSubmit} = renderForm();

    await fillRequired(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
        fullName: 'John Doe',
        email: 'john@example.com',
        phone: '+1 (555) 123-4567',
        startDate: '2026-07-01',
        endDate: '2026-07-05',
    }));
});

test('rejects an invalid phone number and does not submit', async () => {
    const user = userEvent.setup();
    const {onSubmit} = renderForm();

    await fillRequired(user, {phone: 'abc'});
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText('Phone number is invalid')).toBeInTheDocument();
});

test('Cancel respects the in-flight submit guard', async () => {
    const user = userEvent.setup();
    const {onClose} = renderForm({isSubmitting: true});

    const cancel = screen.getByRole('button', {name: 'Cancel'});
    expect(cancel).toBeDisabled();
    await user.click(cancel);

    expect(onClose).not.toHaveBeenCalled();
});

test('no deposit button when onDepositSubmit is not provided', () => {
    renderForm();
    expect(screen.queryByRole('button', {name: /Complete and pay 30% deposit/i})).not.toBeInTheDocument();
});

test('renders the Turnstile widget with the Cloudflare test sitekey on localhost', () => {
    // jsdom serves the app from http://localhost, so the deposit widget must use the "always passes"
    // test sitekey (not the production key, which Cloudflare rejects on localhost).
    let renderedSitekey;
    window.turnstile = {
        render: (el, opts) => {
            renderedSitekey = opts.sitekey;
            return 'widget-1';
        },
        remove: jest.fn(),
    };
    renderForm({onDepositSubmit: jest.fn()});

    expect(renderedSitekey).toBe('1x00000000000000000000AA');
});

test('deposit button is disabled until Turnstile is solved, then submits with the token', async () => {
    const user = userEvent.setup();
    let turnstileCallback;
    window.turnstile = {
        render: (el, opts) => {
            turnstileCallback = opts.callback;
            return 'widget-1';
        },
        remove: jest.fn(),
    };
    const onDepositSubmit = jest.fn();
    renderForm({onDepositSubmit});

    await fillRequired(user);

    const depositBtn = screen.getByRole('button', {name: /Complete and pay 30% deposit/i});
    // No captcha token yet → the real-charge action stays locked.
    expect(depositBtn).toBeDisabled();

    // Simulate the user solving the captcha.
    act(() => {
        turnstileCallback('turnstile-tok');
    });
    expect(depositBtn).toBeEnabled();

    await user.click(depositBtn);
    expect(onDepositSubmit).toHaveBeenCalledWith(
        expect.objectContaining({fullName: 'John Doe', email: 'john@example.com'}),
        'turnstile-tok',
    );
});
