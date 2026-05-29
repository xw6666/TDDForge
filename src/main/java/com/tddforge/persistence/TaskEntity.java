package com.tddforge.persistence;

import com.tddforge.domain.ReviewerResult;
import com.tddforge.domain.TaskPriority;
import com.tddforge.domain.TaskSource;
import com.tddforge.domain.TaskStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(name = "priority", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "source", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private TaskSource source = TaskSource.MANUAL;

    @Column(name = "task_mode", nullable = false, length = 32)
    private String taskMode = "develop";

    @Column(name = "parent_id", length = 32)
    private String parentId;

    @Column(name = "depends_on_json", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonStringListConverter.class)
    private List<String> dependsOn = new ArrayList<>();

    @Column(name = "force_no_split", nullable = false)
    private boolean forceNoSplit;

    @Column(name = "repo_path", nullable = false, length = 1024)
    private String repoPath;

    @Column(name = "branch_name", nullable = false, length = 512)
    private String branchName = "";

    @Column(name = "worktree_path", nullable = false, length = 1024)
    private String worktreePath = "";

    @Column(name = "complexity", nullable = false, length = 64)
    private String complexity = "";

    @Column(name = "plan_output", columnDefinition = "LONGTEXT")
    private String planOutput;

    @Column(name = "test_output", columnDefinition = "LONGTEXT")
    private String testOutput;

    @Column(name = "test_review_output", columnDefinition = "LONGTEXT")
    private String testReviewOutput;

    @Column(name = "code_output", columnDefinition = "LONGTEXT")
    private String codeOutput;

    @Column(name = "review_output", columnDefinition = "LONGTEXT")
    private String reviewOutput;

    @Column(name = "review_pass", nullable = false)
    private boolean reviewPass;

    @Column(name = "reviewer_results_json", nullable = false, columnDefinition = "JSON")
    @Convert(converter = ReviewerResultsConverter.class)
    private List<ReviewerResult> reviewerResults = new ArrayList<>();

    @Column(name = "session_ids_json", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonStringListConverter.class)
    private List<String> sessionIds = new ArrayList<>();

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "test_retry_count", nullable = false)
    private int testRetryCount;

    @Column(name = "code_retry_count", nullable = false)
    private int codeRetryCount;

    @Column(name = "max_test_retries", nullable = false)
    private int maxTestRetries = 2;

    @Column(name = "max_code_retries", nullable = false)
    private int maxCodeRetries = 4;

    @Column(name = "user_feedback", columnDefinition = "LONGTEXT")
    private String userFeedback;

    @Column(name = "error", columnDefinition = "LONGTEXT")
    private String error;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant updatedAt;

    @Column(name = "started_at", columnDefinition = "DATETIME(3)")
    private Instant startedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(3)")
    private Instant completedAt;

    @Column(name = "published_at", columnDefinition = "DATETIME(3)")
    private Instant publishedAt;

    public TaskEntity() {
    }

    public static TaskEntity fromDomain(com.tddforge.domain.Task task) {
        TaskEntity entity = new TaskEntity();
        entity.id = task.getId();
        entity.title = task.getTitle();
        entity.description = task.getDescription();
        entity.status = task.getStatus();
        entity.priority = task.getPriority();
        entity.source = task.getSource();
        entity.taskMode = task.getTaskMode();
        entity.parentId = task.getParentId();
        entity.dependsOn = new ArrayList<>(task.getDependsOn());
        entity.forceNoSplit = task.isForceNoSplit();
        entity.repoPath = task.getRepoPath();
        entity.branchName = task.getBranchName();
        entity.worktreePath = task.getWorktreePath();
        entity.complexity = task.getComplexity();
        entity.planOutput = task.getPlanOutput();
        entity.testOutput = task.getTestOutput();
        entity.testReviewOutput = task.getTestReviewOutput();
        entity.codeOutput = task.getCodeOutput();
        entity.reviewOutput = task.getReviewOutput();
        entity.reviewPass = task.isReviewPass();
        entity.reviewerResults = new ArrayList<>(task.getReviewerResults());
        entity.sessionIds = new ArrayList<>(task.getSessionIds());
        entity.retryCount = task.getRetryCount();
        entity.testRetryCount = task.getTestRetryCount();
        entity.codeRetryCount = task.getCodeRetryCount();
        entity.maxTestRetries = task.getMaxTestRetries();
        entity.maxCodeRetries = task.getMaxCodeRetries();
        entity.userFeedback = task.getUserFeedback();
        entity.error = task.getError();
        entity.createdAt = task.getCreatedAt();
        entity.updatedAt = task.getUpdatedAt();
        entity.startedAt = task.getStartedAt();
        entity.completedAt = task.getCompletedAt();
        entity.publishedAt = task.getPublishedAt();
        return entity;
    }

    public com.tddforge.domain.Task toDomain() {
        return new com.tddforge.domain.Task(
                id, title, description, status, priority, source, taskMode,
                parentId, new ArrayList<>(dependsOn), forceNoSplit,
                repoPath, branchName, worktreePath,
                complexity, planOutput, testOutput, testReviewOutput,
                codeOutput, reviewOutput, reviewPass,
                new ArrayList<>(reviewerResults), new ArrayList<>(sessionIds),
                retryCount, testRetryCount, codeRetryCount,
                maxTestRetries, maxCodeRetries, userFeedback, error,
                createdAt, updatedAt, startedAt, completedAt, publishedAt
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public TaskSource getSource() { return source; }
    public void setSource(TaskSource source) { this.source = source; }
    public String getTaskMode() { return taskMode; }
    public void setTaskMode(String taskMode) { this.taskMode = taskMode; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>(); }
    public boolean isForceNoSplit() { return forceNoSplit; }
    public void setForceNoSplit(boolean forceNoSplit) { this.forceNoSplit = forceNoSplit; }
    public String getRepoPath() { return repoPath; }
    public void setRepoPath(String repoPath) { this.repoPath = repoPath; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getWorktreePath() { return worktreePath; }
    public void setWorktreePath(String worktreePath) { this.worktreePath = worktreePath; }
    public String getComplexity() { return complexity; }
    public void setComplexity(String complexity) { this.complexity = complexity; }
    public String getPlanOutput() { return planOutput; }
    public void setPlanOutput(String planOutput) { this.planOutput = planOutput; }
    public String getTestOutput() { return testOutput; }
    public void setTestOutput(String testOutput) { this.testOutput = testOutput; }
    public String getTestReviewOutput() { return testReviewOutput; }
    public void setTestReviewOutput(String testReviewOutput) { this.testReviewOutput = testReviewOutput; }
    public String getCodeOutput() { return codeOutput; }
    public void setCodeOutput(String codeOutput) { this.codeOutput = codeOutput; }
    public String getReviewOutput() { return reviewOutput; }
    public void setReviewOutput(String reviewOutput) { this.reviewOutput = reviewOutput; }
    public boolean isReviewPass() { return reviewPass; }
    public void setReviewPass(boolean reviewPass) { this.reviewPass = reviewPass; }
    public List<ReviewerResult> getReviewerResults() { return reviewerResults; }
    public void setReviewerResults(List<ReviewerResult> reviewerResults) { this.reviewerResults = reviewerResults != null ? new ArrayList<>(reviewerResults) : new ArrayList<>(); }
    public List<String> getSessionIds() { return sessionIds; }
    public void setSessionIds(List<String> sessionIds) { this.sessionIds = sessionIds != null ? new ArrayList<>(sessionIds) : new ArrayList<>(); }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getTestRetryCount() { return testRetryCount; }
    public void setTestRetryCount(int testRetryCount) { this.testRetryCount = testRetryCount; }
    public int getCodeRetryCount() { return codeRetryCount; }
    public void setCodeRetryCount(int codeRetryCount) { this.codeRetryCount = codeRetryCount; }
    public int getMaxTestRetries() { return maxTestRetries; }
    public void setMaxTestRetries(int maxTestRetries) { this.maxTestRetries = maxTestRetries; }
    public int getMaxCodeRetries() { return maxCodeRetries; }
    public void setMaxCodeRetries(int maxCodeRetries) { this.maxCodeRetries = maxCodeRetries; }
    public String getUserFeedback() { return userFeedback; }
    public void setUserFeedback(String userFeedback) { this.userFeedback = userFeedback; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
