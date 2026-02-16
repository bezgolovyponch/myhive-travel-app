package com.myhive.backend.controller;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
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

    @GetMapping("/{id}")
    public ResponseEntity<ActivityDTO> getActivityById(@PathVariable UUID id) {
        return ResponseEntity.ok(activityService.getActivityById(id));
    }
}
