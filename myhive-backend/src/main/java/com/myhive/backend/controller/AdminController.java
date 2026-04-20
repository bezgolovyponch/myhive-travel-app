package com.myhive.backend.controller;

import com.myhive.backend.dto.*;
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.service.*;
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
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDTO>> getAllActivities() {
        return ResponseEntity.ok(activityService.getAllActivities());
    }

    @GetMapping("/activities/paged")
    public ResponseEntity<Page<ActivityDTO>> getActivitiesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 50), Sort.by("name").ascending());
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
    public ResponseEntity<byte[]> exportActivities() {
        String csv = activityCsvExporter.exportAll();
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        String filename = "activities-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
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
}
