import {useT} from '../i18n';
import {extraPriceLabel} from '../utils/format';
import './ItemExtrasPicker.css';

/**
 * An activity's optional extras as a checklist: one row per extra with its
 * name, a one-line description and its price per group or per person.
 * Nothing comes ticked; only `selected` (codes) is. `onChange` gets the new
 * codes in the activity's own order. `compact` is the slimmer variant for an
 * itinerary row, where the row already names the activity. `id` lets a toggle
 * point at the checklist it reveals.
 */
function ItemExtrasPicker({id, extras, selected, onChange, compact = false}) {
    const t = useT('extras');

    const toggle = (code) => {
        const next = new Set(selected);
        if (next.has(code)) {
            next.delete(code);
        } else {
            next.add(code);
        }
        onChange(extras.filter(extra => next.has(extra.code)).map(extra => extra.code));
    };

    return (
        <fieldset id={id} className={`item-extras-picker${compact ? ' item-extras-picker--compact' : ''}`}>
            <legend className={compact ? 'visually-hidden' : 'item-extras-picker-title'}>{t('title')}</legend>
            <ul className="item-extras-picker-list">
                {extras.map(extra => (
                    <li key={extra.code}>
                        <label className="item-extras-picker-row">
                            <input
                                type="checkbox"
                                className="item-extras-picker-check"
                                checked={selected.includes(extra.code)}
                                onChange={() => toggle(extra.code)}
                            />
                            <span className="item-extras-picker-text">
                                <span className="item-extras-picker-name">{extra.name}</span>
                                {extra.description && (
                                    <span className="item-extras-picker-desc">{extra.description}</span>
                                )}
                            </span>
                            <span className="item-extras-picker-price">{extraPriceLabel(extra, t)}</span>
                        </label>
                    </li>
                ))}
            </ul>
        </fieldset>
    );
}

export default ItemExtrasPicker;
