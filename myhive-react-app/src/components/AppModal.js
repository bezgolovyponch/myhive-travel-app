import {useEffect, useId, useRef} from 'react';
import {useModalA11y} from '../hooks/useModalA11y';

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
    const titleId = useId();
    const contentRef = useModalA11y(isOpen, onClose);
    const overlayRef = useRef(null);

    // Mobile keyboards and browser toolbars shrink only the *visual* viewport;
    // a fixed inset-0 overlay keeps its layout size, so the bottom-sheet footer
    // ends up hidden behind them. Track the visual viewport and size the
    // overlay to it, so the footer buttons always stay on screen.
    useEffect(() => {
        const vv = window.visualViewport;
        if (!isOpen || !vv) return undefined;
        const apply = () => {
            const el = overlayRef.current;
            if (el) {
                el.style.top = `${vv.offsetTop}px`;
                el.style.height = `${vv.height}px`;
            }
        };
        apply();
        vv.addEventListener('resize', apply);
        vv.addEventListener('scroll', apply);
        return () => {
            vv.removeEventListener('resize', apply);
            vv.removeEventListener('scroll', apply);
        };
    }, [isOpen]);

    if (!isOpen) {
        return null;
    }

    return (
        <div
            className={`app-modal ${overlayClassName}`.trim()}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            ref={overlayRef}
            onClick={closeOnBackdrop ? onClose : undefined}
        >
            <div
                className={`app-modal-content ${contentClassName}`.trim()}
                ref={contentRef}
                onClick={closeOnBackdrop ? (e) => e.stopPropagation() : undefined}
            >
                <div className="app-modal-header">
                    <h2 id={titleId}>{title}</h2>
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>
                <div className="app-modal-body">{children}</div>
                {footer && <div className="app-modal-footer">{footer}</div>}
            </div>
        </div>
    );
}

export default AppModal;
