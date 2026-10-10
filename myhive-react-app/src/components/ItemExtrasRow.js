import {useId, useState} from 'react';
import {useT} from '../i18n';
import {formatAmount} from '../utils/format';
import {extraAmount, extrasOf} from '../utils/tripPricing';
import ItemExtrasPicker from './ItemExtrasPicker';

/**
 * The extras of one Trip Builder row: a chip per ticked extra with what it
 * adds to the line for this many travellers (× unticks it), and a toggle that
 * opens the checklist inline, so the organizer never leaves the itinerary to
 * pick one. Nothing for an activity without extras. `onChange` gets the row's
 * new codes.
 */
function ItemExtrasRow({item, travelers, onChange}) {
    const t = useT('extras');
    const pickerId = useId();
    const [open, setOpen] = useState(false);
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
                aria-controls={pickerId}
                onClick={() => setOpen(wasOpen => !wasOpen)}
            >
                {open ? t('done') : t('add')}
            </button>
            {open && <ItemExtrasPicker id={pickerId} compact extras={extras} selected={selected} onChange={onChange}/>}
        </div>
    );
}

export default ItemExtrasRow;
