import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ItemExtrasPicker from './ItemExtrasPicker';

const extras = [
    {code: 'show', name: 'Private show', description: 'Just your group', price: 250, pricing: 'PER_GROUP'},
    {code: 'photo', name: 'Photos', description: 'A photographer', price: 15, pricing: 'PER_PERSON'},
];

test('every extra starts unticked, with its description and unit price', () => {
    render(<ItemExtrasPicker extras={extras} selected={[]} onChange={jest.fn()}/>);

    expect(screen.getByRole('group', {name: 'Optional extras'})).toBeInTheDocument();
    expect(screen.getByRole('checkbox', {name: /Private show/})).not.toBeChecked();
    expect(screen.getByRole('checkbox', {name: /Photos/})).not.toBeChecked();
    expect(screen.getByText('Just your group')).toBeInTheDocument();
    expect(screen.getByText('+€250 per group')).toBeInTheDocument();
    expect(screen.getByText('+€15 per person')).toBeInTheDocument();
});

test('ticking an extra reports the new selection', async () => {
    const onChange = jest.fn();
    render(<ItemExtrasPicker extras={extras} selected={[]} onChange={onChange}/>);

    await userEvent.click(screen.getByRole('checkbox', {name: /Private show/}));

    expect(onChange).toHaveBeenCalledWith(['show']);
});

test('unticking keeps the rest, in the activity’s order', async () => {
    const onChange = jest.fn();
    render(<ItemExtrasPicker extras={extras} selected={['photo', 'show']} onChange={onChange}/>);

    expect(screen.getByRole('checkbox', {name: /Photos/})).toBeChecked();
    await userEvent.click(screen.getByRole('checkbox', {name: /Photos/}));

    expect(onChange).toHaveBeenCalledWith(['show']);
});
