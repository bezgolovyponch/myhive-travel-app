package com.myhive.backend.service;

import com.myhive.backend.dto.ImageRecompressResult;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
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

    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    public String uploadImage(MultipartFile file) throws IOException {
        byte[] originalBytes = file.getBytes();
        BufferedImage decoded = decodeImage(originalBytes);
        if (decoded == null) {
            // A format ImageIO cannot decode (e.g. SVG) — store the original untouched.
            String key = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
            putObject(key, file.getContentType(), originalBytes);
            return publicUrl + "/" + key;
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
            BufferedImage decoded = decodeImage(stored);
            if (decoded == null) {
                return null; // not a decodable image (e.g. SVG) — leave untouched
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
     * Decodes via Thumbnailator (not raw ImageIO) so EXIF orientation is applied — portrait phone
     * photos keep their rotation. Returns null for formats it cannot decode (e.g. SVG).
     */
    private static BufferedImage decodeImage(byte[] bytes) {
        try {
            return Thumbnails.of(new ByteArrayInputStream(bytes)).scale(1.0).asBufferedImage();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] compressForWeb(BufferedImage source) throws IOException {
        BufferedImage rgb = flattenToRgb(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(rgb)
                .width(Math.min(rgb.getWidth(), MAX_WIDTH))
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
