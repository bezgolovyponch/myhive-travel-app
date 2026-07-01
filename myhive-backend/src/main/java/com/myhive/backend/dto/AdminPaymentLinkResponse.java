package com.myhive.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminPaymentLinkResponse(String url, BigDecimal amount, UUID shareId) {
}
