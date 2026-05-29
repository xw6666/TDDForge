package com.tddforge.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tddforge.domain.ReviewVerdict;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestReviewerResult(
        @JsonProperty("verdict") ReviewVerdict verdict,
        @JsonProperty("feedback") String feedback
) {

    public TestReviewerResult {
        if (verdict == null) {
            throw new IllegalArgumentException("verdict must not be null");
        }
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("feedback must not be null or blank");
        }
    }
}
