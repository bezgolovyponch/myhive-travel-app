import {useEffect, useId, useRef} from 'react';
import {useModalA11y} from '../hooks/useModalA11y';
import {useT} from '../i18n';
// AppModal.css is written against the design tokens, so it brings them along:
// this component renders on pages that never load global.css (the landings).
import '../styles/tokens.css';
import './AppModal.css';

// iOS Safari lays fixed elements out against the *large* viewport, so a
// bottom-anchored sheet can sit far below the visible bottom edge: on a 14 Pro
// Max the layout viewport is 932 but only ~740 is on screen, hiding 192px —
// the whole footer plus the field above it. `height: 100dvh` should cover that,
// but where it does not the sheet's actions are simply unreachable, so pin the
// overlay to the visual viewport as well.
//
// Two guards, learned from the regression this replaces: pinch-zoom and some
// keyboard states report a tiny viewport, and applying those collapsed the
// sheet to a sliver. So ignore any zoomed state, and never accept a height
// under half the layout viewport.
const MIN_VIEWPORT_RATIO = 0.5;

function useVisualViewportHeight(isOpen, ref) {
    useEffect(() => {
        const vv = window.visualViewport;
        if (!isOpen || !vv) {
            return undefined;
        }
        // Capture the element at effect-setup time so cleanup resets the same
        // node it was measuring, not whatever ref.current points at later.
        const el = ref.current;
        const apply = () => {
            if (!el) return;
            const height = Math.round(vv.height);
            const usable = vv.scale === 1 && height >= window.innerHeight * MIN_VIEWPORT_RATIO;
            el.style.height = usable ? `${height}px` : '';
        };
        apply();
        vv.addEventListener('resize', apply);
        vv.addEventListener('scroll', apply);
        return () => {
            vv.removeEventListener('resize', apply);
            vv.removeEventListener('scroll', apply);
            if (el) el.style.height = '';
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
    useVisualViewportHeight(isOpen, overlayRef);

    if (!isOpen) {
        return null;
    }

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
