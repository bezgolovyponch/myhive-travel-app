package com.myhive.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private UUID id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 120, message = "Slug must be at most 120 characters")
    private String slug;

    /** Raw per-locale overrides — present on the admin (no-locale) view only; localized reads fold them into name. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, String>> translations;

    public CategoryDTO(UUID id, String name, String slug) {
        this(id, name, slug, null);
    }
}
