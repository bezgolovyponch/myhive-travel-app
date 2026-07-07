package com.myhive.backend.controller;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.dto.AdminPaymentLinkRequest;
import com.myhive.backend.dto.AdminPaymentLinkResponse;
import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.CategoryUsageDTO;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.dto.QuizDTO;
import com.myhive.backend.service.ActivityService;
import com.myhive.backend.service.BlogPostService;
import com.myhive.backend.service.BookingService;
import com.myhive.backend.service.CategoryService;
import com.myhive.backend.service.DestinationService;
import com.myhive.backend.service.ImageUploadService;
import com.myhive.backend.service.PackageService;
import com.myhive.backend.service.PaymentService;
import com.myhive.backend.service.QuizService;
import com.myhive.backend.service.activity.ActivityCsvExporter;
import com.myhive.backend.service.activity.ActivityCsvImporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final BookingService bookingService;
    private final ActivityService activityService;
    private final BlogPostService blogPostService;
    private final DestinationService destinationService;
    private final CategoryService categoryService;
    private final PackageService packageService;
    private final PaymentService paymentService;
    private final QuizService quizService;
    private final Optional<ImageUploadService> imageUploadService;
    private final ActivityCsvExporter activityCsvExporter;
    private final ActivityCsvImporter activityCsvImporter;

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingDTO>> getAllBookings() {
        List<BookingDTO> bookings = bookingService.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingDTO> getBookingById(@PathVariable UUID id) {
        BookingDTO booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(booking);
    }

    @GetMapping("/bookings/stats")
    public ResponseEntity<BookingStatsDTO> getBookingStats() {
        return ResponseEntity.ok(bookingService.getBookingStats());
    }

    @PostMapping("/bookings/{id}/payment-link")
    public ResponseEntity<AdminPaymentLinkResponse> createBookingPaymentLink(
            @PathVariable UUID id,
            @Valid @RequestBody AdminPaymentLinkRequest request) {
        AdminPaymentLinkResponse response = paymentService.createAdminPaymentLink(id, request.amountCents());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDTO>> getAllActivities() {
        return ResponseEntity.ok(activityService.getAllActivities());
    }

    @GetMapping("/activities/paged")
    public ResponseEntity<Page<ActivityDTO>> getActivitiesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID destinationId) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
        if (destinationId != null) {
            return ResponseEntity.ok(activityService.getActivitiesByDestinationPaged(destinationId, pageRequest));
        }
        return ResponseEntity.ok(activityService.getActivitiesPaged(pageRequest));
    }

    @PostMapping("/activities")
    public ResponseEntity<ActivityDTO> createActivity(@Valid @RequestBody ActivityDTO activityDTO) {
        ActivityDTO created = activityService.createActivity(activityDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/activities/{id}")
    public ResponseEntity<ActivityDTO> updateActivity(@PathVariable UUID id, @Valid @RequestBody ActivityDTO activityDTO) {
        ActivityDTO updated = activityService.updateActivity(id, activityDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/activities/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable UUID id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/activities/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportActivities(
            @RequestParam(required = false) UUID destinationId) {
        String csv = destinationId == null
                ? activityCsvExporter.exportAll()
                : activityCsvExporter.exportByDestination(destinationId);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        String filename = buildExportFilename(destinationId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private String buildExportFilename(UUID destinationId) {
        String date = LocalDate.now().toString();
        if (destinationId == null) {
            return "activities-" + date + ".csv";
        }
        DestinationDTO dest = destinationService.getDestinationById(destinationId);
        return "activities-" + dest.getSlug() + "-" + date + ".csv";
    }

    @PostMapping("/activities/import/preview")
    public ResponseEntity<ActivityImportPreviewDTO> previewActivityImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        byte[] bytes = file == null ? new byte[0] : file.getBytes();
        return ResponseEntity.ok(activityCsvImporter.preview(bytes));
    }

    @PostMapping("/activities/import/apply")
    public ResponseEntity<ActivityImportResultDTO> applyActivityImport(
            @Valid @RequestBody ActivityImportApplyRequest request) {
        return ResponseEntity.ok(activityCsvImporter.apply(request));
    }

    @GetMapping("/destinations")
    public ResponseEntity<List<DestinationDTO>> getAllDestinations() {
        return ResponseEntity.ok(destinationService.getAllDestinations());
    }

    @GetMapping("/destinations/paged")
    public ResponseEntity<Page<DestinationDTO>> getDestinationsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
        return ResponseEntity.ok(destinationService.getDestinationsPaged(pageRequest));
    }

    @PostMapping("/destinations")
    public ResponseEntity<DestinationDTO> createDestination(@Valid @RequestBody DestinationDTO destinationDTO) {
        DestinationDTO created = destinationService.createDestination(destinationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/destinations/{id}")
    public ResponseEntity<DestinationDTO> updateDestination(@PathVariable UUID id, @Valid @RequestBody DestinationDTO destinationDTO) {
        DestinationDTO updated = destinationService.updateDestination(id, destinationDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/destinations/{id}")
    public ResponseEntity<Void> deleteDestination(@PathVariable UUID id) {
        destinationService.deleteDestination(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/destinations/{id}/categories")
    public ResponseEntity<Void> updateDestinationCategories(
            @PathVariable UUID id,
            @RequestBody List<UUID> categoryIds) {
        destinationService.updateDestinationCategories(id, categoryIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/categories/paged")
    public ResponseEntity<Page<CategoryDTO>> getCategoriesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
        return ResponseEntity.ok(categoryService.getCategoriesPaged(pageRequest));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        CategoryDTO created = categoryService.createCategory(categoryDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryDTO categoryDTO) {
        CategoryDTO updated = categoryService.updateCategory(id, categoryDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories/{id}/usage")
    public ResponseEntity<CategoryUsageDTO> getCategoryUsage(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getCategoryUsage(id));
    }

    @GetMapping("/blog")
    public ResponseEntity<List<BlogPostDTO>> getAllBlogPosts() {
        return ResponseEntity.ok(blogPostService.getAllBlogPosts());
    }

    @GetMapping("/blog/paged")
    public ResponseEntity<Page<BlogPostDTO>> getBlogPostsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("date").descending());
        return ResponseEntity.ok(blogPostService.getBlogPostsPaged(pageRequest));
    }

    @PostMapping("/blog")
    public ResponseEntity<BlogPostDTO> createBlogPost(@Valid @RequestBody BlogPostDTO blogPostDTO) {
        BlogPostDTO created = blogPostService.createBlogPost(blogPostDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/blog/{id}")
    public ResponseEntity<BlogPostDTO> updateBlogPost(@PathVariable UUID id, @Valid @RequestBody BlogPostDTO blogPostDTO) {
        BlogPostDTO updated = blogPostService.updateBlogPost(id, blogPostDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/blog/{id}")
    public ResponseEntity<Void> deleteBlogPost(@PathVariable UUID id) {
        blogPostService.deleteBlogPost(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/packages")
    public ResponseEntity<List<PackageDTO>> getAllPackages() {
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @GetMapping("/packages/paged")
    public ResponseEntity<Page<PackageDTO>> getPackagesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
        return ResponseEntity.ok(packageService.getPackagesPaged(pageRequest));
    }

    @PostMapping("/packages")
    public ResponseEntity<PackageDTO> createPackage(@Valid @RequestBody PackageDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(packageService.createPackage(dto));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<PackageDTO> updatePackage(@PathVariable UUID id, @Valid @RequestBody PackageDTO dto) {
        return ResponseEntity.ok(packageService.updatePackage(id, dto));
    }

    @DeleteMapping("/packages/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable UUID id) {
        packageService.deletePackage(id);
        return ResponseEntity.noContent().build();
    }

    @Value("${app.upload.max-file-size}")
    private long maxFileSize;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (imageUploadService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Image upload is not configured"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }
        if (file.getSize() > maxFileSize) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File size exceeds the maximum allowed size of " + (maxFileSize / 1024 / 1024) + "MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only image files are allowed"));
        }
        String url = imageUploadService.get().uploadImage(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * One-off maintenance: recompress oversized images already in the R2 bucket in place (same keys,
     * URLs keep working). Batched — repeat the call while the response reports {@code truncated=true}.
     */
    @PostMapping("/images/recompress")
    public ResponseEntity<?> recompressImages(@RequestParam(defaultValue = "25") int limit) {
        if (imageUploadService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Image upload is not configured"));
        }
        return ResponseEntity.ok(imageUploadService.get().recompressExistingImages(limit));
    }

    @GetMapping("/destinations/{destinationId}/quiz")
    public ResponseEntity<QuizDTO> getQuiz(@PathVariable UUID destinationId) {
        return ResponseEntity.ok(quizService.getQuiz(destinationId));
    }

    @PutMapping("/destinations/{destinationId}/quiz")
    public ResponseEntity<QuizDTO> replaceQuiz(@PathVariable UUID destinationId,
                                               @Valid @RequestBody QuizDTO quizDTO) {
        return ResponseEntity.ok(quizService.replaceQuiz(destinationId, quizDTO));
    }
}
