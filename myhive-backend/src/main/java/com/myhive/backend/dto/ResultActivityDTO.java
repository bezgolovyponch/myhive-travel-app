package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultActivityDTO {

    private UUID activityId;
    private String name;          // snapshot
    private BigDecimal price;     // snapshot, per-person
    private long likeCount;
    private long skipCount;
}
