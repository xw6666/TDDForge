package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;

public enum ReviewVerdict {
    APPROVE,
    REQUEST_CHANGES;

    @JsonCreator
    public static ReviewVerdict fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ReviewVerdict value must not be null or blank");
        }
        String trimmed = value.trim();
        for (ReviewVerdict v : values()) {
            if (v.name().equals(trimmed)) {
                return v;
            }
        }
        String valid = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Invalid ReviewVerdict: '" + value + "'. Valid values: " + valid);
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
