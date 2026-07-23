package com.myhive.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TripLeadUnsubscribeRequest {
    @NotNull private UUID token;
}
