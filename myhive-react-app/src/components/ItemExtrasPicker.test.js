import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ItemExtrasPicker from './ItemExtrasPicker';

const expectedShowDescription = 'Just your group';
const extras = [
    {code: 'show', name: 'Private show', description: expectedShowDescription, price: 250, pricing: 'PER_GROUP'},
    {code: 'photo', name: 'Photos', description: 'A photographer', price: 15, pricing: 'PER_PERSON'},
];

test('every extra starts unticked, with its description and unit price', () => {
    const expectedGroupLabel = '+€250 per group';
    const expectedPersonLabel = '+€15 per person';
    render(<ItemExtrasPicker extras={extras} selected={[]} onChange={jest.fn()}/>);

    expect(screen.getByRole('group', {name: 'Optional extras'})).toBeInTheDocument();
    expect(screen.getByRole('checkbox', {name: /Private show/})).not.toBeChecked();
    expect(screen.getByRole('checkbox', {name: /Photos/})).not.toBeChecked();
    expect(screen.getByText(expectedShowDescription)).toBeInTheDocument();
    expect(screen.getByText(expectedGroupLabel)).toBeInTheDocument();
    expect(screen.getByText(expectedPersonLabel)).toBeInTheDocument();
});

test('ticking an extra reports the new selection', async () => {
    const expectedCodes = ['show'];
    const onChange = jest.fn();
    render(<ItemExtrasPicker extras={extras} selected={[]} onChange={onChange}/>);

    await userEvent.click(screen.getByRole('checkbox', {name: /Private show/}));

    expect(onChange).toHaveBeenCalledWith(expectedCodes);
});

test('unticking keeps the rest, in the activity’s order', async () => {
    const expectedCodes = ['show'];
    const onChange = jest.fn();
    render(<ItemExtrasPicker extras={extras} selected={['photo', 'show']} onChange={onChange}/>);

    expect(screen.getByRole('checkbox', {name: /Photos/})).toBeChecked();
    await userEvent.click(screen.getByRole('checkbox', {name: /Photos/}));

    expect(onChange).toHaveBeenCalledWith(expectedCodes);
});
