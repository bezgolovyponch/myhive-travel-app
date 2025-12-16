package com.myhive.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "activity_name")
    private String activityName;

    @Column(name = "destination_name")
    private String destinationName;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer quantity;
}
