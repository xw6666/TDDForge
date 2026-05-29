package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

class TaskTest {

    @Test
    void shouldCreateWithMinimalConstructor() {
        var task = new Task("task-1", "Fix bug", "Fix the null pointer", "/repo");
        assertEquals("task-1", task.getId());
        assertEquals("Fix bug", task.getTitle());
        assertEquals("Fix the null pointer", task.getDescription());
        assertEquals("/repo", task.getRepoPath());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertEquals(TaskSource.MANUAL, task.getSource());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        assertTrue(task.getDependsOn().isEmpty());
        assertTrue(task.getSessionIds().isEmpty());
        assertTrue(task.getReviewerResults().isEmpty());
        assertEquals(2, task.getMaxTestRetries());
        assertEquals(4, task.getMaxCodeRetries());
        assertEquals(0, task.getTestRetryCount());
        assertEquals(0, task.getCodeRetryCount());
    }

    @Test
    void shouldSetAndGetAllFields() {
        var now = Instant.now();
        var task = new Task();
        task.setId("t1");
        task.setTitle("Title");
        task.setDescription("Desc");
        task.setStatus(TaskStatus.CODING);
        task.setPriority(TaskPriority.HIGH);
        task.setSource(TaskSource.CLI);
        task.setTaskMode("review");
        task.setParentId("parent-1");
        task.setForceNoSplit(true);
        task.setRepoPath("/repo");
        task.setBranchName("feature/test");
        task.setWorktreePath("/worktrees/test");
        task.setComplexity("medium");
        task.setPlanOutput("plan");
        task.setTestOutput("test");
        task.setTestReviewOutput("test-review");
        task.setCodeOutput("code");
        task.setReviewOutput("review");
        task.setReviewPass(true);
        task.setRetryCount(1);
        task.setTestRetryCount(2);
        task.setCodeRetryCount(3);
        task.setMaxTestRetries(5);
        task.setMaxCodeRetries(6);
        task.setUserFeedback("feedback");
        task.setError("error");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setStartedAt(now);
        task.setCompletedAt(now);
        task.setPublishedAt(now);

        assertEquals("t1", task.getId());
        assertEquals("Title", task.getTitle());
        assertEquals("Desc", task.getDescription());
        assertEquals(TaskStatus.CODING, task.getStatus());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(TaskSource.CLI, task.getSource());
        assertEquals("review", task.getTaskMode());
        assertEquals("parent-1", task.getParentId());
        assertTrue(task.isForceNoSplit());
        assertEquals("/repo", task.getRepoPath());
        assertEquals("feature/test", task.getBranchName());
        assertEquals("/worktrees/test", task.getWorktreePath());
        assertEquals("medium", task.getComplexity());
        assertEquals("plan", task.getPlanOutput());
        assertEquals("test", task.getTestOutput());
        assertEquals("test-review", task.getTestReviewOutput());
        assertEquals("code", task.getCodeOutput());
        assertEquals("review", task.getReviewOutput());
        assertTrue(task.isReviewPass());
        assertEquals(1, task.getRetryCount());
        assertEquals(2, task.getTestRetryCount());
        assertEquals(3, task.getCodeRetryCount());
        assertEquals(5, task.getMaxTestRetries());
        assertEquals(6, task.getMaxCodeRetries());
        assertEquals("feedback", task.getUserFeedback());
        assertEquals("error", task.getError());
        assertEquals(now, task.getCreatedAt());
        assertEquals(now, task.getUpdatedAt());
        assertEquals(now, task.getStartedAt());
        assertEquals(now, task.getCompletedAt());
        assertEquals(now, task.getPublishedAt());
    }

    @Test
    void shouldManageSessionIds() {
        var task = new Task("t1", "title", "desc", "/repo");
        assertTrue(task.getSessionIds().isEmpty());

        task.addSessionId("sess-1");
        task.addSessionId("sess-2");
        assertEquals(2, task.getSessionIds().size());
        assertTrue(task.getSessionIds().contains("sess-1"));
        assertTrue(task.getSessionIds().contains("sess-2"));

        task.addSessionId(null);
        task.addSessionId("  ");
        assertEquals(2, task.getSessionIds().size());

        task.setSessionIds(List.of("sess-3"));
        assertEquals(1, task.getSessionIds().size());
        assertEquals("sess-3", task.getSessionIds().get(0));

        task.setSessionIds(null);
        assertTrue(task.getSessionIds().isEmpty());
    }

    @Test
    void shouldManageReviewerResults() {
        var task = new Task("t1", "title", "desc", "/repo");
        assertTrue(task.getReviewerResults().isEmpty());

        var result = new ReviewerResult("rev-1", ReviewVerdict.APPROVE, "Looks good", null);
        task.addReviewerResult(result);
        assertEquals(1, task.getReviewerResults().size());
        assertEquals("rev-1", task.getReviewerResults().get(0).reviewerId());

        task.addReviewerResult(null);
        assertEquals(1, task.getReviewerResults().size());

        task.setReviewerResults(null);
        assertTrue(task.getReviewerResults().isEmpty());
    }

    @Test
    void shouldManageDependencies() {
        var task = new Task("t1", "title", "desc", "/repo");
        assertTrue(task.getDependsOn().isEmpty());

        task.addDependency("dep-1");
        task.addDependency("dep-2");
        assertEquals(2, task.getDependsOn().size());

        task.addDependency(null);
        task.addDependency("  ");
        assertEquals(2, task.getDependsOn().size());

        task.setDependsOn(List.of("dep-3"));
        assertEquals(1, task.getDependsOn().size());
        assertEquals("dep-3", task.getDependsOn().get(0));

        task.setDependsOn(null);
        assertTrue(task.getDependsOn().isEmpty());
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        var now = Instant.now();
        var result = new ReviewerResult("r1", ReviewVerdict.APPROVE, "OK", null);
        var task = new Task("t1", "title", "desc", TaskStatus.COMPLETED,
                TaskPriority.HIGH, TaskSource.CLI, "develop", "parent-1",
                List.of("dep-1"), true, "/repo", "branch", "/worktree",
                "simple", "plan", "test", "treview", "code", "review",
                true, List.of(result), List.of("sess-1"),
                1, 2, 3, 5, 6, "feedback", "error",
                now, now, now, now, now);

        assertEquals("t1", task.getId());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals(1, task.getDependsOn().size());
        assertEquals(1, task.getSessionIds().size());
        assertEquals(1, task.getReviewerResults().size());
        assertEquals(2, task.getTestRetryCount());
        assertEquals(3, task.getCodeRetryCount());
    }
}
