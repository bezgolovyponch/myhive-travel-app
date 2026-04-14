package com.myhive.backend.controller;

import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.service.BlogPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogPostService blogPostService;

    @GetMapping
    public ResponseEntity<List<BlogPostDTO>> getAllBlogPosts() {
        return ResponseEntity.ok(blogPostService.getAllBlogPosts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BlogPostDTO> getBlogPostById(@PathVariable UUID id) {
        return ResponseEntity.ok(blogPostService.getBlogPostById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BlogPostDTO> getBlogPostBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(blogPostService.getBlogPostBySlug(slug));
    }
}
