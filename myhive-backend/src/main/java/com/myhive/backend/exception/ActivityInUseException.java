package com.myhive.backend.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class ActivityInUseException extends RuntimeException {

    private final List<String> packageNames;

    public ActivityInUseException(List<String> packageNames) {
        super("Activity is used in packages: " + String.join(", ", packageNames));
        this.packageNames = packageNames;
    }
}
