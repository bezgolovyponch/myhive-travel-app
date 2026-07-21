package com.myhive.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateBookingStatusRequest {

    @NotBlank(message = "Status is required")
    @Size(max = 50, message = "Status must be at most 50 characters")
    private String status;
}
