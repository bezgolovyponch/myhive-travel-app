import {fireEvent, render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AiRecommendations from './AiRecommendations';

// jsdom has no PointerEvent; a MouseEvent carries clientY, and the pointerId is kept for the capture.
beforeAll(() => {
    if (!window.PointerEvent) {
        window.PointerEvent = class PointerEvent extends MouseEvent {
            constructor(type, init = {}) {
                super(type, init);
                this.pointerId = init.pointerId;
            }
        };
    }
});

const rec = (name, over = {}) => ({
    activityId: `id-${name}`, name, oneLine: `${name} in one line`, durationMinutes: 90, imageUrl: null, ...over,
});
const FOUR = [rec('AK-47 shooting'), rec('Paintball'), rec('Pistol + AK combo'), rec('Laser tag')];

function renderRecs(props = {}) {
    const onToggle = jest.fn();
    const utils = render(
        <AiRecommendations recommendations={FOUR} resetKey="offer-1" isAdded={() => false} onOpen={jest.fn()}
                           onToggle={onToggle} pendingId={null} disabled={false} {...props}/>,
    );
    return {...utils, onToggle};
}

const rerenderWith = (rerender, props) => rerender(
    <AiRecommendations recommendations={FOUR} resetKey="offer-1" isAdded={() => false} onOpen={jest.fn()}
                       onToggle={jest.fn()} pendingId={null} disabled={false} {...props}/>,
);

const group = () => screen.getByRole('group', {name: 'Suggested activities'});
const addButton = (name) => within(group()).queryByRole('button', {name: `Add ${name} to the trip draft`});
const swipe = (element, fromY, toY) => {
    fireEvent.pointerDown(element, {clientY: fromY, pointerId: 1});
    fireEvent.pointerUp(element, {clientY: toY, pointerId: 1});
};

test('compact: the count, the top match as a card, the rest behind "3 more"', () => {
    renderRecs();

    expect(within(group()).getByRole('button', {name: 'Recommendations (4)'})).toHaveAttribute('aria-expanded', 'false');
    expect(addButton('AK-47 shooting')).toBeInTheDocument();
    expect(within(group()).getByText('1 h 30 min · AK-47 shooting in one line')).toBeInTheDocument();
    expect(within(group()).queryByText('Paintball')).not.toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: '3 more'})).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: 'Hide'})).toBeInTheDocument();
});

test('expanded: every recommendation is a card with Add; a tap on any of them toggles it', async () => {
    const {onToggle} = renderRecs();

    await userEvent.click(screen.getByRole('button', {name: '3 more'}));

    expect(within(group()).getByRole('button', {name: 'Recommendations (4)'})).toHaveAttribute('aria-expanded', 'true');
    for (const item of FOUR) {
        expect(addButton(item.name)).toHaveTextContent('Add');
    }
    expect(within(group()).queryByRole('button', {name: '3 more'})).not.toBeInTheDocument();
    await userEvent.click(addButton('Laser tag'));
    expect(onToggle).toHaveBeenCalledWith(FOUR[3], false);

    // The header folds it back to the top match.
    await userEvent.click(screen.getByRole('button', {name: 'Recommendations (4)'}));
    expect(addButton('Laser tag')).not.toBeInTheDocument();
    expect(addButton('AK-47 shooting')).toBeInTheDocument();
});

test('"Hide" leaves one line with the count; "Show" brings the top match back', async () => {
    renderRecs();

    await userEvent.click(screen.getByRole('button', {name: 'Hide'}));

    expect(within(group()).getByRole('button', {name: 'Recommendations (4)'})).toBeInTheDocument();
    expect(addButton('AK-47 shooting')).not.toBeInTheDocument();
    expect(within(group()).queryByRole('button', {name: '3 more'})).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Show'}));
    expect(addButton('AK-47 shooting')).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: '3 more'})).toBeInTheDocument();
});

test('a new offer (the parent\'s resetKey) opens on its top match again, even when the last one was hidden', async () => {
    const {rerender} = renderRecs();
    await userEvent.click(screen.getByRole('button', {name: 'Hide'}));

    rerenderWith(rerender, {recommendations: [rec('Beer spa'), rec('Beer bike')], resetKey: 'offer-2'});

    expect(addButton('Beer spa')).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: '1 more'})).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: 'Recommendations (2)'})).toBeInTheDocument();
});

test('the same offer with the same names again still opens (the chat says "it is above" each time)', async () => {
    const {rerender} = renderRecs();
    await userEvent.click(screen.getByRole('button', {name: 'Hide'}));

    rerenderWith(rerender, {resetKey: 'offer-2'});

    expect(addButton('AK-47 shooting')).toBeInTheDocument();
});

test('an Add that takes the tapped one out of the set leaves the list open for the next tap', async () => {
    const {rerender} = renderRecs();
    await userEvent.click(screen.getByRole('button', {name: '3 more'}));

    // The default suggestions are filtered by what the draft holds: Laser tag was just added.
    rerenderWith(rerender, {recommendations: FOUR.slice(0, 3)});

    expect(addButton('Pistol + AK combo')).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: 'Recommendations (3)'})).toHaveAttribute('aria-expanded', 'true');
});

test('an added one reads "Added ✓" in the compact card and as a check in the list', async () => {
    renderRecs({isAdded: (id) => id === 'id-AK-47 shooting'});

    expect(within(group()).getByRole('button', {name: 'Remove AK-47 shooting'})).toHaveTextContent('Added ✓');
    await userEvent.click(screen.getByRole('button', {name: '3 more'}));
    expect(within(group()).getByRole('button', {name: 'Remove AK-47 shooting'})).toHaveAttribute('aria-pressed', 'true');
});

test('on the header, a swipe up opens the list, a swipe down folds it, another hides it; a tap toggles', () => {
    renderRecs();
    const head = screen.getByRole('button', {name: 'Recommendations (4)'});

    swipe(head, 300, 250);
    expect(addButton('Paintball')).toBeInTheDocument();
    // The click a real swipe ends with must not undo it.
    fireEvent.click(head);
    expect(addButton('Paintball')).toBeInTheDocument();

    swipe(head, 250, 300);
    expect(addButton('Paintball')).not.toBeInTheDocument();
    expect(addButton('AK-47 shooting')).toBeInTheDocument();

    swipe(head, 250, 300);
    expect(addButton('AK-47 shooting')).not.toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: 'Show'})).toBeInTheDocument();

    swipe(head, 300, 260);
    expect(addButton('AK-47 shooting')).toBeInTheDocument();

    // A short move is a tap: it toggles between the card and the list.
    swipe(head, 300, 290);
    fireEvent.click(head);
    expect(addButton('Paintball')).toBeInTheDocument();
});

test('a swipe that starts on "Hide" is a swipe, not a tap on Hide', () => {
    renderRecs();
    const hide = screen.getByRole('button', {name: 'Hide'});

    swipe(hide, 300, 250);
    fireEvent.click(hide);

    expect(addButton('Paintball')).toBeInTheDocument();
    expect(within(group()).queryByRole('button', {name: 'Show'})).not.toBeInTheDocument();
});

test('a swipe without a tail click (touch) does not eat the next activation of the header', () => {
    const now = jest.spyOn(Date, 'now');
    try {
        now.mockReturnValue(1_000);
        renderRecs();
        const head = screen.getByRole('button', {name: 'Recommendations (4)'});
        swipe(head, 300, 250);
        expect(addButton('Paintball')).toBeInTheDocument();

        now.mockReturnValue(3_000);
        fireEvent.click(head);

        expect(addButton('Paintball')).not.toBeInTheDocument();
        expect(addButton('AK-47 shooting')).toBeInTheDocument();
    } finally {
        now.mockRestore();
    }
});

/**
 * Chrome retargets the click to the element holding pointer capture: captured on pointerdown, every tap on
 * the header's buttons landed on the header div and Hide/Show did nothing (seen live). So the capture is
 * taken only once the pointer has moved - a drag - and a plain click never involves it.
 */
test('the header captures the pointer only once it moves, never on a plain press', () => {
    const capture = jest.fn();
    HTMLElement.prototype.setPointerCapture = capture;
    HTMLElement.prototype.hasPointerCapture = () => false;
    try {
        renderRecs();
        const head = screen.getByRole('button', {name: 'Recommendations (4)'});
        fireEvent.pointerDown(head, {clientY: 300, pointerId: 7});
        fireEvent.pointerMove(head, {clientY: 298, pointerId: 7});
        expect(capture).not.toHaveBeenCalled();

        fireEvent.pointerMove(head, {clientY: 290, pointerId: 7});
        expect(capture).toHaveBeenCalledWith(7);
    } finally {
        delete HTMLElement.prototype.setPointerCapture;
        delete HTMLElement.prototype.hasPointerCapture;
    }
});

test('expanded: "Show less" at the foot of the list folds it back to the top match', async () => {
    renderRecs();
    await userEvent.click(screen.getByRole('button', {name: '3 more'}));

    await userEvent.click(within(group()).getByRole('button', {name: 'Show less'}));

    expect(addButton('Paintball')).not.toBeInTheDocument();
    expect(addButton('AK-47 shooting')).toBeInTheDocument();
    expect(within(group()).getByRole('button', {name: '3 more'})).toBeInTheDocument();
});

test('nothing to recommend renders nothing', () => {
    const {container} = renderRecs({recommendations: []});
    expect(container).toBeEmptyDOMElement();
});
