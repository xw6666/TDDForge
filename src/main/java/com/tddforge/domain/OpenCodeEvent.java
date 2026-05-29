package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenCodeEvent(
        @Nullable @JsonProperty("type") String type,
        @Nullable @JsonProperty("sessionId") String sessionId,
        @Nullable @JsonProperty("step_start") StepStart stepStart,
        @Nullable @JsonProperty("step_finish") StepFinish stepFinish,
        @Nullable @JsonProperty("text") String text,
        @Nullable @JsonProperty("role") String role,
        @Nullable @JsonProperty("tool_name") String toolName,
        @Nullable @JsonProperty("tool_input") Map<String, Object> toolInput,
        @Nullable @JsonProperty("tool_result") Object toolResult
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepStart(
            @Nullable @JsonProperty("type") String type
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepFinish(
            @Nullable @JsonProperty("reason") String reason
    ) {}
}
