package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationDTO {
    private UUID id;
    private String name;
    private String description;
    private String country;
    private String city;
    private String imageUrl;
    private BigDecimal rating;
}
