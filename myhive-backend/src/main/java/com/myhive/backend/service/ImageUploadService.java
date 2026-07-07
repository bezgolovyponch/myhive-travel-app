package com.myhive.backend.service;

import com.myhive.backend.dto.ImageRecompressResult;
import com.myhive.backend.exception.BadRequestException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.util.exif.ExifFilterUtils;
import net.coobird.thumbnailator.util.exif.ExifUtils;
import net.coobird.thumbnailator.util.exif.Orientation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
@ConditionalOnBean(S3Client.class)
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    /** Card/detail images never render wider than this — larger originals are downscaled to it. */
    private static final int MAX_WIDTH = 1600;

    private static final float JPEG_QUALITY = 0.8f;

    /** Objects at or below this size are already cheap to serve — recompression skips them. */
    private static final long RECOMPRESS_THRESHOLD_BYTES = 300L * 1024L;

    /**
     * The decode is subsampled so its raster never exceeds roughly this many pixels on the longest
     * side (≈{@code 3200²×4B ≈ 41MB} worst case) — memory stays bounded no matter how many
     * megapixels the source has. 2× the output size keeps enough detail for the final resize.
     */
    private static final int DECODE_BOUND = 2 * MAX_WIDTH;

    /** Header-sanity guard against pathological files; ordinary camera photos are far below it. */
    private static final long MAX_PIXELS = 100_000_000L;

    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    public String uploadImage(MultipartFile file) throws IOException {
        byte[] originalBytes = file.getBytes();
        Dimension size = probeDimensions(originalBytes);
        if (size == null) {
            // A format ImageIO cannot decode (e.g. SVG) — store the original untouched.
            String key = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
            putObject(key, file.getContentType(), originalBytes);
            return publicUrl + "/" + key;
        }
        if ((long) size.width * size.height > MAX_PIXELS) {
            throw new BadRequestException("Image is too large to process — please resize it and retry");
        }
        BufferedImage decoded = decodeBounded(originalBytes);
        if (decoded == null) {
            throw new BadRequestException("Could not read the image file — please re-export it and retry");
        }
        String key = UUID.randomUUID() + ".jpg";
        putObject(key, "image/jpeg", compressForWeb(decoded));
        return publicUrl + "/" + key;
    }

    /**
     * One batch of in-place recompression over the bucket: every object larger than
     * {@value #RECOMPRESS_THRESHOLD_BYTES} bytes that decodes as an image is rewritten (same key,
     * so stored URLs keep working) as a web-sized JPEG. Batched via {@code limit} so a call stays
     * well under proxy timeouts — repeat while the result reports {@code truncated}.
     */
    public ImageRecompressResult recompressExistingImages(int limit) {
        int processed = 0;
        int skipped = 0;
        long bytesBefore = 0;
        long bytesAfter = 0;
        String continuationToken = null;
        do {
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .continuationToken(continuationToken)
                    .build());
            for (S3Object object : page.contents()) {
                if (processed >= limit) {
                    return new ImageRecompressResult(processed, skipped, bytesBefore, bytesAfter, true);
                }
                if (object.size() <= RECOMPRESS_THRESHOLD_BYTES) {
                    skipped++;
                    continue;
                }
                byte[] compressed = recompressObject(object);
                if (compressed == null) {
                    skipped++;
                    continue;
                }
                putObject(object.key(), "image/jpeg", compressed);
                processed++;
                bytesBefore += object.size();
                bytesAfter += compressed.length;
            }
            continuationToken = page.nextContinuationToken();
        } while (continuationToken != null);
        return new ImageRecompressResult(processed, skipped, bytesBefore, bytesAfter, false);
    }

    /** Returns the recompressed body, or null when the object should be skipped. */
    private byte[] recompressObject(S3Object object) {
        try {
            byte[] stored = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(object.key())
                    .build()).asByteArray();
            Dimension size = probeDimensions(stored);
            if (size == null || (long) size.width * size.height > MAX_PIXELS) {
                return null; // undecodable (e.g. SVG) or pathologically large — leave untouched
            }
            BufferedImage decoded = decodeBounded(stored);
            if (decoded == null) {
                return null; // corrupt image — leave untouched
            }
            byte[] compressed = compressForWeb(decoded);
            if (compressed.length >= object.size()) {
                return null; // rewriting with a bigger body would defeat the point
            }
            return compressed;
        } catch (Exception e) {
            // Best-effort batch: one broken object must not abort the rest of the sweep.
            log.error("Recompression failed for object {}: {}", object.key(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Header-only dimension probe — reads a few KB regardless of image size, no pixel decode.
     * Returns null for formats ImageIO cannot read (e.g. SVG).
     */
    private static Dimension probeDimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Memory-bounded decode for the 512MB prod instance: large photos are read with source
     * subsampling so the decoded raster never exceeds ~{@code DECODE_BOUND}px on the longest side
     * (≈41MB) regardless of the source's megapixels — a full 24MP decode (~96MB raster) previously
     * got the container OOM-killed. EXIF orientation is applied to the already-small image so
     * portrait phone photos keep their rotation. Returns null when the image cannot be decoded.
     */
    private static BufferedImage decodeBounded(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int longestSide = Math.max(reader.getWidth(0), reader.getHeight(0));
                int subsampling = Math.max(1, (int) Math.ceil(longestSide / (double) DECODE_BOUND));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage decoded = reader.read(0, param);
                return applyExifOrientation(reader, decoded);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage applyExifOrientation(ImageReader reader, BufferedImage decoded) {
        try {
            Orientation orientation = ExifUtils.getExifOrientation(reader, 0);
            if (orientation != null && orientation != Orientation.TOP_LEFT) {
                return ExifFilterUtils.getFilterForOrientation(orientation).apply(decoded);
            }
        } catch (Exception e) {
            // No EXIF metadata (PNG etc.) or unreadable tags — keep the image as decoded.
        }
        return decoded;
    }

    /**
     * Resize into the {@value #MAX_WIDTH}px box (never upscaling), flatten alpha AFTER the resize
     * (cheap — the image is small by then), encode as JPEG q{@value #JPEG_QUALITY}.
     */
    private static byte[] compressForWeb(BufferedImage decoded) throws IOException {
        int boundingBox = Math.min(MAX_WIDTH, Math.max(decoded.getWidth(), decoded.getHeight()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(decoded)
                .size(boundingBox, boundingBox)
                .addFilter(ImageUploadService::flattenToRgb)
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY)
                .toOutputStream(out);
        return out.toByteArray();
    }

    /** JPEG has no alpha channel — transparent regions are flattened onto white. */
    private static BufferedImage flattenToRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(source, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return rgb;
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "";
    }

    private void putObject(String key, String contentType, byte[] body) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(body));
    }
}
