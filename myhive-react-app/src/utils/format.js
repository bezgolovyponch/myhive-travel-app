export function formatAmount(amount) {
    if (amount == null) return '\u2014';
    return `\u20AC${Number(amount).toFixed(2)}`;
}

export function formatDate(dateStr) {
    if (!dateStr) return '\u2014';
    return new Date(dateStr).toLocaleDateString('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric',
    });
}

export const STATUS_VARIANTS = {
    PAID: 'success',
    CONFIRMED: 'info',
    PENDING: 'warning',
    CANCELLED: 'danger',
};
