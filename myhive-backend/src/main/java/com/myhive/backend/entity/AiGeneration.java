package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/** One LLM generation attempt (and its outcome) for a planner session. */
@Entity
@Table(name = "ai_generations")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "session")
public class AiGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiGenerationStatus status;

    @Column(name = "brief_snapshot", nullable = false, columnDefinition = "TEXT")
    private String briefSnapshot;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false)
    private boolean degraded = false;

    @Column(name = "selected_package_key", length = 16)
    private String selectedPackageKey;

    @Column(name = "selected_at")
    private LocalDateTime selectedAt;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(length = 64)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false)
    private short attempt = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
