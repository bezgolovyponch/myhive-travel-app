import {useId} from 'react';
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

    if (!isOpen) {
        return null;
    }

    return (
        <div
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
                    <button type="button" className="app-modal-close-btn" onClick={onClose} aria-label="Close">×</button>
                </div>
                <div className="app-modal-body">{children}</div>
                {footer && <div className="app-modal-footer">{footer}</div>}
            </div>
        </div>
    );
}

export default AppModal;
