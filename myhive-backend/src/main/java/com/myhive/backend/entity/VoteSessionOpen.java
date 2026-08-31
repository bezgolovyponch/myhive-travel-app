package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vote_session_opens",
        uniqueConstraints = @UniqueConstraint(name = "uq_vote_session_opens",
                columnNames = {"session_id", "voter_token"}))
@Getter
@Setter
@NoArgsConstructor
public class VoteSessionOpen {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private VoteSession session;

    @Column(name = "voter_token", nullable = false)
    private UUID voterToken;

    @CreationTimestamp
    @Column(name = "first_opened_at", updatable = false)
    private LocalDateTime firstOpenedAt;
}
