package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoderResult(
        @JsonProperty("summary") String summary,
        @JsonProperty("testCommand") String testCommand,
        @JsonProperty("testResult") String testResult,
        @Nullable @JsonProperty("commitHash") String commitHash,
        @Nullable @JsonProperty("filesChanged") List<String> filesChanged
) {

    public CoderResult {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be null or blank");
        }
        if (testCommand == null || testCommand.isBlank()) {
            throw new IllegalArgumentException("testCommand must not be null or blank");
        }
        if (testResult == null || testResult.isBlank()) {
            throw new IllegalArgumentException("testResult must not be null or blank");
        }
    }
}
