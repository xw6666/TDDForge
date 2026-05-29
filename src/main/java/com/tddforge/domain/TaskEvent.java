package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskEvent(
        @Nullable @JsonProperty("id") Long id,
        @JsonProperty("taskId") String taskId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("message") String message,
        @Nullable @JsonProperty("dataJson") String dataJson,
        @JsonProperty("createdAt") Instant createdAt
) {

    public TaskEvent {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public TaskEvent(String taskId, String eventType, String message) {
        this(null, taskId, eventType, message, null, Instant.now());
    }

    public TaskEvent(String taskId, String eventType, String message, String dataJson) {
        this(null, taskId, eventType, message, dataJson, Instant.now());
    }
}