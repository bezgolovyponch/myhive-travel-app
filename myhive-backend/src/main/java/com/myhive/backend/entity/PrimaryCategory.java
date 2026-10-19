package com.myhive.backend.entity;

import com.myhive.backend.util.TranslationsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** One of the ten fixed SEO hubs an activity belongs to; the URL source for category pages (not the m2m {@link Category}). */
@Entity
@Table(name = "primary_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class PrimaryCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "slug_de", nullable = false, unique = true, length = 120)
    private String slugDe;

    @Column(nullable = false, length = 100)
    private String name;

    /** Per-locale overrides of name, e.g. {"de": {"name": ...}}. */
    @Convert(converter = TranslationsConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Map<String, String>> translations;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
