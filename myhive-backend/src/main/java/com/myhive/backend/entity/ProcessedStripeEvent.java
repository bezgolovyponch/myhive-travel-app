package com.myhive.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "processed_stripe_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @CreationTimestamp
    @Column(name = "received_at", updatable = false)
    private LocalDateTime receivedAt;

    public ProcessedStripeEvent(String eventId) {
        this.eventId = eventId;
    }
}
