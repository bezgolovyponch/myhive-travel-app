package com.myhive.backend.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositSessionResponse {
    private UUID bookingId;
    private String checkoutUrl;
}
