package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tddforge.domain.TaskPriority;
import com.tddforge.domain.TaskStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskSummary(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("priority") TaskPriority priority,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("branchName") String branchName,
        @JsonProperty("error") String error,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {
}
