package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRun(
        @JsonProperty("id") String id,
        @JsonProperty("taskId") String taskId,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("model") String model,
        @Nullable @JsonProperty("variant") String variant,
        @Nullable @JsonProperty("agent") String agent,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("output") String output,
        @JsonProperty("exitCode") int exitCode,
        @JsonProperty("durationMs") long durationMs,
        @Nullable @JsonProperty("sessionId") String sessionId,
        @JsonProperty("continueCount") int continueCount,
        @JsonProperty("createdAt") Instant createdAt
) {

    public AgentRun {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be null or blank");
        }
        if (agentType == null || agentType.isBlank()) {
            throw new IllegalArgumentException("agentType must not be null or blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (continueCount < 0) {
            throw new IllegalArgumentException("continueCount must not be negative");
        }
    }
}
