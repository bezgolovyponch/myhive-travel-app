import {useState} from 'react';
import {useT} from '../i18n';
import {formatAmount} from '../utils/format';
import {extrasOf} from '../utils/tripPricing';
import ItemExtrasPicker from './ItemExtrasPicker';

/**
 * The extras of one Trip Builder row: a chip per ticked extra (× unticks it)
 * and a toggle that opens the checklist inline, so the organizer never leaves
 * the itinerary to pick one. Nothing for an activity without extras.
 * `onChange` gets the row's new codes.
 */
function ItemExtrasRow({item, onChange}) {
    const t = useT('extras');
    const [open, setOpen] = useState(false);
    const extras = item.extras || [];
    if (extras.length === 0) {
        return null;
    }
    const selected = item.selectedExtras || [];
    const chosen = extrasOf(item);

    return (
        <div className="itinerary-item-extras">
            {chosen.length > 0 && (
                <ul className="itinerary-item-extras-chips">
                    {chosen.map(extra => (
                        <li key={extra.code} className="itinerary-item-extras-chip">
                            <span>{`${extra.name} · +${formatAmount(extra.price)}`}</span>
                            <button
                                type="button"
                                className="itinerary-item-extras-remove"
                                aria-label={t('removeAria', {name: extra.name})}
                                onClick={() => onChange(selected.filter(code => code !== extra.code))}
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
                onClick={() => setOpen(wasOpen => !wasOpen)}
            >
                {open ? t('done') : t('add')}
            </button>
            {open && <ItemExtrasPicker compact extras={extras} selected={selected} onChange={onChange}/>}
        </div>
    );
}

export default ItemExtrasRow;
