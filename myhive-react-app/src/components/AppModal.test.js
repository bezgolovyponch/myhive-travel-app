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

// CRA's Jest stubs CSS imports, so assert on the declared rules instead.
// Regression: on phones the modal is a bottom sheet (align-items: flex-end).
// `.app-modal` pins to `inset: 0` — the *layout* viewport, which on iOS Safari
// extends behind the bottom toolbar — while the sheet is capped at 92dvh, the
// *visible* height. The sheet was therefore anchored ~a toolbar's worth below
// the screen, hiding its footer (Cancel / Continue) and the date row.
describe('mobile bottom-sheet anchoring', () => {
    const fs = require('fs');
    const path = require('path');
    const css = fs.readFileSync(path.join(__dirname, '../styles/global.css'), 'utf8');
    const mobileBlock = css.match(/@media\s*\(max-width:\s*768px\)\s*{[\s\S]*?\n}/)[0];
    const modalRule = mobileBlock.match(/\.app-modal\s*{[^}]*}/)[0];

    it('anchors the sheet to the dynamic (visible) viewport, not the layout viewport', () => {
        expect(modalRule).toMatch(/height:\s*100dvh/);
        // Without bottom:auto the inherited `inset: 0` keeps winning over height.
        expect(modalRule).toMatch(/bottom:\s*auto/);
    });

    it('keeps the sheet bottom-aligned', () => {
        expect(modalRule).toMatch(/align-items:\s*flex-end/);
    });
});
