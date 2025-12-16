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
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "stripe_session_id", unique = true)
    private String stripeSessionId;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 50)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BookingItem> bookingItems;
}
