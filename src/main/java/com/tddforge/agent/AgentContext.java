package com.tddforge.agent;

import com.tddforge.domain.ModelSpec;
import jakarta.annotation.Nullable;
import java.nio.file.Path;

public record AgentContext(
        String taskId,
        Path worktreePath,
        @Nullable String sessionId,
        ModelSpec modelSpec,
        String title,
        String description,
        String repoPath,
        long timeoutSeconds,
        boolean forceNoSplit,
        @Nullable String planOutput,
        @Nullable String testOutput,
        @Nullable String testReviewOutput,
        @Nullable String testWriterResponse,
        @Nullable String coderResponse,
        @Nullable String priorRejections,
        @Nullable String filePath,
        @Nullable String lineNumber,
        @Nullable String dependencyContext,
        @Nullable String reviewerId,
        @Nullable String testPhaseFeedback,
        @Nullable Integer attempt,
        @Nullable String humanRevisionFeedback
) {

    public AgentContext {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be null or blank");
        }
        if (worktreePath == null) {
            throw new IllegalArgumentException("worktreePath must not be null");
        }
        if (modelSpec == null) {
            throw new IllegalArgumentException("modelSpec must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be null or blank");
        }
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath must not be null or blank");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
    }
}
