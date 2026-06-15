import {required, slugFormat, discountRange} from './validators';

describe('required', () => {
    it('returns a message for null/undefined/empty/whitespace', () => {
        expect(required(null)).toBeTruthy();
        expect(required(undefined)).toBeTruthy();
        expect(required('')).toBeTruthy();
        expect(required('   ')).toBeTruthy();
    });

    it('returns undefined for non-empty values, including 0 and "0"', () => {
        expect(required('Bali')).toBeUndefined();
        expect(required('0')).toBeUndefined();
        expect(required(0)).toBeUndefined();
    });

    it('supports a custom message', () => {
        expect(required('', 'Name is required.')).toBe('Name is required.');
    });
});

describe('slugFormat', () => {
    it('returns undefined when blank (slug is optional)', () => {
        expect(slugFormat('')).toBeUndefined();
        expect(slugFormat(null)).toBeUndefined();
        expect(slugFormat(undefined)).toBeUndefined();
    });

    it('accepts lowercase alphanumeric with hyphens', () => {
        expect(slugFormat('bali-beach-2')).toBeUndefined();
    });

    it('rejects spaces, uppercase and other characters', () => {
        expect(slugFormat('Bali Beach')).toBeTruthy();
        expect(slugFormat('Bali')).toBeTruthy();
        expect(slugFormat('bali_beach')).toBeTruthy();
    });
});

describe('discountRange', () => {
    it('accepts 0 through 100 inclusive (and decimals)', () => {
        expect(discountRange(0)).toBeUndefined();
        expect(discountRange('0')).toBeUndefined();
        expect(discountRange(100)).toBeUndefined();
        expect(discountRange(15.5)).toBeUndefined();
    });

    it('rejects out-of-range and non-numeric values', () => {
        expect(discountRange(-1)).toBeTruthy();
        expect(discountRange(101)).toBeTruthy();
        expect(discountRange('abc')).toBeTruthy();
        expect(discountRange(Infinity)).toBeTruthy();
        expect(discountRange(-Infinity)).toBeTruthy();
    });
});
