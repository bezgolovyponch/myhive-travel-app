package com.myhive.backend.controller;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// `locale` (en/de/…) localizes the name in place; the response shape is
// unchanged. Absent → raw view with the translations map (admin use).
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories(
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(categoryService.getAllCategories(locale));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategoryById(
            @PathVariable UUID id,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(categoryService.getCategoryById(id, locale));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<CategoryDTO> getCategoryBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(categoryService.getCategoryBySlug(slug, locale));
    }
}
