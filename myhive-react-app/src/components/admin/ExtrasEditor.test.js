import {useState} from 'react';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ExtrasEditor from './ExtrasEditor';

// The editor is controlled: the harness keeps its value the way the admin form does.
function Harness({initial = [], onChange}) {
    const [value, setValue] = useState(initial);
    return (
        <ExtrasEditor
            value={value}
            onChange={(next) => {
                onChange(next);
                setValue(next);
            }}
        />
    );
}

test('adding a row and filling it emits the extra, priced as a number', async () => {
    const user = userEvent.setup();
    const onChange = jest.fn();
    render(<Harness onChange={onChange}/>);

    await user.click(screen.getByRole('button', {name: '+ Add extra'}));
    await user.type(screen.getByLabelText('Extra 1 name'), 'Private show');
    await user.type(screen.getByLabelText('Extra 1 price'), '250');

    expect(onChange).toHaveBeenLastCalledWith([{name: 'Private show', price: 250, pricing: 'PER_GROUP', description: ''}]);
});

test('the pricing select switches an extra to per person', async () => {
    const user = userEvent.setup();
    const onChange = jest.fn();
    render(<Harness initial={[{code: 'photo', name: 'Photos', price: 15, pricing: 'PER_GROUP', description: ''}]}
                    onChange={onChange}/>);

    await user.selectOptions(screen.getByLabelText('Extra 1 pricing'), 'PER_PERSON');

    // An existing extra keeps its code, so the booking history still matches it.
    expect(onChange).toHaveBeenLastCalledWith([{code: 'photo', name: 'Photos', price: 15, pricing: 'PER_PERSON', description: ''}]);
});

test('removing the last row emits an empty list', async () => {
    const user = userEvent.setup();
    const onChange = jest.fn();
    render(<Harness initial={[{code: 'show', name: 'Private show', price: 250, pricing: 'PER_GROUP', description: ''}]}
                    onChange={onChange}/>);

    await user.click(screen.getByRole('button', {name: 'Remove extra 1'}));

    expect(onChange).toHaveBeenLastCalledWith([]);
    expect(screen.queryByLabelText('Extra 1 name')).not.toBeInTheDocument();
});
