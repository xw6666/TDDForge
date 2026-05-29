package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenCodeRequest(
        @JsonProperty("model") String model,
        @JsonProperty("worktreeDir") Path worktreeDir,
        @JsonProperty("prompt") String prompt,
        @Nullable @JsonProperty("sessionId") String sessionId,
        @Nullable @JsonProperty("variant") String variant,
        @Nullable @JsonProperty("agent") String agent,
        @Nullable @JsonProperty("configPath") Path configPath,
        @JsonProperty("timeoutSeconds") long timeoutSeconds
) {

    public OpenCodeRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (worktreeDir == null) {
            throw new IllegalArgumentException("worktreeDir must not be null");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be null or blank");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }
}
