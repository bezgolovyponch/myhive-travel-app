package com.myhive.backend.controller;

import com.myhive.backend.dto.PrimaryCategoryDTO;
import com.myhive.backend.service.PrimaryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The ten fixed SEO hubs, in display order. `locale` localizes the name in place.
@RestController
@RequestMapping("/primary-categories")
@RequiredArgsConstructor
public class PrimaryCategoryController {

    private final PrimaryCategoryService primaryCategoryService;

    @GetMapping
    public ResponseEntity<List<PrimaryCategoryDTO>> getAll(@RequestParam(required = false) String locale) {
        return ResponseEntity.ok(primaryCategoryService.getAll(locale));
    }
}
