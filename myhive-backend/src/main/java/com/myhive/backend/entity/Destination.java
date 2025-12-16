package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "destinations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Activity> activities;
}
