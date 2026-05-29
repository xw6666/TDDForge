package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRunSummary(
        @JsonProperty("id") String id,
        @JsonProperty("agentType") String agentType,
        @JsonProperty("model") String model,
        @JsonProperty("exitCode") int exitCode,
        @JsonProperty("durationMs") long durationMs,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("continueCount") int continueCount,
        @JsonProperty("createdAt") Instant createdAt
) {
}
