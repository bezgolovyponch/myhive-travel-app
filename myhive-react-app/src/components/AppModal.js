import {useEffect, useId, useRef} from 'react';
import {useModalA11y} from '../hooks/useModalA11y';
import {useT} from '../i18n';
import './AppModal.css';

// iOS Safari lays fixed elements out against the *large* viewport, so a
// bottom-anchored sheet can sit far below the visible bottom edge: on a 14 Pro
// Max the layout viewport is 932 but only ~740 is on screen, hiding 192px —
// the whole footer plus the field above it. `height: 100dvh` should cover that,
// but where it does not the sheet's actions are simply unreachable, so pin the
// overlay to the visual viewport as well.
//
// The pin has to follow the viewport's offset, not only its height. With the
// keyboard open Safari both shortens the visual viewport and pans it down to
// reveal the focused field; a fixed overlay shortened to that height but left
// at top:0 then sits above the panned-to region, and the field the user just
// focused (the organizer email step focuses it itself) scrolls off the top of
// the screen. Pinning top to offsetTop keeps the sheet on the visible bottom
// edge — right above the keyboard — as the viewport moves.
//
// Two guards. Pinch-zoom shrinks the visual viewport without moving the
// keyboard, so a zoomed state is never pinned. And below a certain height the
// pinned sheet cannot show anything useful — header, one 56px field with the
// body padding, and the footer with its safe-area inset need roughly this
// much — so smaller readings (landscape with the keyboard up, transient
// values mid-animation) leave the overlay to the CSS and to Safari's own
// pan-to-reveal. This used to be a ratio against window.innerHeight, but iOS
// does not shrink innerHeight for the keyboard, so any keyboard covering half
// the screen (an iPhone 14 with the QuickType bar) switched the pin off in
// exactly the state it exists for.
const MIN_PINNED_HEIGHT = 240;

function useVisualViewportPin(isOpen, ref) {
    useEffect(() => {
        const vv = window.visualViewport;
        // Capture the element at effect-setup time so cleanup resets the same
        // node it was measuring, not whatever ref.current points at later.
        const el = ref.current;
        if (!isOpen || !vv || !el) {
            return undefined;
        }
        const reset = () => {
            el.style.top = '';
            el.style.height = '';
        };
        const apply = () => {
            const height = Math.round(vv.height);
            const usable = vv.scale === 1 && height >= MIN_PINNED_HEIGHT;
            if (!usable) {
                reset();
                return;
            }
            el.style.top = `${Math.round(vv.offsetTop)}px`;
            el.style.height = `${height}px`;
        };
        apply();
        vv.addEventListener('resize', apply);
        vv.addEventListener('scroll', apply);
        return () => {
            vv.removeEventListener('resize', apply);
            vv.removeEventListener('scroll', apply);
            reset();
        };
    }, [isOpen, ref]);
}

// Shared dialog scaffold: focus trap/Escape/focus restore (useModalA11y),
// aria wiring, and the standard header with a labelled close button.
function AppModal({
                      isOpen,
                      onClose,
                      title,
                      children,
                      footer,
                      contentClassName = '',
                      overlayClassName = '',
                      closeOnBackdrop = false,
                  }) {
    const t = useT('common');
    const titleId = useId();
    const contentRef = useModalA11y(isOpen, onClose);
    const overlayRef = useRef(null);
    useVisualViewportPin(isOpen, overlayRef);

    if (!isOpen) {
        return null;
    }

    // Rendered in place, not portalled to <body>. The landings scope their whole
    // design system under .tl and carry no .btn rules of main's, so a portalled
    // dialog lost its footer buttons entirely; in place they pick up .tl's.
    // position:fixed still resolves against the viewport there — .tl's
    // overflow:clip is not a containing block for fixed descendants.
    return (
        <div
            ref={overlayRef}
            className={`app-modal ${overlayClassName}`.trim()}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            onClick={closeOnBackdrop ? onClose : undefined}
        >
            <div
                className={`app-modal-content ${contentClassName}`.trim()}
                ref={contentRef}
                onClick={closeOnBackdrop ? (e) => e.stopPropagation() : undefined}
            >
                <div className="app-modal-header">
                    <h2 id={titleId}>{title}</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label={t('close')}>×</button>
                </div>
                <div className="app-modal-body">{children}</div>
                {footer && <div className="app-modal-footer">{footer}</div>}
            </div>
        </div>
    );
}

export default AppModal;
