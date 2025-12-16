package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    private String userEmail;
    private List<BookingActivityItem> activities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingActivityItem {
        private UUID activityId;
        private Integer quantity;
    }
}
