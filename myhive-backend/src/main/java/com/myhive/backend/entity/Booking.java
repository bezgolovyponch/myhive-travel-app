package com.myhive.backend.entity;

import com.myhive.backend.model.BookingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "bookingItems")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "stripe_session_id", unique = true)
    private String stripeSessionId;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 50)
    private BookingStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "number_of_travelers")
    private Integer numberOfTravelers;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "utm_term")
    private String utmTerm;

    @Column(name = "utm_content")
    private String utmContent;

    @Column(name = "ref")
    private String ref;

    @Column(name = "gclid")
    private String gclid;

    @Column(name = "fbclid")
    private String fbclid;

    @Column(name = "referrer")
    private String referrer;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "amount_paid", precision = 10, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "refunded_amount", precision = 10, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "participant_share_count")
    private Integer participantShareCount;

    @Column(name = "vote_session_id")
    private UUID voteSessionId;

    @Column(name = "consultation_requested", nullable = false)
    private boolean consultationRequested;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BookingItem> bookingItems;
}
