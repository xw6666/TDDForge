package com.tddforge.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtractionResult<T>(
        @Nullable @JsonProperty("result") T result,
        @JsonProperty("warnings") List<String> warnings,
        @Nullable @JsonProperty("criticalError") String criticalError,
        @JsonProperty("rawOutput") String rawOutput
) {

    public ExtractionResult {
        if (warnings == null) {
            warnings = List.of();
        }
        if (rawOutput == null) {
            rawOutput = "";
        }
    }

    public static <T> ExtractionResult<T> success(T result, String rawOutput) {
        return new ExtractionResult<>(result, List.of(), null, rawOutput);
    }

    public static <T> ExtractionResult<T> successWithWarnings(T result, List<String> warnings, String rawOutput) {
        return new ExtractionResult<>(result, Collections.unmodifiableList(warnings), null, rawOutput);
    }

    public static <T> ExtractionResult<T> criticalError(String error, String rawOutput) {
        return new ExtractionResult<>(null, List.of(), error, rawOutput);
    }

    public boolean hasCriticalError() {
        return criticalError != null && !criticalError.isBlank();
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
