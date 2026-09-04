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
    const css = fs.readFileSync(path.join(__dirname, 'AppModal.css'), 'utf8');
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

    // Once the overlay is pinned to the visual viewport (see below) it can be
    // far shorter than 92dvh — dvh ignores the keyboard — so the sheet has to
    // be capped by the overlay itself or its top half overflows off screen.
    it('caps the sheet at the overlay height so it cannot overflow the visible area', () => {
        const contentRule = mobileBlock.match(/\.app-modal-content\s*{[^}]*}/)[0];
        expect(contentRule).toMatch(/max-height:\s*min\(92dvh,\s*100%\)/);
    });
});

// The sheet is bottom-anchored, and on iOS the layout viewport it pins to runs
// on behind the browser UI — 932 vs ~740 on a 14 Pro Max, so 192px of the sheet
// (footer + the field above it) hangs off screen. The overlay is therefore
// pinned to the visual viewport too.
describe('visual viewport pinning', () => {
    const setViewport = ({height, offsetTop = 0, scale = 1, innerHeight = 932}) => {
        window.innerHeight = innerHeight;
        window.visualViewport = {
            height, offsetTop, scale,
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        };
    };

    afterEach(() => {
        delete window.visualViewport;
    });

    it('pins the overlay to the visible height', () => {
        setViewport({height: 740});
        renderModal();
        expect(screen.getByRole('dialog')).toHaveStyle({height: '740px'});
    });

    // Regression: with the keyboard open, iOS Safari both shortens the visual
    // viewport and pans it down to reveal the focused field. Pinning only the
    // height left the overlay at the top of the layout viewport — above the
    // region Safari had panned to — so the organizer email field scrolled off
    // the top of the screen the moment the keyboard opened.
    it('follows the visual viewport offset, not just its height', () => {
        setViewport({height: 404, offsetTop: 336, innerHeight: 740});
        renderModal();
        expect(screen.getByRole('dialog')).toHaveStyle({top: '336px', height: '404px'});
    });

    // iOS does not shrink window.innerHeight for the keyboard, so a guard
    // expressed as a ratio of it switched the pin off whenever the keyboard
    // covered half the screen — an iPhone 14 with the QuickType bar: 664
    // visible, 336 of keyboard.
    it('still pins when the keyboard covers more than half of innerHeight', () => {
        setViewport({height: 328, offsetTop: 336, innerHeight: 664});
        renderModal();
        expect(screen.getByRole('dialog')).toHaveStyle({top: '336px', height: '328px'});
    });

    it('re-pins as the visual viewport pans', () => {
        setViewport({height: 404, innerHeight: 740});
        renderModal();
        const [, onScroll] = window.visualViewport.addEventListener.mock.calls
            .find(([type]) => type === 'scroll');

        window.visualViewport.offsetTop = 200;
        onScroll();

        expect(screen.getByRole('dialog')).toHaveStyle({top: '200px'});
    });

    // Regression: pinch-zoom reports a tiny viewport; honouring it collapsed
    // the sheet to a sliver.
    it('ignores zoomed states', () => {
        setViewport({height: 300, offsetTop: 120, scale: 2.5});
        renderModal();
        const dialog = screen.getByRole('dialog');
        expect(dialog.style.height).toBe('');
        expect(dialog.style.top).toBe('');
    });

    // Below this the pinned sheet could not show its header, one field and
    // the footer at once, so Safari's own pan-to-reveal serves better.
    it('ignores a viewport too short to hold the sheet', () => {
        setViewport({height: 200, offsetTop: 100});
        renderModal();
        const dialog = screen.getByRole('dialog');
        expect(dialog.style.height).toBe('');
        expect(dialog.style.top).toBe('');
    });

    it('does nothing where visualViewport is unavailable', () => {
        renderModal();
        const dialog = screen.getByRole('dialog');
        expect(dialog.style.height).toBe('');
        expect(dialog.style.top).toBe('');
    });
});
