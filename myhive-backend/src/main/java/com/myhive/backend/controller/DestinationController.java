package com.myhive.backend.controller;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// `locale` (en/de/…) localizes the translatable fields in place; the response
// shape is unchanged. Absent → raw view with the translations map (admin use).
@RestController
@RequestMapping("/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping
    public ResponseEntity<List<DestinationDTO>> getAllDestinations(
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(destinationService.getAllDestinations(locale));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationDTO> getDestinationById(
            @PathVariable UUID id,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(destinationService.getDestinationById(id, locale));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<DestinationDTO> getDestinationBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(destinationService.getDestinationBySlug(slug, locale));
    }

    @GetMapping("/{id}/categories")
    public ResponseEntity<List<CategoryDTO>> getCategoriesForDestination(
            @PathVariable UUID id,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(destinationService.getCategoriesForDestination(id, locale));
    }
}
