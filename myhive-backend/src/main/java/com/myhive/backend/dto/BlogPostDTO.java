package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogPostDTO {
    private UUID id;
    private String title;
    private String excerpt;
    private String content;
    private String category;
    private String imageUrl;
    private LocalDate date;
    private LocalDateTime createdAt;
}
