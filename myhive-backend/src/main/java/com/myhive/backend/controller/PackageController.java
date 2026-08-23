package com.myhive.backend.controller;

import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    // `locale` (en/de/…) localizes the translatable fields in place; the
    // response shape is unchanged. Absent → raw view with the translations map
    // (admin use).

    @GetMapping
    public ResponseEntity<List<PackageDTO>> getAllPackages(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String locale) {
        if (destinationId != null && categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestinationAndCategorySlug(destinationId, categorySlug, locale));
        } else if (destinationId != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestination(destinationId, locale));
        } else if (categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByCategorySlug(categorySlug, locale));
        }
        return ResponseEntity.ok(packageService.getAllPackages(locale));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackageDTO> getPackageById(
            @PathVariable UUID id,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(packageService.getPackageById(id, locale));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<PackageDTO> getPackageBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(packageService.getPackageBySlug(slug, locale));
    }
}
