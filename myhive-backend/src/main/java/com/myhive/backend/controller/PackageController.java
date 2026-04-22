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

    @GetMapping
    public ResponseEntity<List<PackageDTO>> getAllPackages(
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) String categorySlug) {
        if (destinationId != null && categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestinationAndCategorySlug(destinationId, categorySlug));
        } else if (destinationId != null) {
            return ResponseEntity.ok(packageService.getPackagesByDestination(destinationId));
        } else if (categorySlug != null) {
            return ResponseEntity.ok(packageService.getPackagesByCategorySlug(categorySlug));
        }
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PackageDTO> getPackageById(@PathVariable UUID id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<PackageDTO> getPackageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(packageService.getPackageBySlug(slug));
    }
}
