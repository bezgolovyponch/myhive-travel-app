package com.myhive.backend.entity;

import com.myhive.backend.util.TranslationsConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "blog_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BlogPost implements Slugged {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(unique = true, length = 300)
    private String slug;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Per-locale overrides of title/excerpt/content/category, e.g. {"de": {"title": ...}}. Base columns stay English. */
    @Convert(converter = TranslationsConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Map<String, String>> translations;

    @Column(length = 100)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    private LocalDate date;

    /** Per-record SEO gate: only editorially ready records are indexable (sitemap + no noindex). */
    @Column(name = "seo_indexable", nullable = false, columnDefinition = "boolean default false")
    private boolean seoIndexable = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
