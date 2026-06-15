import {copyToClipboard} from './clipboard';

describe('copyToClipboard', () => {
    const originalClipboard = navigator.clipboard;

    afterEach(() => {
        Object.defineProperty(navigator, 'clipboard', {
            value: originalClipboard,
            configurable: true,
            writable: true
        });
    });

    function setClipboard(value) {
        Object.defineProperty(navigator, 'clipboard', {
            value,
            configurable: true,
            writable: true
        });
    }

    it('resolves true and writes the text on success', async () => {
        const writeText = jest.fn().mockResolvedValue(undefined);
        setClipboard({writeText});

        const result = await copyToClipboard('https://trivlu.com/share');

        expect(result).toBe(true);
        expect(writeText).toHaveBeenCalledWith('https://trivlu.com/share');
    });

    it('resolves false when the clipboard API is unavailable', async () => {
        setClipboard(undefined);

        const result = await copyToClipboard('https://trivlu.com/share');

        expect(result).toBe(false);
    });

    it('resolves false when the write is rejected', async () => {
        const writeText = jest.fn().mockRejectedValue(new Error('denied'));
        setClipboard({writeText});

        const result = await copyToClipboard('https://trivlu.com/share');

        expect(result).toBe(false);
    });
});
