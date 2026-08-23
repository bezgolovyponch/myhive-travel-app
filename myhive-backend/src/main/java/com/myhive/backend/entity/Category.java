package com.myhive.backend.entity;

import com.myhive.backend.util.TranslationsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"activities", "destinations"})
public class Category implements Slugged {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(unique = true, length = 120)
    private String slug;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean votable = true;

    /** Per-locale overrides of name, e.g. {"de": {"name": ...}}. The unique base name stays English. */
    @Convert(converter = TranslationsConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Map<String, String>> translations;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(mappedBy = "categories")
    private Set<Activity> activities = new HashSet<>();

    @ManyToMany(mappedBy = "categories")
    private Set<Destination> destinations = new HashSet<>();
}
