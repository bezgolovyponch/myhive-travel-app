package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentLinkDTO {
        private UUID id;
        private BigDecimal amount;
        private boolean paid;
        private String url;
        private String type;
    }
    private UUID id;
    private String userEmail;
    private String stripeSessionId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private String customerName;
    private String phone;
    private Integer numberOfTravelers;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private String tripId;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmTerm;
    private String utmContent;
    private String ref;
    private String gclid;
    private String fbclid;
    private String referrer;
    private List<BookingItemDTO> items;
    private BigDecimal amountPaid;
    private BigDecimal depositAmount;
    private List<PaymentLinkDTO> paymentLinks;
    private LocalDateTime firstTouchAt;
    private String firstUtmSource;
    private String firstUtmCampaign;
}
