package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private TaskStatus status;

    @JsonProperty("priority")
    private TaskPriority priority;

    @JsonProperty("source")
    private TaskSource source;

    @JsonProperty("taskMode")
    private String taskMode;

    @Nullable @JsonProperty("parentId")
    private String parentId;

    @JsonProperty("dependsOn")
    private List<String> dependsOn = new ArrayList<>();

    @JsonProperty("forceNoSplit")
    private boolean forceNoSplit;

    @JsonProperty("repoPath")
    private String repoPath;

    @JsonProperty("branchName")
    private String branchName;

    @JsonProperty("worktreePath")
    private String worktreePath;

    @JsonProperty("complexity")
    private String complexity;

    @Nullable @JsonProperty("planOutput")
    private String planOutput;

    @Nullable @JsonProperty("testOutput")
    private String testOutput;

    @Nullable @JsonProperty("testReviewOutput")
    private String testReviewOutput;

    @Nullable @JsonProperty("codeOutput")
    private String codeOutput;

    @Nullable @JsonProperty("reviewOutput")
    private String reviewOutput;

    @JsonProperty("reviewPass")
    private boolean reviewPass;

    @JsonProperty("reviewerResults")
    private List<ReviewerResult> reviewerResults = new ArrayList<>();

    @JsonProperty("sessionIds")
    private List<String> sessionIds = new ArrayList<>();

    @JsonProperty("retryCount")
    private int retryCount;

    @JsonProperty("testRetryCount")
    private int testRetryCount;

    @JsonProperty("codeRetryCount")
    private int codeRetryCount;

    @JsonProperty("maxTestRetries")
    private int maxTestRetries = 2;

    @JsonProperty("maxCodeRetries")
    private int maxCodeRetries = 4;

    @Nullable @JsonProperty("userFeedback")
    private String userFeedback;

    @Nullable @JsonProperty("error")
    private String error;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @Nullable @JsonProperty("startedAt")
    private Instant startedAt;

    @Nullable @JsonProperty("completedAt")
    private Instant completedAt;

    @Nullable @JsonProperty("publishedAt")
    private Instant publishedAt;

    public Task() {
    }

    public Task(String id, String title, String description, String repoPath) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.repoPath = repoPath;
        this.status = TaskStatus.PENDING;
        this.priority = TaskPriority.MEDIUM;
        this.source = TaskSource.MANUAL;
        this.taskMode = "develop";
        this.branchName = "";
        this.worktreePath = "";
        this.complexity = "";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Task(String id, String title, String description, TaskStatus status, TaskPriority priority,
                TaskSource source, String taskMode, String parentId, List<String> dependsOn,
                boolean forceNoSplit, String repoPath, String branchName, String worktreePath,
                String complexity, String planOutput, String testOutput, String testReviewOutput,
                String codeOutput, String reviewOutput, boolean reviewPass,
                List<ReviewerResult> reviewerResults, List<String> sessionIds,
                int retryCount, int testRetryCount, int codeRetryCount,
                int maxTestRetries, int maxCodeRetries, String userFeedback, String error,
                Instant createdAt, Instant updatedAt, Instant startedAt,
                Instant completedAt, Instant publishedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.source = source;
        this.taskMode = taskMode;
        this.parentId = parentId;
        this.dependsOn = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>();
        this.forceNoSplit = forceNoSplit;
        this.repoPath = repoPath;
        this.branchName = branchName;
        this.worktreePath = worktreePath;
        this.complexity = complexity;
        this.planOutput = planOutput;
        this.testOutput = testOutput;
        this.testReviewOutput = testReviewOutput;
        this.codeOutput = codeOutput;
        this.reviewOutput = reviewOutput;
        this.reviewPass = reviewPass;
        this.reviewerResults = reviewerResults != null ? new ArrayList<>(reviewerResults) : new ArrayList<>();
        this.sessionIds = sessionIds != null ? new ArrayList<>(sessionIds) : new ArrayList<>();
        this.retryCount = retryCount;
        this.testRetryCount = testRetryCount;
        this.codeRetryCount = codeRetryCount;
        this.maxTestRetries = maxTestRetries;
        this.maxCodeRetries = maxCodeRetries;
        this.userFeedback = userFeedback;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.publishedAt = publishedAt;
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

    @Nullable
    public String getParentId() { return parentId; }
    public void setParentId(@Nullable String parentId) { this.parentId = parentId; }

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

    @Nullable
    public String getPlanOutput() { return planOutput; }
    public void setPlanOutput(@Nullable String planOutput) { this.planOutput = planOutput; }

    @Nullable
    public String getTestOutput() { return testOutput; }
    public void setTestOutput(@Nullable String testOutput) { this.testOutput = testOutput; }

    @Nullable
    public String getTestReviewOutput() { return testReviewOutput; }
    public void setTestReviewOutput(@Nullable String testReviewOutput) { this.testReviewOutput = testReviewOutput; }

    @Nullable
    public String getCodeOutput() { return codeOutput; }
    public void setCodeOutput(@Nullable String codeOutput) { this.codeOutput = codeOutput; }

    @Nullable
    public String getReviewOutput() { return reviewOutput; }
    public void setReviewOutput(@Nullable String reviewOutput) { this.reviewOutput = reviewOutput; }

    public boolean isReviewPass() { return reviewPass; }
    public void setReviewPass(boolean reviewPass) { this.reviewPass = reviewPass; }

    public List<ReviewerResult> getReviewerResults() { return reviewerResults; }
    public void setReviewerResults(List<ReviewerResult> reviewerResults) {
        this.reviewerResults = reviewerResults != null ? new ArrayList<>(reviewerResults) : new ArrayList<>();
    }

    public List<String> getSessionIds() { return sessionIds; }
    public void setSessionIds(List<String> sessionIds) {
        this.sessionIds = sessionIds != null ? new ArrayList<>(sessionIds) : new ArrayList<>();
    }

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

    @Nullable
    public String getUserFeedback() { return userFeedback; }
    public void setUserFeedback(@Nullable String userFeedback) { this.userFeedback = userFeedback; }

    @Nullable
    public String getError() { return error; }
    public void setError(@Nullable String error) { this.error = error; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Nullable
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(@Nullable Instant startedAt) { this.startedAt = startedAt; }

    @Nullable
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(@Nullable Instant completedAt) { this.completedAt = completedAt; }

    @Nullable
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(@Nullable Instant publishedAt) { this.publishedAt = publishedAt; }

    public void addSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            this.sessionIds.add(sessionId);
        }
    }

    public void addReviewerResult(ReviewerResult result) {
        if (result != null) {
            this.reviewerResults.add(result);
        }
    }

    public void addDependency(String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            this.dependsOn.add(taskId);
        }
    }
}
