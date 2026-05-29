package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tddforge.domain.*;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskDetailResponse(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("priority") TaskPriority priority,
        @JsonProperty("source") TaskSource source,
        @JsonProperty("taskMode") String taskMode,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("children") List<TaskSummary> children,
        @JsonProperty("dependsOn") List<String> dependsOn,
        @JsonProperty("forceNoSplit") boolean forceNoSplit,
        @JsonProperty("branchName") String branchName,
        @JsonProperty("worktreePath") String worktreePath,
        @JsonProperty("complexity") String complexity,
        @JsonProperty("retryCount") int retryCount,
        @JsonProperty("testRetryCount") int testRetryCount,
        @JsonProperty("codeRetryCount") int codeRetryCount,
        @JsonProperty("maxTestRetries") int maxTestRetries,
        @JsonProperty("maxCodeRetries") int maxCodeRetries,
        @JsonProperty("sessionIds") List<String> sessionIds,
        @JsonProperty("reviewPass") boolean reviewPass,
        @JsonProperty("reviewerResults") List<ReviewerResult> reviewerResults,
        @JsonProperty("error") String error,
        @JsonProperty("userFeedback") String userFeedback,
        @JsonProperty("latestAgentRun") AgentRunSummary latestAgentRun,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("publishedAt") Instant publishedAt
) {
}
