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
            @RequestParam(required = false) String category) {

        if (destinationId != null && category != null) {
            return ResponseEntity.ok(activityService.getActivitiesByDestinationAndCategory(destinationId, category));
        } else if (destinationId != null) {
            return ResponseEntity.ok(activityService.getActivitiesByDestination(destinationId));
        } else if (category != null) {
            return ResponseEntity.ok(activityService.getActivitiesByCategory(category));
        } else {
            return ResponseEntity.ok(activityService.getAllActivities());
        }
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<ActivityDTO>> getActivitiesPaged(
            @RequestParam UUID destinationId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        int safeSize = Math.min(size, 50);
        PageRequest pageRequest = PageRequest.of(page, safeSize, Sort.by("name").ascending());

        if (category != null && !category.isEmpty()) {
            return ResponseEntity.ok(activityService.getActivitiesByDestinationAndCategoryPaged(destinationId, category, pageRequest));
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
