package com.myhive.backend.controller;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<List<ActivityDTO>> getAllActivities(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false, defaultValue = "false") boolean featured) {

        if (featured) {
            return ResponseEntity.ok(activityService.getFeaturedActivities(categorySlug));
        }
        if (destinationId != null && categorySlug != null) {
            return ResponseEntity.ok(activityService.getActivitiesByDestinationAndCategorySlug(destinationId, categorySlug));
        } else if (destinationId != null) {
            return ResponseEntity.ok(activityService.getActivitiesByDestination(destinationId));
        } else if (categorySlug != null) {
            return ResponseEntity.ok(activityService.getActivitiesByCategorySlug(categorySlug));
        } else {
            return ResponseEntity.ok(activityService.getAllActivities());
        }
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<ActivityDTO>> getActivitiesPaged(
            @RequestParam UUID destinationId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        int safeSize = Math.min(size, 50);
        PageRequest pageRequest = PageRequest.of(page, safeSize, Sort.by("name").ascending());

        if (categorySlug != null && !categorySlug.isEmpty()) {
            return ResponseEntity.ok(activityService.getActivitiesByDestinationAndCategorySlugPaged(destinationId, categorySlug, pageRequest));
        }
        return ResponseEntity.ok(activityService.getActivitiesByDestinationPaged(destinationId, pageRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityDTO> getActivityById(@PathVariable UUID id) {
        return ResponseEntity.ok(activityService.getActivityById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ActivityDTO> getActivityBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(activityService.getActivityBySlug(slug));
    }
}
