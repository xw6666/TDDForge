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
        @Nullable @JsonProperty("sessionID") String sessionID,
        @Nullable @JsonProperty("step_start") StepStart stepStart,
        @Nullable @JsonProperty("step_finish") StepFinish stepFinish,
        @Nullable @JsonProperty("text") String text,
        @Nullable @JsonProperty("role") String role,
        @Nullable @JsonProperty("tool_name") String toolName,
        @Nullable @JsonProperty("tool_input") Map<String, Object> toolInput,
        @Nullable @JsonProperty("tool_result") Object toolResult,
        @Nullable @JsonProperty("part") Part part
) {

    public String sessionId() {
        if (sessionId != null) {
            return sessionId;
        }
        if (sessionID != null) {
            return sessionID;
        }
        return part != null ? part.sessionId() : null;
    }

    public StepStart stepStart() {
        if (stepStart != null) {
            return stepStart;
        }
        if (part != null && "step-start".equals(part.type())) {
            return new StepStart(part.type());
        }
        return null;
    }

    public StepFinish stepFinish() {
        if (stepFinish != null) {
            return stepFinish;
        }
        if (part != null && "step-finish".equals(part.type())) {
            return new StepFinish(part.reason());
        }
        return null;
    }

    public String text() {
        if (text != null) {
            return text;
        }
        return part != null ? part.text() : null;
    }

    public String toolName() {
        if (toolName != null) {
            return toolName;
        }
        return part != null ? part.toolName() : null;
    }

    public Map<String, Object> toolInput() {
        if (toolInput != null) {
            return toolInput;
        }
        return part != null ? part.toolInput() : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepStart(
            @Nullable @JsonProperty("type") String type
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepFinish(
            @Nullable @JsonProperty("reason") String reason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            @Nullable @JsonProperty("type") String type,
            @Nullable @JsonProperty("sessionId") String sessionId,
            @Nullable @JsonProperty("sessionID") String sessionID,
            @Nullable @JsonProperty("text") String text,
            @Nullable @JsonProperty("reason") String reason,
            @Nullable @JsonProperty("tool_name") String toolName,
            @Nullable @JsonProperty("tool_input") Map<String, Object> toolInput
    ) {
        public String sessionId() {
            return sessionId != null ? sessionId : sessionID;
        }
    }
}
