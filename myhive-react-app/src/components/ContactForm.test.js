import {render, screen} from '@testing-library/react';
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
