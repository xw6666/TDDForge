package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum TaskStatus {
    PENDING,
    PLANNING,
    TEST_WRITING,
    TEST_WRITE_FAILED,
    TEST_REVIEWING,
    TEST_REVIEW_FAILED,
    CODING,
    REVIEWING,
    REVIEW_FAILED,
    NEEDS_ARBITRATION,
    COMPLETED,
    FAILED,
    CANCELLED;

    @JsonCreator
    public static TaskStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskStatus value must not be null or blank");
        }
        String trimmed = value.trim();
        for (TaskStatus s : values()) {
            if (s.name().equals(trimmed)) {
                return s;
            }
        }
        String valid = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Invalid TaskStatus: '" + value + "'. Valid values: " + valid);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
