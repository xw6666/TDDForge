package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonCreator
    public static TaskPriority fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskPriority value must not be null or blank");
        }
        String trimmed = value.trim();
        for (TaskPriority p : values()) {
            if (p.name().equals(trimmed)) {
                return p;
            }
        }
        String valid = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Invalid TaskPriority: '" + value + "'. Valid values: " + valid);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
