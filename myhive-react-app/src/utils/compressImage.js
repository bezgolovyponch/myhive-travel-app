/**
 * Downscale/recompress a photo in the browser before uploading, so mobile
 * uploads don't push 5-10MB camera originals over the network. The server
 * still recompresses, so any failure here safely falls back to the original.
 */
export async function compressImage(file, {maxDim = 1920, quality = 0.82} = {}) {
    // Skip non-images and gifs (canvas would drop the animation).
    if (!file.type.startsWith('image/') || file.type === 'image/gif' || file.type === 'image/svg+xml') {
        return file;
    }
    try {
        // 'from-image' applies EXIF rotation, matching how the photo looks on the phone.
        const bitmap = await createImageBitmap(file, {imageOrientation: 'from-image'});
        const scale = Math.min(1, maxDim / Math.max(bitmap.width, bitmap.height));
        const width = Math.max(1, Math.round(bitmap.width * scale));
        const height = Math.max(1, Math.round(bitmap.height * scale));

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        canvas.getContext('2d').drawImage(bitmap, 0, 0, width, height);
        bitmap.close();

        const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
        if (!blob || blob.size >= file.size) return file;

        const name = file.name.replace(/\.[^.]*$/, '') + '.jpg';
        return new File([blob], name, {type: 'image/jpeg'});
    } catch {
        return file;
    }
}
