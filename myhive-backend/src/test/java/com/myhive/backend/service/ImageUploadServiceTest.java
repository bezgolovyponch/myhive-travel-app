package com.myhive.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myhive.backend.dto.ImageRecompressResult;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @Mock
    private S3Client s3Client;

    private ImageUploadService imageUploadService;

    @BeforeEach
    void setUp() {
        imageUploadService = new ImageUploadService(s3Client);
        ReflectionTestUtils.setField(imageUploadService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(imageUploadService, "publicUrl", "https://img.test");
    }

    private static byte[] jpegBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] pngWithAlphaBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private PutObjectRequest capturedPut() {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        return captor.getValue();
    }

    private byte[] capturedBody() {
        ArgumentCaptor<RequestBody> captor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(any(PutObjectRequest.class), captor.capture());
        try {
            return captor.getValue().contentStreamProvider().newStream().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void uploadImage_downscalesOversizedImageTo1600AndJpeg() throws IOException {
        int expectedMaxWidth = 1600;
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", jpegBytes(3200, 2000));

        String url = imageUploadService.uploadImage(file);

        assertThat(url).startsWith("https://img.test/").endsWith(".jpg");
        PutObjectRequest put = capturedPut();
        assertThat(put.contentType()).isEqualTo("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(capturedBody()));
        assertThat(stored.getWidth()).isEqualTo(expectedMaxWidth);
        assertThat(stored.getHeight()).isEqualTo(1000); // aspect ratio preserved
    }

    @Test
    void uploadImage_neverUpscalesSmallImages() throws IOException {
        int expectedWidth = 800;
        MockMultipartFile file = new MockMultipartFile("file", "small.jpg", "image/jpeg", jpegBytes(expectedWidth, 600));

        imageUploadService.uploadImage(file);

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(capturedBody()));
        assertThat(stored.getWidth()).isEqualTo(expectedWidth);
    }

    @Test
    void uploadImage_flattensTransparentPngToJpeg() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", pngWithAlphaBytes(400, 300));

        String url = imageUploadService.uploadImage(file);

        assertThat(url).endsWith(".jpg");
        PutObjectRequest put = capturedPut();
        assertThat(put.contentType()).isEqualTo("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(capturedBody()));
        assertThat(stored).isNotNull();
    }

    @Test
    void uploadImage_handlesLargePhotosViaSubsampledDecode() throws IOException {
        // A ~24MP DSLR photo must be processable within the small prod heap: the decode is
        // subsampled (bounded raster) and the output still lands in the 1600px box.
        MockMultipartFile file = new MockMultipartFile("file", "dslr.jpg", "image/jpeg", jpegBytes(5100, 4800));

        String url = imageUploadService.uploadImage(file);

        assertThat(url).endsWith(".jpg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(capturedBody()));
        assertThat(stored.getWidth()).isEqualTo(1600);
    }

    @Test
    void uploadImage_storesUndecodableFilesUntouched() throws IOException {
        byte[] expectedBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "icon.svg", "image/svg+xml", expectedBytes);

        String url = imageUploadService.uploadImage(file);

        assertThat(url).endsWith(".svg");
        PutObjectRequest put = capturedPut();
        assertThat(put.contentType()).isEqualTo("image/svg+xml");
        assertThat(capturedBody()).isEqualTo(expectedBytes);
    }

    @Test
    void recompressExistingImages_recompressesOnlyOversizedDecodableObjects() throws IOException {
        // One oversized image (fake 2MB listing size), one already-small object, one undecodable.
        S3Object big = S3Object.builder().key("big.jpg").size(2_000_000L).build();
        S3Object small = S3Object.builder().key("small.jpg").size(50_000L).build();
        S3Object svg = S3Object.builder().key("icon.svg").size(500_000L).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of(big, small, svg)).isTruncated(false).build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), jpegBytes(3200, 2000)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "not an image".getBytes()));

        ImageRecompressResult result = imageUploadService.recompressExistingImages(25);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
        PutObjectRequest put = capturedPut();
        assertThat(put.key()).isEqualTo("big.jpg"); // same key — the stored URL keeps working
        assertThat(put.contentType()).isEqualTo("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(capturedBody()));
        assertThat(stored.getWidth()).isEqualTo(1600);
    }

    @Test
    void recompressExistingImages_stopsAtLimitAndReportsTruncated() throws IOException {
        // Random noise compresses poorly, so every listed object is "eligible"; limit=1 must stop after one.
        S3Object first = S3Object.builder().key("a.jpg").size(2_000_000L).build();
        S3Object second = S3Object.builder().key("b.jpg").size(2_000_000L).build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of(first, second)).isTruncated(false).build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), jpegBytes(2000, 1500)));

        ImageRecompressResult result = imageUploadService.recompressExistingImages(1);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void recompressExistingImages_skipsWhenRecompressionWouldNotShrinkTheObject() throws IOException {
        // Random noise is incompressible: a 1600x1200 noise JPEG re-encodes to several hundred KB,
        // reliably MORE than the just-above-threshold size we report for it — so it must be skipped
        // (rewriting an object with a bigger body would defeat the point).
        BufferedImage noise = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int x = 0; x < noise.getWidth(); x++) {
            for (int y = 0; y < noise.getHeight(); y++) {
                noise.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noise, "jpg", out);
        long reportedSizeJustAboveThreshold = 310_000L;
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(List.of(S3Object.builder().key("noise.jpg").size(reportedSizeJustAboveThreshold).build()))
                        .isTruncated(false).build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), out.toByteArray()));

        ImageRecompressResult result = imageUploadService.recompressExistingImages(25);

        assertThat(result.processed()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(s3Client, org.mockito.Mockito.never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
