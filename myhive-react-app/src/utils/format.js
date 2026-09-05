import {currentLocale} from '../i18n/routes';

// Numbers and dates follow the page locale (separators, month names, order);
// English keeps en-GB.
const LOCALE_TAGS = {en: 'en-GB', de: 'de-DE'};

function localeTag() {
    return LOCALE_TAGS[currentLocale()] || 'en-GB';
}

export function formatAmount(amount) {
    if (amount == null) return '\u2014';
    const n = Number(amount);
    // Non-numeric input (e.g. a NaN from a bad value) renders the em-dash
    // rather than a literal "\u20ACNaN".
    if (!Number.isFinite(n)) return '\u2014';
    // Cents only when present: whole euros render clean (\u20AC45), fractional
    // amounts keep exactly two decimals (\u20AC40.50). Never one decimal.
    // Thousands are grouped the locale's way (1,600 / 1.600).
    const digits = n.toLocaleString(localeTag(), Number.isInteger(n)
        ? {maximumFractionDigits: 0}
        : {minimumFractionDigits: 2, maximumFractionDigits: 2});
    // German puts the sign after the number, separated by a space (45 \u20AC).
    // The space is a no-break so the sign never wraps away from its number.
    // English keeps the \u20AC45 prefix style.
    if (currentLocale() === 'de') {
        return `${digits}\u00A0\u20AC`;
    }
    return `\u20AC${digits}`;
}

/**
 * Whole hours and minutes, never decimal hours (3h 35m, not 3.7h). `t` is a
 * translator scoped to the `activityDetail.duration` messages, which carry the
 * locale's unit words (h / Std.). Null when there is no usable duration.
 */
export function formatDuration(minutes, t) {
    const n = Number(minutes);
    if (!Number.isFinite(n) || n <= 0) return null;
    if (n < 60) return t('minutes', {minutes: n});
    const hours = Math.floor(n / 60);
    const rest = n % 60;
    return rest ? t('hoursMinutes', {hours, rest}) : t('hours', {hours});
}

function dateLocaleTag() {
    return localeTag();
}

export function formatDate(dateStr) {
    if (!dateStr) return '\u2014';
    return new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00')).toLocaleDateString(dateLocaleTag(), {
        day: '2-digit', month: 'short', year: 'numeric',
    });
}

export function formatDateTime(dateStr) {
    if (!dateStr) return '\u2014';
    return new Date(dateStr).toLocaleDateString(dateLocaleTag(), {
        day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
}

export function formatPrice(price) {
    // Delegate numbers to formatAmount for the one canonical format (whole
    // euros without decimals, fractional amounts with exactly two), so a price
    // renders identically wherever it appears; non-numbers (e.g. legacy strings
    // like "\u20AC120") pass through unchanged.
    if (typeof price === 'number') return formatAmount(price);
    return price;
}

export function formatPricePerPerson(price) {
    const base = formatPrice(price);
    if (!base) return base;
    return `${base} / person`;
}

export function hasGroupMin(activity) {
    return Number(activity?.minPrice) > 0;
}

export function groupMinNote(activity) {
    if (!hasGroupMin(activity)) return null;
    return `Group minimum ${formatAmount(Number(activity.minPrice))}`;
}

export function capitalizeFirst(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1);
}

export function truncateText(text, limit = 60) {
    if (!text || text.length <= limit) return text;
    return text.substring(0, limit) + '...';
}

export const DEFAULT_ACTIVITY_IMAGE = 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop';
export const DEFAULT_DESTINATION_IMAGE = 'https://images.unsplash.com/photo-1541849546-216549ae216d?w=400&h=300&fit=crop';

export const STATUS_VARIANTS = {
    PAID: 'success',
    CONFIRMED: 'info',
    PENDING: 'warning',
    CANCELLED: 'danger',
};
