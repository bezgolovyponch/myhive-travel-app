import {render, screen, fireEvent, act} from '@testing-library/react';
import StickyVoteCta from './StickyVoteCta';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

let ioCallback;

beforeEach(() => {
    jest.clearAllMocks();
    document.body.classList.remove('homepage-has-sticky-cta');
    window.IntersectionObserver = jest.fn((cb) => {
        ioCallback = cb;
        return {observe: jest.fn(), disconnect: jest.fn()};
    });
});

function renderCta(props = {}) {
    return render(
        <div>
            <div className="hero-cta-group"/>
            <StickyVoteCta onStartVote={jest.fn()} {...props}/>
        </div>
    );
}

test('hidden until the hero CTA leaves the viewport, then shows and marks the body', () => {
    renderCta();
    expect(screen.queryByRole('button', {name: /start group vote/i})).toBeNull();

    act(() => ioCallback([{isIntersecting: false}]));

    expect(screen.getByRole('button', {name: /start group vote/i})).toBeInTheDocument();
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(true);
});

test('stays hidden and does not mark the body while the hero CTA is still in view', () => {
    renderCta();

    act(() => ioCallback([{isIntersecting: true}]));

    expect(screen.queryByRole('button', {name: /start group vote/i})).toBeNull();
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(false);
});

test('click fires analytics + onStartVote; hidden prop suppresses it', () => {
    const onStartVote = jest.fn();
    const {rerender} = renderCta({onStartVote});
    act(() => ioCallback([{isIntersecting: false}]));

    fireEvent.click(screen.getByRole('button', {name: /start group vote/i}));

    expect(onStartVote).toHaveBeenCalled();
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Start Group Vote', block: 'sticky_mobile'});

    rerender(<div><div className="hero-cta-group"/><StickyVoteCta onStartVote={onStartVote} hidden/></div>);

    expect(screen.queryByRole('button', {name: /start group vote/i})).toBeNull();
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(false);
});

test('removes the body class on unmount', () => {
    const {unmount} = renderCta();
    act(() => ioCallback([{isIntersecting: false}]));
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(true);

    unmount();

    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(false);
});

// CRA's Jest replaces CSS imports with an empty stub, so getComputedStyle can't
// see stylesheet rules — assert on the declared values in the source CSS instead
// (mirrors the WhatsAppWidget z-index regression test).
// The sticky CTA must stack BELOW the .app-modal overlay so any modal covers it —
// today only the vote setup modal explicitly hides it via the `hidden` prop.
test('sticky CTA z-index stays below the app-modal overlay', () => {
    const fs = require('fs');
    const path = require('path');
    const ctaCss = fs.readFileSync(path.join(__dirname, 'StickyVoteCta.css'), 'utf8');
    const globalCss = fs.readFileSync(path.join(__dirname, '../../styles/global.css'), 'utf8');

    const ctaZ = Number(ctaCss.match(/\.sticky-vote-cta\s*{[^}]*?z-index:\s*(\d+)/)[1]);
    const modalZ = Number(globalCss.match(/\.app-modal\s*{[^}]*?z-index:\s*(\d+)/)[1]);

    expect(ctaZ).toBeLessThan(modalZ);
});
