import {fireEvent, render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ContactForm from './ContactForm';

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

test('modal mode renders inside a dialog with the trip summary', () => {
    renderForm();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Trip Summary')).toBeInTheDocument();
});

test('inline mode renders a panel (no dialog) and submits via Confirm', async () => {
    const user = userEvent.setup();
    const {onSubmit} = renderForm({inline: true, submitLabel: 'Confirm'});

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    // Compact panel: no duplicate trip summary (it's visible in the itinerary column)
    // and the calendar stays folded while the pre-seeded date range is complete.
    expect(screen.queryByText('Trip Summary')).not.toBeInTheDocument();
    expect(screen.queryAllByRole('grid')).toHaveLength(0);

    await fillRequired(user);
    await user.click(screen.getByRole('button', {name: 'Confirm'}));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
        fullName: 'John Doe',
        email: 'john@example.com',
    }));
});

test('inline mode Cancel closes the form', async () => {
    const user = userEvent.setup();
    const {onClose} = renderForm({inline: true, submitLabel: 'Confirm'});

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(onClose).toHaveBeenCalledTimes(1);
});

test('never renders a deposit action — the deposit moved to the booking-confirmation screen', () => {
    renderForm();
    expect(screen.queryByRole('button', {name: /deposit/i})).not.toBeInTheDocument();
});

test('inline mode shows the trip total and reprices it when the traveler count changes', async () => {
    const user = userEvent.setup();
    // €45 × 2 travelers, per renderForm's tripData/initialValues.
    const expectedInitialTotal = '€90';
    const expectedTotalAfterAdding = '€135';
    renderForm({inline: true});

    expect(screen.getByText('Estimated cost')).toBeInTheDocument();
    expect(screen.getByText(expectedInitialTotal)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Increase travelers'}));

    expect(screen.getByText(expectedTotalAfterAdding)).toBeInTheDocument();
    expect(screen.queryByText(expectedInitialTotal)).not.toBeInTheDocument();
});

test('reports email input via onEmailChange and shows consent note when asked', () => {
    const onEmailChange = jest.fn();
    render(<ContactForm isOpen inline tripData={{tripItems: []}}
                        onClose={jest.fn()} onSubmit={jest.fn()}
                        onEmailChange={onEmailChange} showConsentNote />);
    fireEvent.change(screen.getByLabelText(/email address/i), {target: {value: 'sam@example.com'}});
    expect(onEmailChange).toHaveBeenCalledWith('sam@example.com');
    expect(screen.getByText(/reminders\. unsubscribe anytime/i)).toBeInTheDocument();
});
