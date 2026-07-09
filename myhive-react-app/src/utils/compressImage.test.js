import {compressImage} from './compressImage';

function makeFile(name, type, size) {
    const file = new File([new Uint8Array(size)], name, {type});
    return file;
}

afterEach(() => {
    jest.restoreAllMocks();
    delete global.createImageBitmap;
});

test('returns the original file for gifs (animation would be lost)', async () => {
    const gif = makeFile('anim.gif', 'image/gif', 5000);
    expect(await compressImage(gif)).toBe(gif);
});

test('returns the original file for non-images', async () => {
    const pdf = makeFile('doc.pdf', 'application/pdf', 5000);
    expect(await compressImage(pdf)).toBe(pdf);
});

test('falls back to the original file when decoding is unavailable', async () => {
    // jsdom has no createImageBitmap — the util must not throw, the server
    // recompresses anyway.
    const photo = makeFile('photo.jpg', 'image/jpeg', 5_000_000);
    expect(await compressImage(photo)).toBe(photo);
});

test('downscales large photos to a jpeg File when the canvas pipeline works', async () => {
    global.createImageBitmap = jest.fn().mockResolvedValue({width: 4000, height: 3000, close: jest.fn()});
    const drawImage = jest.fn();
    const smallBlob = new Blob([new Uint8Array(1000)], {type: 'image/jpeg'});
    const fakeCanvas = {
        getContext: () => ({drawImage}),
        toBlob: (cb) => cb(smallBlob),
    };
    const realCreateElement = document.createElement.bind(document);
    jest.spyOn(document, 'createElement').mockImplementation(
        (tag) => (tag === 'canvas' ? fakeCanvas : realCreateElement(tag)),
    );

    const photo = makeFile('photo.HEIC.jpg', 'image/jpeg', 5_000_000);
    const result = await compressImage(photo, {maxDim: 1920, quality: 0.82});

    expect(result).not.toBe(photo);
    expect(result.type).toBe('image/jpeg');
    expect(result.name).toMatch(/\.jpg$/);
    expect(result.size).toBe(1000);
    // 4000x3000 capped at 1920 on the long edge.
    expect(fakeCanvas.width).toBe(1920);
    expect(fakeCanvas.height).toBe(1440);
    expect(drawImage).toHaveBeenCalled();
});

test('keeps the original when the compressed blob is not smaller', async () => {
    global.createImageBitmap = jest.fn().mockResolvedValue({width: 800, height: 600, close: jest.fn()});
    const bigBlob = new Blob([new Uint8Array(9000)], {type: 'image/jpeg'});
    const fakeCanvas = {
        getContext: () => ({drawImage: jest.fn()}),
        toBlob: (cb) => cb(bigBlob),
    };
    const realCreateElement = document.createElement.bind(document);
    jest.spyOn(document, 'createElement').mockImplementation(
        (tag) => (tag === 'canvas' ? fakeCanvas : realCreateElement(tag)),
    );

    const photo = makeFile('small.jpg', 'image/jpeg', 4000);
    expect(await compressImage(photo)).toBe(photo);
});
