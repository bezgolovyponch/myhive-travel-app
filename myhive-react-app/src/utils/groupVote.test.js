import {toE164} from './groupVote';

describe('toE164', () => {
    test.each([
        ['+44', '07700 900-123', '+447700900123'],
        ['+44', '7700900123', '+447700900123'],
        ['+420', '602 123 456', '+420602123456'],
        ['+353', '087 123 4567', '+353871234567'],
        ['+49', '0151 23456789', '+4915123456789'],
        ['+49', '0171 2345678', '+491712345678'],
        ['+1', '(415) 555-0132', '+14155550132'],
    ])('%s %s is a whole number', (code, raw, expected) => {
        expect(toE164(code, raw)).toBe(expected);
    });

    test.each([
        ['+420', '6085940'],       // two digits short
        ['+420', '6021234567'],    // one too many
        ['+44', '7700 900'],
        ['+44', '77009001234'],
        ['+353', '8712345'],
        ['+49', '15123456'],
        ['+1', '415555013'],
        ['+44', ''],
        ['+44', 'call me'],
    ])('%s %s is not a number', (code, raw) => {
        expect(toE164(code, raw)).toBeNull();
    });

    test('a country the picker does not list takes any plausible length', () => {
        expect(toE164('+33', '612345678')).toBe('+33612345678');
        expect(toE164('+33', '61234')).toBeNull();
    });
});
