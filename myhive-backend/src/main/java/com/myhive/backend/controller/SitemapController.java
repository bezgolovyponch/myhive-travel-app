package com.myhive.backend.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Legacy backend sitemap, now answering 410 Gone (ТЗ T-10).
 * The Next.js frontend owns /sitemap.xml (app/sitemap.ts); a copy of this
 * endpoint's last output is kept in docs/seo/legacy-api-sitemap-2026-10-18.xml.
 */
@RestController
public class SitemapController {

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.status(HttpStatus.GONE)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .contentType(MediaType.TEXT_PLAIN)
                .body("Gone");
    }
}
