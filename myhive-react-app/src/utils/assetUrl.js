// Normalizes an imported image asset to a URL usable as an <img src>.
//
// The two bundlers that build this code disagree on what an image import is:
// CRA's webpack resolves `import img from './x.jpg'` to a URL string, while
// Next.js resolves it to a static-image object ({src, width, height, ...}).
// Rendering the object straight into src produced <img src="[object Object]">
// and a 404 for /[object%20Object] on the SSR pages.
export function assetUrl(asset) {
    if (!asset) {
        return '';
    }
    if (typeof asset === 'string') {
        return asset;
    }
    return asset.src ?? '';
}
