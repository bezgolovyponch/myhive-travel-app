package com.myhive.backend.entity;

import com.myhive.backend.model.TripLeadSource;
import com.myhive.backend.model.TripLeadStatus;
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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "destination")
public class TripLead {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Normalized (trimmed, lowercase) — see TripLeadService.normalizeEmail. */
    @Column(nullable = false)
    private String email;

    /** Locale the lead was browsing in ("de"); null = English. Drives the language of the reminder emails and their links. */
    @Column(length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TripLeadSource source;

    @Column(name = "restore_token", unique = true, nullable = false, updatable = false)
    private UUID restoreToken;

    @Column(name = "unsubscribe_token", unique = true, nullable = false, updatable = false)
    private UUID unsubscribeToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "number_of_travelers")
    private Integer numberOfTravelers;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal budget;

    @Column(name = "quiz_responses_json", columnDefinition = "text")
    private String quizResponsesJson;

    @Column(name = "vote_session_id")
    private UUID voteSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TripLeadStatus status;

    @Column(name = "reminder_stage", nullable = false)
    private int reminderStage;

    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;

    /** Series anchor — refreshed on every capture/sync; reminder N+1 is due at lastActivityAt + cadence[N]. */
    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
