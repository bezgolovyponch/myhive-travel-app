import {formatAmount, formatDuration} from '../../utils/format';
import {useT} from '../../i18n';

/**
 * Activities offered in the chat itself, one under the other: the name opens
 * the activity's card, the button puts it into the plan - or takes it out
 * again once it is in. Used for what the chat recommends for a typed wish and
 * for the answers to a "+ Add ..." tag.
 *
 * @param offers     [{activityId, name, oneLine, durationMinutes, fromPricePerPerson, imageUrl}], best first
 * @param isAdded    (activityId) => whether the plan holds it
 * @param onOpen     (offer) => void - shows the activity's card
 * @param onToggle   (offer, added) => void
 * @param pendingId  the activity whose tap is in flight
 * @param disabled   a chat turn or another tap is in flight
 */
function AiOffers({offers, isAdded, onOpen, onToggle, pendingId, disabled}) {
    const t = useT('aiPlanner');
    const tDuration = useT('activityDetail.duration');
    if (!offers?.length) return null;
    return (
        <ul className="aip-offers" aria-label={t('result.suggestions')}>
            {offers.map((offer) => {
                const added = isAdded(offer.activityId);
                // Duration, then what it adds to the plan's own "from ... / person".
                const price = Number(offer.fromPricePerPerson ?? offer.pricePerPerson);
                const meta = [
                    formatDuration(offer.durationMinutes, tDuration),
                    Number.isFinite(price) && price > 0
                        ? t('result.priceFromPerPerson', {price: formatAmount(Math.round(price))}) : null,
                ].filter(Boolean).join(' · ');
                return (
                    <li key={offer.activityId} className="aip-offer">
                        <span className="aip-offer-thumb" aria-hidden="true">
                            {offer.imageUrl ? <img src={offer.imageUrl} alt="" loading="lazy"/> : offer.name.charAt(0)}
                        </span>
                        <div className="aip-offer-body">
                            <button type="button" className="aip-offer-name" aria-haspopup="dialog"
                                    onClick={() => onOpen(offer)}>
                                {offer.name}
                            </button>
                            {meta && <div className="aip-offer-meta">{meta}</div>}
                        </div>
                        <button
                            type="button"
                            className={`aip-offer-add${added ? ' is-added' : ''}`}
                            aria-pressed={added}
                            aria-label={t(added ? 'result.removeAria' : 'result.addAria', {name: offer.name})}
                            disabled={disabled}
                            onClick={() => onToggle(offer, added)}
                        >
                            {pendingId === offer.activityId ? '…' : added ? t('result.added') : t('result.add')}
                        </button>
                    </li>
                );
            })}
        </ul>
    );
}

export default AiOffers;
