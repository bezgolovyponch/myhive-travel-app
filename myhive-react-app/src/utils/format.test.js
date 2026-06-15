import {formatPrice, formatAmount, formatPricePerPerson} from './format';

describe('formatPrice', () => {
    it('renders numbers with the same two-decimal shape as formatAmount', () => {
        const expected = formatAmount(12.5);
        expect(formatPrice(12.5)).toBe(expected);
        expect(formatPrice(12.5)).toBe('€12.50');
    });

    it('formats whole numbers with two decimals', () => {
        expect(formatPrice(45)).toBe('€45.00');
    });

    it('passes legacy string prices through unchanged', () => {
        expect(formatPrice('€120')).toBe('€120');
    });

    it('returns nullish input as-is rather than an em-dash', () => {
        expect(formatPrice(null)).toBeNull();
        expect(formatPrice(undefined)).toBeUndefined();
    });
});

describe('formatPricePerPerson', () => {
    it('appends the per-person suffix to the formatted price', () => {
        expect(formatPricePerPerson(45)).toBe('€45.00 / person');
    });
});
