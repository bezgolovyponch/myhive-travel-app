package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingItemDTO {
    private UUID id;
    private UUID activityId;
    private String activityName;
    private String destinationName;
    private BigDecimal price;
    private Integer quantity;
}
