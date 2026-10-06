import {formatShortRange, parseISODate} from './format';

// What the organiser's contact modal and dashboard share: the invite link, the
// message for the group chat, the WhatsApp links that carry it, and the
// WhatsApp number in E.164.

// The countries the number picker offers; the first is the default. `digits`
// is how long a mobile number is there without the country code and the trunk
// 0 - [shortest, longest] - so half a number is never taken for a whole one.
export const COUNTRY_CODES = [
    {code: '+44', label: '🇬🇧 +44', digits: [10, 10]},
    {code: '+353', label: '🇮🇪 +353', digits: [9, 9]},
    {code: '+420', label: '🇨🇿 +420', digits: [9, 9]},
    {code: '+49', label: '🇩🇪 +49', digits: [10, 11]},
    {code: '+1', label: '🇺🇸 +1', digits: [10, 10]},
];

// A country the picker does not list: any length E.164 allows for a subscriber number.
const ANY_COUNTRY = [7, 12];

/**
 * "+44" + "07700 900-123" -> "+447700900123". The national part may start with
 * the trunk 0 people type at home. It has to be a whole number for that
 * country - "+420" + "6085940" is two digits short and is null, like anything
 * else that is not a number.
 */
export function toE164(countryCode, raw) {
    const digits = String(raw || '').replace(/\D/g, '').replace(/^0+/, '');
    const [min, max] = COUNTRY_CODES.find((c) => c.code === countryCode)?.digits || ANY_COUNTRY;
    if (digits.length < min || digits.length > max) {
        return null;
    }
    return `${countryCode}${digits}`;
}

/** The friend's swipe link; ?ref=invite marks shared-link arrivals in analytics. */
export function inviteUrl(origin, localePath, shareToken) {
    return `${origin}${localePath(`/vote/${shareToken}/activities`)}?ref=invite`;
}

/** "16–18 Oct", or null while the trip has no dates. */
export function tripDates(startDate, endDate) {
    const from = parseISODate(startDate);
    const to = parseISODate(endDate);
    return from && to ? formatShortRange(from, to) : null;
}

/** The message bubble: the same text the group receives, without the link. */
export function groupMessage(t, {destinationName, startDate, endDate}) {
    const dates = tripDates(startDate, endDate);
    return dates
        ? t('groupMessage.text', {destination: destinationName, dates})
        : t('groupMessage.textNoDates', {destination: destinationName});
}

/** wa.me (web) and whatsapp:// (app) links that open WhatsApp with the message and link filled in. */
export function whatsappShareUrls(message, link) {
    const text = encodeURIComponent(`${message}\n${link}`);
    return {
        webUrl: `https://wa.me/?text=${text}`,
        appUrl: `whatsapp://send?text=${text}`,
    };
}

/** The organiser's dashboard: the Trip Builder tab, live with the vote. */
export function dashboardPath(destinationSlug, shareToken) {
    return `/destination/${destinationSlug}?tab=trip-builder&voteSession=${shareToken}`;
}
