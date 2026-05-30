package com.tddforge.service;

import java.util.List;

public record ResourceSnapshot(
        List<TaskResourceStatus> tasks
) {
    public record TaskResourceStatus(
            String taskId,
            String branchName,
            String worktreePath,
            boolean branchExists,
            boolean worktreeExists
    ) {}
}
