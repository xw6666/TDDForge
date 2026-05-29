package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewerResult(
        @JsonProperty("reviewerId") String reviewerId,
        @JsonProperty("verdict") ReviewVerdict verdict,
        @JsonProperty("feedback") String feedback,
        @Nullable @JsonProperty("category") String category
) {

    public ReviewerResult {
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new IllegalArgumentException("reviewerId must not be null or blank");
        }
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must not be null");
        }
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("feedback must not be null or blank");
        }
    }
}
