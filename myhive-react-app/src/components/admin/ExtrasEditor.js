import {Button, Col, Form, Row} from 'react-bootstrap';

// A new row: no code yet (the server derives it from the name), billed per group by default.
const EMPTY_EXTRA = {name: '', price: '', pricing: 'PER_GROUP', description: ''};

/**
 * An activity's optional extras in the admin form: one row each with name,
 * price, per group / per person and a one-line description for the customer.
 * Controlled: `value` is the list, `onChange` gets the whole new list. Prices
 * come out as numbers ('' while the field is empty). Existing rows keep their
 * `code`.
 */
function ExtrasEditor({value, onChange}) {
    const rows = value || [];

    const update = (index, field, fieldValue) =>
        onChange(rows.map((row, i) => (i === index ? {...row, [field]: fieldValue} : row)));

    return (
        <div className="d-flex flex-column gap-2">
            {rows.map((row, index) => {
                const label = `Extra ${index + 1}`;
                return (
                    // Index keys: rows have no stable id until saved, and every field is controlled.
                    <div key={index} className="border rounded p-2">
                        <Row className="g-2">
                            <Col sm={6}>
                                <Form.Control
                                    size="sm"
                                    aria-label={`${label} name`}
                                    placeholder="Name"
                                    value={row.name}
                                    onChange={e => update(index, 'name', e.target.value)}
                                />
                            </Col>
                            <Col sm={3}>
                                <Form.Control
                                    size="sm"
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    aria-label={`${label} price`}
                                    placeholder="€"
                                    value={row.price}
                                    onChange={e => update(index, 'price', e.target.value === '' ? '' : Number(e.target.value))}
                                />
                            </Col>
                            <Col sm={3}>
                                <Form.Select
                                    size="sm"
                                    aria-label={`${label} pricing`}
                                    value={row.pricing}
                                    onChange={e => update(index, 'pricing', e.target.value)}
                                >
                                    <option value="PER_GROUP">per group</option>
                                    <option value="PER_PERSON">per person</option>
                                </Form.Select>
                            </Col>
                            <Col xs={12} className="d-flex gap-2">
                                <Form.Control
                                    size="sm"
                                    aria-label={`${label} description`}
                                    placeholder="One line for the customer"
                                    value={row.description || ''}
                                    onChange={e => update(index, 'description', e.target.value)}
                                />
                                <Button
                                    variant="outline-danger"
                                    size="sm"
                                    aria-label={`Remove extra ${index + 1}`}
                                    onClick={() => onChange(rows.filter((_, i) => i !== index))}
                                >
                                    ×
                                </Button>
                            </Col>
                        </Row>
                    </div>
                );
            })}
            <div>
                <Button variant="outline-secondary" size="sm" onClick={() => onChange([...rows, {...EMPTY_EXTRA}])}>
                    + Add extra
                </Button>
            </div>
        </div>
    );
}

export default ExtrasEditor;
