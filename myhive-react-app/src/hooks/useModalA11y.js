import {useEffect, useRef} from 'react';

// Shared dialog behavior (extracted from ActivityPreviewModal): while isOpen,
// traps Tab inside the returned contentRef, closes on Escape, focuses the
// first focusable element on open and restores focus on close/unmount.
export function useModalA11y(isOpen, onClose) {
    const contentRef = useRef(null);
    const previouslyFocusedRef = useRef(null);
    const onCloseRef = useRef(onClose);

    // Keep the ref current so the focus effect can call the latest onClose without
    // depending on it — listing onClose would re-run the effect (and yank focus) on
    // every parent re-render, since call sites pass an inline-arrow onClose.
    useEffect(() => {
        onCloseRef.current = onClose;
    });

    useEffect(() => {
        if (!isOpen) {
            return;
        }
        previouslyFocusedRef.current = document.activeElement;
        const node = contentRef.current;

        const getFocusable = () => {
            if (!node) {
                return [];
            }
            return Array.from(
                node.querySelectorAll(
                    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
                )
            ).filter((el) => !el.hasAttribute('disabled'));
        };

        const focusables = getFocusable();
        if (focusables.length > 0) {
            focusables[0].focus();
        }

        const handleKey = (e) => {
            if (e.key === 'Escape') {
                onCloseRef.current();
                return;
            }
            if (e.key === 'Tab') {
                const items = getFocusable();
                if (items.length === 0) {
                    e.preventDefault();
                    return;
                }
                const first = items[0];
                const last = items[items.length - 1];
                const active = document.activeElement;
                if (!node.contains(active)) {
                    // Focus drifted outside the dialog (e.g. onto <body>) — pull it back.
                    e.preventDefault();
                    first.focus();
                } else if (e.shiftKey && active === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && active === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        };

        document.addEventListener('keydown', handleKey);
        // Cleanup runs on close (isOpen -> false) or unmount; restore focus then.
        return () => {
            document.removeEventListener('keydown', handleKey);
            const prev = previouslyFocusedRef.current;
            if (prev && typeof prev.focus === 'function' && document.contains(prev)) {
                prev.focus();
            }
        };
    }, [isOpen]);

    return contentRef;
}
