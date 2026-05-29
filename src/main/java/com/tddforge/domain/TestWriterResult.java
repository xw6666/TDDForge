package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestWriterResult(
        @JsonProperty("summary") String summary,
        @JsonProperty("testCommand") String testCommand,
        @JsonProperty("resultClassification") String resultClassification,
        @Nullable @JsonProperty("commitHash") String commitHash,
        @Nullable @JsonProperty("filesChanged") List<String> filesChanged
) {

    public TestWriterResult {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be null or blank");
        }
        if (testCommand == null || testCommand.isBlank()) {
            throw new IllegalArgumentException("testCommand must not be null or blank");
        }
        if (resultClassification == null || resultClassification.isBlank()) {
            throw new IllegalArgumentException("resultClassification must not be null or blank");
        }
    }
}
