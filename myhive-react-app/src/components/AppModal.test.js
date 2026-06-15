import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AppModal from './AppModal';

function renderModal(props = {}) {
    const onClose = jest.fn();
    render(
        <AppModal isOpen onClose={onClose} title="Test Dialog" {...props}>
            <p>Body content</p>
        </AppModal>
    );
    return {onClose};
}

describe('AppModal', () => {
    it('renders nothing when closed', () => {
        const {container} = render(
            <AppModal isOpen={false} onClose={jest.fn()} title="Hidden">
                <p>Body content</p>
            </AppModal>
        );
        expect(container).toBeEmptyDOMElement();
    });

    it('labels the dialog with its title', () => {
        renderModal();
        const dialog = screen.getByRole('dialog');
        expect(dialog).toHaveAttribute('aria-modal', 'true');
        expect(dialog).toHaveAccessibleName('Test Dialog');
    });

    it('closes via the × button', async () => {
        const user = userEvent.setup();
        const {onClose} = renderModal();

        await user.click(screen.getByRole('button', {name: 'Close'}));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('closes on backdrop click only when closeOnBackdrop is set', async () => {
        const user = userEvent.setup();
        const {onClose} = renderModal({closeOnBackdrop: true});

        await user.click(screen.getByRole('dialog'));
        expect(onClose).toHaveBeenCalledTimes(1);

        // A click on the content must not bubble to the backdrop handler.
        await user.click(screen.getByText('Body content'));
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('ignores backdrop clicks when closeOnBackdrop is not set', async () => {
        const user = userEvent.setup();
        const {onClose} = renderModal();

        await user.click(screen.getByRole('dialog'));

        expect(onClose).not.toHaveBeenCalled();
    });
});
