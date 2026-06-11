import {fireEvent, render, screen} from '@testing-library/react';
import {useModalA11y} from './useModalA11y';

function TestDialog({isOpen, onClose}) {
    const contentRef = useModalA11y(isOpen, onClose);
    if (!isOpen) {
        return null;
    }
    return (
        <div role="dialog" aria-modal="true">
            <div ref={contentRef}>
                <button>First</button>
                <button>Last</button>
            </div>
        </div>
    );
}

test('focuses the first focusable element on open', () => {
    render(<TestDialog isOpen onClose={jest.fn()}/>);
    expect(screen.getByText('First')).toHaveFocus();
});

test('Escape calls onClose', () => {
    const onClose = jest.fn();
    render(<TestDialog isOpen onClose={onClose}/>);
    fireEvent.keyDown(document, {key: 'Escape'});
    expect(onClose).toHaveBeenCalledTimes(1);
});

test('Tab wraps from the last focusable back to the first', () => {
    render(<TestDialog isOpen onClose={jest.fn()}/>);
    screen.getByText('Last').focus();
    fireEvent.keyDown(document, {key: 'Tab'});
    expect(screen.getByText('First')).toHaveFocus();
});

test('restores focus to the previously focused element on close', () => {
    const outside = document.createElement('button');
    document.body.appendChild(outside);
    outside.focus();

    const {rerender} = render(<TestDialog isOpen onClose={jest.fn()}/>);
    expect(screen.getByText('First')).toHaveFocus();

    rerender(<TestDialog isOpen={false} onClose={jest.fn()}/>);
    expect(outside).toHaveFocus();
    outside.remove();
});
