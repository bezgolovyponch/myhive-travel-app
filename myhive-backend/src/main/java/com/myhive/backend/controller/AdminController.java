package com.myhive.backend.controller;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.dto.BookingDTO;
import com.myhive.backend.dto.BookingStatsDTO;
import com.myhive.backend.service.ActivityService;
import com.myhive.backend.service.BlogPostService;
import com.myhive.backend.service.BookingService;
import com.myhive.backend.service.ImageUploadService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final BookingService bookingService;
    private final ActivityService activityService;
    private final BlogPostService blogPostService;
    private final ImageUploadService imageUploadService;

    public AdminController(BookingService bookingService,
                           ActivityService activityService,
                           BlogPostService blogPostService,
                           @Nullable ImageUploadService imageUploadService) {
        this.bookingService = bookingService;
        this.activityService = activityService;
        this.blogPostService = blogPostService;
        this.imageUploadService = imageUploadService;
    }

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

    @GetMapping("/blog")
    public ResponseEntity<List<BlogPostDTO>> getAllBlogPosts() {
        return ResponseEntity.ok(blogPostService.getAllBlogPosts());
    }

    @PostMapping("/blog")
    public ResponseEntity<BlogPostDTO> createBlogPost(@RequestBody BlogPostDTO blogPostDTO) {
        BlogPostDTO created = blogPostService.createBlogPost(blogPostDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/blog/{id}")
    public ResponseEntity<BlogPostDTO> updateBlogPost(@PathVariable UUID id, @RequestBody BlogPostDTO blogPostDTO) {
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
        if (imageUploadService == null) {
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
        String url = imageUploadService.uploadImage(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
