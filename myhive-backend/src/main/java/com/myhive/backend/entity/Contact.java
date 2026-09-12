package com.myhive.backend.entity;

import com.myhive.backend.model.ContactSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per email address ever typed into the site (Trip Builder, vote, booking, contact form,
 * payment). Rows are never auto-deleted — this is the sales/ops address book. Opt-out lives in
 * {@link EmailSuppression}, not here.
 */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Normalized (trimmed, lowercase) — see ContactService.normalizeEmail. */
    @Column(unique = true, nullable = false)
    private String email;

    /** Latest non-blank name the visitor gave us (booking or contact form); null when never given. */
    private String name;

    /** Latest locale the visitor browsed in ("de"); null = English. */
    @Column(length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "first_source", nullable = false, length = 20)
    private ContactSource firstSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "last_source", nullable = false, length = 20)
    private ContactSource lastSource;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    /**
     * How many times any capture point saw this address. Soft signal only: incremented without
     * locking, so concurrent touches may lose an increment.
     */
    @Column(name = "touch_count", nullable = false)
    private int touchCount;

    /** When this row was last included in the daily "new contacts" digest; null = not yet reported. */
    @Column(name = "digest_sent_at")
    private LocalDateTime digestSentAt;
}
