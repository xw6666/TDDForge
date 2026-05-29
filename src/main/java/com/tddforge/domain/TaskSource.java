package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum TaskSource {
    MANUAL,
    WEBHOOK,
    CLI;

    @JsonCreator
    public static TaskSource fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskSource value must not be null or blank");
        }
        String trimmed = value.trim();
        for (TaskSource s : values()) {
            if (s.name().equals(trimmed)) {
                return s;
            }
        }
        String valid = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Invalid TaskSource: '" + value + "'. Valid values: " + valid);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
