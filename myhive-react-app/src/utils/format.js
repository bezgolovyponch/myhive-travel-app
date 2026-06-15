export function formatAmount(amount) {
    if (amount == null) return '\u2014';
    const n = Number(amount);
    // Non-numeric input (e.g. a NaN from a bad value) renders the em-dash
    // rather than a literal "\u20ACNaN".
    if (!Number.isFinite(n)) return '\u2014';
    // Cents only when present: whole euros render clean (\u20AC45), fractional
    // amounts keep exactly two decimals (\u20AC40.50). Never one decimal.
    return Number.isInteger(n) ? `\u20AC${n}` : `\u20AC${n.toFixed(2)}`;
}

export function formatDate(dateStr) {
    if (!dateStr) return '\u2014';
    return new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00')).toLocaleDateString('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric',
    });
}

export function formatDateTime(dateStr) {
    if (!dateStr) return '\u2014';
    return new Date(dateStr).toLocaleDateString('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
}

export function formatPrice(price) {
    // Delegate numbers to formatAmount so a price renders identically
    // (two decimals) wherever it appears; non-numbers (e.g. legacy strings
    // like "\u20AC120") pass through unchanged.
    if (typeof price === 'number') return formatAmount(price);
    return price;
}

export function formatPricePerPerson(price) {
    const base = formatPrice(price);
    if (!base) return base;
    return `${base} / person`;
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
