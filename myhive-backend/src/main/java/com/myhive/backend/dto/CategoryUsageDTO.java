package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CategoryUsageDTO {

    private final List<String> activityNames;
    private final List<String> packageNames;
}
