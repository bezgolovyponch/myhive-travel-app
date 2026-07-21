import {formatPrice, formatAmount, formatPricePerPerson, hasGroupMin, groupMinNote} from './format';

describe('formatPrice', () => {
    it('renders numbers with the same two-decimal shape as formatAmount', () => {
        const expected = formatAmount(12.5);
        expect(formatPrice(12.5)).toBe(expected);
        expect(formatPrice(12.5)).toBe('€12.50');
    });

    it('renders whole numbers without decimals', () => {
        expect(formatPrice(45)).toBe('€45');
    });

    it('passes legacy string prices through unchanged', () => {
        expect(formatPrice('€120')).toBe('€120');
    });

    it('returns nullish input as-is rather than an em-dash', () => {
        expect(formatPrice(null)).toBeNull();
        expect(formatPrice(undefined)).toBeUndefined();
    });
});

describe('formatAmount', () => {
    it('renders whole numbers without decimals', () => {
        expect(formatAmount(45)).toBe('€45');
    });

    it('keeps two decimals for fractional amounts and never one', () => {
        expect(formatAmount(45.5)).toBe('€45.50');
        expect(formatAmount(40.1)).toBe('€40.10');
    });

    it('applies the same rule to numeric strings', () => {
        expect(formatAmount('45')).toBe('€45');
        expect(formatAmount('40.5')).toBe('€40.50');
    });

    it('renders an em-dash for nullish input', () => {
        expect(formatAmount(null)).toBe('—');
        expect(formatAmount(undefined)).toBe('—');
    });

    it('renders an em-dash for non-numeric input instead of €NaN', () => {
        expect(formatAmount('abc')).toBe('—');
        expect(formatAmount(NaN)).toBe('—');
    });
});

describe('formatPricePerPerson', () => {
    it('appends the per-person suffix to the formatted price', () => {
        expect(formatPricePerPerson(45)).toBe('€45 / person');
    });

    it('appends the suffix to a fractional price', () => {
        expect(formatPricePerPerson(12.5)).toBe('€12.50 / person');
    });

    it('appends the suffix to a legacy string price', () => {
        expect(formatPricePerPerson('€120')).toBe('€120 / person');
    });

    it('returns nullish input as-is without the suffix', () => {
        expect(formatPricePerPerson(null)).toBeNull();
        expect(formatPricePerPerson(undefined)).toBeUndefined();
    });
});

describe('group minimum helpers', () => {
    test('hasGroupMin true only for a positive minPrice', () => {
        expect(hasGroupMin({minPrice: 300})).toBe(true);
        expect(hasGroupMin({minPrice: 0})).toBe(false);
        expect(hasGroupMin({minPrice: null})).toBe(false);
        expect(hasGroupMin({})).toBe(false);
        expect(hasGroupMin(undefined)).toBe(false);
    });

    test('groupMinNote renders the canonical copy', () => {
        expect(groupMinNote({minPrice: 300})).toBe('Group minimum €300');
        expect(groupMinNote({minPrice: 299.5})).toBe('Group minimum €299.50');
        expect(groupMinNote({minPrice: 0})).toBeNull();
    });
});
