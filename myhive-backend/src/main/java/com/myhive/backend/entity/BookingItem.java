package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"booking", "activity", "pkg"})
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "activity_name")
    private String activityName;

    @Column(name = "destination_name")
    private String destinationName;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Snapshot of the activity's group minimum at booking time (same pattern as {@link #price}). */
    @Column(name = "min_price", precision = 10, scale = 2)
    private BigDecimal minPrice;

    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private Package pkg;

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "package_discount_pct", precision = 5, scale = 2)
    private BigDecimal packageDiscountPct;
}
