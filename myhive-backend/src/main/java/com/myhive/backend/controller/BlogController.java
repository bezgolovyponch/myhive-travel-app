package com.myhive.backend.controller;

import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.service.BlogPostService;
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
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogPostService blogPostService;

    @GetMapping
    public ResponseEntity<List<BlogPostDTO>> getAllBlogPosts(
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(blogPostService.getAllBlogPosts(locale));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPostDTO> getBlogPostById(
            @PathVariable UUID id,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(blogPostService.getBlogPostById(id, locale));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BlogPostDTO> getBlogPostBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String locale) {
        return ResponseEntity.ok(blogPostService.getBlogPostBySlug(slug, locale));
    }
}
