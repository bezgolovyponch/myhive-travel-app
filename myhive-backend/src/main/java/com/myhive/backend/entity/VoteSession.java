package com.myhive.backend.entity;

import com.myhive.backend.model.VoteMode;
import com.myhive.backend.model.VoteSessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "vote_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"destination", "likedCategories", "curatedActivities", "quizResponses"})
public class VoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "share_token", unique = true, nullable = false, updatable = false)
    private UUID shareToken;

    @Column(name = "manager_token", nullable = false, updatable = false)
    private UUID managerToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "initiator_email", nullable = false)
    private String initiatorEmail;

    @Column(name = "number_of_travelers", nullable = false)
    private Integer numberOfTravelers;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private VoteSessionStatus status;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "budget", precision = 10, scale = 2)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    // columnDefinition carries a DEFAULT so ddl-auto=update can add this NOT NULL column to the
    // already-populated vote_sessions table in prod; every pre-existing session is quiz-driven.
    @Column(name = "vote_mode", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'QUIZ'")
    private VoteMode voteMode = VoteMode.QUIZ;

    @PrePersist
    private void prePersist() {
        if (maxParticipants == null) {
            maxParticipants = 50;
        }
    }

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vote_session_liked_categories",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Category> likedCategories = new HashSet<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VoteSessionActivity> curatedActivities = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VoteSessionQuizResponse> quizResponses = new ArrayList<>();
}
