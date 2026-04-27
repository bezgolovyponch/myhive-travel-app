package com.myhive.backend.controller;

import com.myhive.backend.dto.ActivityDTO;
import com.myhive.backend.dto.BlogPostDTO;
import com.myhive.backend.dto.DestinationDTO;
import com.myhive.backend.dto.PackageDTO;
import com.myhive.backend.service.ActivityService;
import com.myhive.backend.service.BlogPostService;
import com.myhive.backend.service.DestinationService;
import com.myhive.backend.service.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final DestinationService destinationService;
    private final ActivityService activityService;
    private final BlogPostService blogPostService;
    private final PackageService packageService;

    @Value("${app.frontend.url:https://trivlu.com}")
    private String frontendUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(xml, frontendUrl + "/");
        appendUrl(xml, frontendUrl + "/about");
        appendUrl(xml, frontendUrl + "/blog");
        appendUrl(xml, frontendUrl + "/contact");

        List<DestinationDTO> destinations = destinationService.getAllDestinations();
        for (DestinationDTO dest : destinations) {
            if (dest.getSlug() != null && !dest.getSlug().isEmpty()) {
                appendUrl(xml, frontendUrl + "/destination/" + dest.getSlug());
            }
        }

        List<ActivityDTO> activities = activityService.getAllActivities();
        for (ActivityDTO activity : activities) {
            if (activity.getSlug() != null && !activity.getSlug().isEmpty()
                    && activity.getDestinationSlug() != null && !activity.getDestinationSlug().isEmpty()) {
                appendUrl(xml, frontendUrl + "/destination/" + activity.getDestinationSlug() + "/activity/" + activity.getSlug());
            }
        }

        List<PackageDTO> packages = packageService.getAllPackages();
        for (PackageDTO pkg : packages) {
            if (pkg.getSlug() != null && !pkg.getSlug().isEmpty()
                    && pkg.getDestinationSlug() != null && !pkg.getDestinationSlug().isEmpty()) {
                appendUrl(xml, frontendUrl + "/destination/" + pkg.getDestinationSlug() + "/package/" + pkg.getSlug());
            }
        }

        List<BlogPostDTO> blogPosts = blogPostService.getAllBlogPosts();
        for (BlogPostDTO post : blogPosts) {
            if (post.getSlug() != null && !post.getSlug().isEmpty()) {
                appendUrl(xml, frontendUrl + "/blog/" + post.getSlug());
            }
        }

        xml.append("</urlset>");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(xml.toString());
    }

    private void appendUrl(StringBuilder xml, String loc) {
        xml.append("  <url><loc>").append(escapeXml(loc)).append("</loc></url>\n");
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
