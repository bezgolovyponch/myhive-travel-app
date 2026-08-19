import {assetUrl} from './assetUrl';

describe('assetUrl', () => {
    it('passes through a plain URL string (CRA webpack image import)', () => {
        expect(assetUrl('/static/media/step-pick.abc123.jpg')).toBe('/static/media/step-pick.abc123.jpg');
    });

    it('unwraps a static-image object (Next.js image import)', () => {
        // Next resolves `import img from './x.jpg'` to an object, not a string —
        // rendering it straight into src produced <img src="[object Object]">
        // and a 404 for /[object%20Object].
        expect(assetUrl({src: '/_next/static/media/step-pick.abc123.jpg', width: 800, height: 600}))
            .toBe('/_next/static/media/step-pick.abc123.jpg');
    });

    it('never yields the string "[object Object]"', () => {
        expect(assetUrl({src: '/x.png'})).not.toContain('object Object');
        expect(assetUrl({})).not.toContain('object Object');
    });

    it('returns an empty string for missing or unusable input', () => {
        expect(assetUrl(undefined)).toBe('');
        expect(assetUrl(null)).toBe('');
        expect(assetUrl({})).toBe('');
    });
});
