package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An activity a friend recommends while voting. Anonymous like the votes: only the voter token is
 * stored. The organiser sees the counts per activity on the dashboard and can add one to the plan.
 */
@Entity
@Table(name = "vote_recommendations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_recommendations_session_voter_activity",
                columnNames = {"session_id", "voter_token", "activity_id"}),
        indexes = @Index(name = "idx_vote_recommendations_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VoteRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private VoteSession session;

    @Column(name = "voter_token", nullable = false)
    private UUID voterToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Activity activity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
