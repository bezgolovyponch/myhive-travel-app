import {useT} from '../i18n';
import {formatAmount} from '../utils/format';
import {extraAmount, extrasOf} from '../utils/tripPricing';
import ItemExtrasPicker from './ItemExtrasPicker';

/**
 * The extras of one Trip Builder row, under its title: a chip per ticked extra
 * with what it adds to the line for this many travellers (× unticks it), and a
 * toggle for the checklist. The checklist itself is {@link ItemExtrasPanel},
 * which the row renders full width below its title line (beside the thumbnail
 * there is no room for it on a phone), so the toggle is controlled: `open`,
 * `onToggle`, and `pickerId` for `aria-controls` while the checklist is shown.
 * Nothing for an activity without extras. `onChange` gets the row's new codes.
 */
function ItemExtrasRow({item, travelers, onChange, open, onToggle, pickerId}) {
    const t = useT('extras');
    const extras = item.extras || [];
    if (extras.length === 0) {
        return null;
    }
    const selected = item.selectedExtras || [];
    const chosen = extrasOf(item);

    // Built from the extras the activity still offers, so a stale code goes with the first removal.
    const without = (code) => chosen.filter(extra => extra.code !== code).map(extra => extra.code);

    return (
        <div className="itinerary-item-extras">
            {chosen.length > 0 && (
                <ul className="itinerary-item-extras-chips">
                    {chosen.map(extra => (
                        <li key={extra.code} className="itinerary-item-extras-chip">
                            <span>{`${extra.name} · +${formatAmount(extraAmount(extra, travelers))}`}</span>
                            <button
                                type="button"
                                className="itinerary-item-extras-remove"
                                aria-label={t('removeAria', {name: extra.name})}
                                onClick={() => onChange(without(extra.code))}
                            >
                                ×
                            </button>
                        </li>
                    ))}
                </ul>
            )}
            <button
                type="button"
                className="itinerary-item-extras-toggle"
                aria-expanded={open}
                aria-controls={open ? pickerId : undefined}
                onClick={onToggle}
            >
                {open ? t('done') : t('add')}
            </button>
        </div>
    );
}

/**
 * The checklist {@link ItemExtrasRow} toggles, as a block spanning the whole
 * itinerary row. Nothing for an activity without extras.
 */
export function ItemExtrasPanel({item, onChange, pickerId}) {
    const extras = item.extras || [];
    if (extras.length === 0) {
        return null;
    }
    return (
        <div className="itinerary-item-extras-panel">
            <ItemExtrasPicker id={pickerId} compact extras={extras} selected={item.selectedExtras || []}
                              onChange={onChange}/>
        </div>
    );
}

export default ItemExtrasRow;
