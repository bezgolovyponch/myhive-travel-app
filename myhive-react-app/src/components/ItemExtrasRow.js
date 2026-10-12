import {useT} from '../i18n';
import {formatAmount} from '../utils/format';
import {extraAmount, extrasOf} from '../utils/tripPricing';

/**
 * The extras of one Trip Builder row, under its title: a chip per chosen extra
 * with what it adds to the line for this many travellers (× drops it), and a
 * button that opens the activity's card - the one place extras are ticked and
 * confirmed (`onOpen`). Nothing for an activity without extras. `onChange`
 * gets the row's new codes when a chip is dropped.
 */
function ItemExtrasRow({item, travelers, onChange, onOpen}) {
    const t = useT('extras');
    const extras = item.extras || [];
    if (extras.length === 0) {
        return null;
    }
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
                aria-haspopup="dialog"
                onClick={onOpen}
            >
                {chosen.length > 0 ? t('edit') : t('add')}
            </button>
        </div>
    );
}

export default ItemExtrasRow;
