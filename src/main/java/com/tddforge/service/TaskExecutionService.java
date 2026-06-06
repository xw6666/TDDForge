package com.tddforge.service;

import com.tddforge.agent.*;
import com.tddforge.config.ModelConfigSnapshot;
import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.config.RuntimeModelConfig;
import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.opencode.OpenCodeNdjsonParser;
import com.tddforge.persistence.AgentRunEntity;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.util.MdcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TaskRepository taskRepository;
    private final AgentRunRepository agentRunRepository;
    private final TaskEventRepository taskEventRepository;
    private final PlannerAgent plannerAgent;
    private final TestWriterAgent testWriterAgent;
    private final TestReviewerAgent testReviewerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final PlannerService plannerService;
    private final DependencyTracker dependencyTracker;
    private final WorktreeManager worktreeManager;
    private final OrchestratorConfig orchestratorConfig;
    private final OpencodeConfig opencodeConfig;
    private final RuntimeModelConfig runtimeModelConfig;
    private final RepoConfig repoConfig;

    public TaskExecutionService(TaskRepository taskRepository,
                                AgentRunRepository agentRunRepository,
                                TaskEventRepository taskEventRepository,
                                PlannerAgent plannerAgent,
                                TestWriterAgent testWriterAgent,
                                TestReviewerAgent testReviewerAgent,
                                CoderAgent coderAgent,
                                ReviewerAgent reviewerAgent,
                                PlannerService plannerService,
                                DependencyTracker dependencyTracker,
                                WorktreeManager worktreeManager,
                                OrchestratorConfig orchestratorConfig,
                                OpencodeConfig opencodeConfig,
                                RuntimeModelConfig runtimeModelConfig,
                                RepoConfig repoConfig) {
        this.taskRepository = taskRepository;
        this.agentRunRepository = agentRunRepository;
        this.taskEventRepository = taskEventRepository;
        this.plannerAgent = plannerAgent;
        this.testWriterAgent = testWriterAgent;
        this.testReviewerAgent = testReviewerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.plannerService = plannerService;
        this.dependencyTracker = dependencyTracker;
        this.worktreeManager = worktreeManager;
        this.orchestratorConfig = orchestratorConfig;
        this.opencodeConfig = opencodeConfig;
        this.runtimeModelConfig = runtimeModelConfig;
        this.repoConfig = repoConfig;
    }

    public sealed interface ExecutionOutcome {
        record Success(Task task) implements ExecutionOutcome {}
        record SplitParentWaiting(Task parentTask, List<Task> childTasks) implements ExecutionOutcome {}
        record Failed(Task task, String error) implements ExecutionOutcome {}
        record NeedsArbitration(Task task, String reason) implements ExecutionOutcome {}
        record Cancelled(Task task) implements ExecutionOutcome {}
    }

    public ExecutionOutcome executeTask(String taskId) {
        MdcSupport.setTaskContext(taskId);
        try {
            return doExecuteTask(taskId);
        } finally {
            MdcSupport.clearTaskContext();
        }
    }

    private ExecutionOutcome doExecuteTask(String taskId) {
        TaskEntity taskEntity = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Task task = taskEntity.toDomain();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        if (dependencyTracker.isBlockedByDependencies(taskId)) {
            List<DependencyTracker.BlockReason> blockReasons = dependencyTracker.getBlockingReasons(taskId);
            String reason = blockReasons.stream()
                    .map(DependencyTracker.BlockReason::reason)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Blocked by unmet dependencies");
            task.setError(reason);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "DEPENDENCY_BLOCKED", reason);
            return new ExecutionOutcome.Failed(task, reason);
        }

        task.setStatus(TaskStatus.PLANNING);
        task.setStartedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "STATUS_CHANGE", "Task entered PLANNING");

        ExecutionOutcome outcome = runPlanning(task);
        if (outcome instanceof ExecutionOutcome.Cancelled) return outcome;
        if (outcome instanceof ExecutionOutcome.Failed) {
            handleParentAggregationOnFailure(reloadTask(taskId));
            return outcome;
        }
        if (outcome instanceof ExecutionOutcome.SplitParentWaiting) return outcome;
        if (outcome instanceof ExecutionOutcome.NeedsArbitration) {
            handleParentAggregationOnFailure(reloadTask(taskId));
            return outcome;
        }

        task = reloadTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        task = moveToStatus(task, TaskStatus.TEST_WRITING);

        while (true) {
            if (task.getStatus() == TaskStatus.CANCELLED) {
                handleParentAggregationOnFailure(task);
                return new ExecutionOutcome.Cancelled(task);
            }
            if (task.getStatus() == TaskStatus.NEEDS_ARBITRATION) {
                handleParentAggregationOnFailure(task);
                return new ExecutionOutcome.NeedsArbitration(task, task.getError());
            }
            if (task.getStatus() == TaskStatus.FAILED) {
                handleParentAggregationOnFailure(task);
                return new ExecutionOutcome.Failed(task, task.getError());
            }
            if (task.getStatus() == TaskStatus.COMPLETED) {
                return new ExecutionOutcome.Success(task);
            }

            switch (task.getStatus()) {
                case TEST_WRITING, TEST_WRITE_FAILED, TEST_REVIEW_FAILED -> {
                    outcome = runTestWriting(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) {
                        if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.NeedsArbitration) {
                            handleParentAggregationOnFailure(reloadTask(taskId));
                        }
                        if (outcome instanceof ExecutionOutcome.Cancelled) {
                            handleParentAggregationOnFailure(reloadTask(taskId));
                        }
                        return outcome;
                    }
                    task = reloadTask(taskId);
                    if (task.getStatus() == TaskStatus.TEST_WRITE_FAILED || task.getStatus() == TaskStatus.TEST_REVIEW_FAILED) {
                        continue;
                    }
                    if (task.getStatus() == TaskStatus.TEST_REVIEWING) {
                        continue;
                    }
                }
                case TEST_REVIEWING -> {
                    outcome = runTestReviewing(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) {
                        if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.NeedsArbitration || outcome instanceof ExecutionOutcome.Cancelled) {
                            handleParentAggregationOnFailure(reloadTask(taskId));
                        }
                        return outcome;
                    }
                    task = reloadTask(taskId);
                    if (task.getStatus() == TaskStatus.TEST_REVIEW_FAILED) {
                        continue;
                    }
                    if (task.getStatus() == TaskStatus.CODING) {
                        continue;
                    }
                }
                case CODING, REVIEW_FAILED -> {
                    outcome = runCoding(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) {
                        if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.NeedsArbitration || outcome instanceof ExecutionOutcome.Cancelled) {
                            handleParentAggregationOnFailure(reloadTask(taskId));
                        }
                        return outcome;
                    }
                    task = reloadTask(taskId);
                    if (task.getStatus() == TaskStatus.REVIEW_FAILED || task.getStatus() == TaskStatus.REVIEWING) {
                        continue;
                    }
                }
                case REVIEWING -> {
                    outcome = runReviewing(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) {
                        if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.NeedsArbitration || outcome instanceof ExecutionOutcome.Cancelled) {
                            handleParentAggregationOnFailure(reloadTask(taskId));
                        }
                        return outcome;
                    }
                    task = reloadTask(taskId);
                }
                default -> {
                    return new ExecutionOutcome.Failed(task, "Unexpected status: " + task.getStatus());
                }
            }
        }
    }

    private ExecutionOutcome runPlanning(Task task) {
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        ModelConfigSnapshot snapshot = runtimeModelConfig.snapshot();
        ModelSpec modelSpec = toDomainModelSpec(snapshot.planner());
        long timeout = opencodeConfig.getTimeoutSeconds();
        MdcSupport.setAgentContext("planner", modelSpec.model());
        log.info("Starting planning phase: taskId={}, model={}", task.getId(), modelSpec.model());

        AgentContext context = new AgentContext(
                task.getId(), Path.of(task.getRepoPath()), null, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), null, null, null, null, null, null, null, null, null, null, null, null
        );

        AgentResult<PlannerResult> result;
        try {
            result = plannerAgent.run(context);
        } catch (Exception e) {
            String error = "Planner execution failed: " + e.getMessage();
            log.error(error, e);
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "PLANNING_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        saveAgentRun(result.agentRun());

        Task reloadedAfterPlanner = reloadTask(task.getId());
        if (reloadedAfterPlanner.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(reloadedAfterPlanner);
        }

        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            reloadedAfterPlanner.addSessionId(result.agentRun().sessionId());
            reloadedAfterPlanner.setUpdatedAt(Instant.now());
            saveTask(reloadedAfterPlanner);
        }

        task = reloadedAfterPlanner;

        if (result.extractionResult().hasCriticalError()) {
            String error = result.extractionResult().criticalError();
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "PLANNING_FAILED", "Planner extraction failed: " + error);
            return new ExecutionOutcome.Failed(task, error);
        }

        PlannerService.PlannerOutcome plannerOutcome = plannerService.process(task, result.agentRun(), 0);

        return switch (plannerOutcome) {
            case PlannerService.PlannerOutcome.Success(var updatedTask, var childTasks) -> {
                if (!childTasks.isEmpty()) {
                    updatedTask.setUpdatedAt(Instant.now());
                    for (Task child : childTasks) {
                        saveTask(child);
                        recordEvent(child, "CREATED", "Child task created from planner split");
                    }
                    saveTask(updatedTask);
                    recordEvent(updatedTask, "PLANNER_SPLIT", "Task split into " + childTasks.size() + " sub-tasks");
                    yield new ExecutionOutcome.SplitParentWaiting(updatedTask, childTasks);
                } else {
                    yield continueAfterPlanning(updatedTask);
                }
            }
            case PlannerService.PlannerOutcome.NeedsRetry(var error) -> {
                task.setError(error);
                task.setStatus(TaskStatus.FAILED);
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "PLANNING_FAILED", error);
                yield new ExecutionOutcome.Failed(task, error);
            }
            case PlannerService.PlannerOutcome.Failed(var fTask, var error) -> {
                saveTask(fTask);
                recordEvent(fTask, "PLANNING_FAILED", error);
                yield new ExecutionOutcome.Failed(fTask, error);
            }
        };
    }

    private ExecutionOutcome continueAfterPlanning(Task task) {
        saveTask(task);
        task = reloadTask(task.getId());
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        if (task.getBranchName() == null || task.getBranchName().isBlank()) {
            String branchName = worktreeManager.generateBranchName(task.getId(), task.getTitle());
            task.setBranchName(branchName);
        }

        try {
            Path worktreePath = worktreeManager.createWorktree(task.getId(), task.getBranchName());
            task.setWorktreePath(worktreePath.toString());
        } catch (Exception e) {
            String error = "Failed to create worktree: " + e.getMessage();
            log.error(error, e);
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "WORKTREE_CREATION_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        task.setStatus(TaskStatus.TEST_WRITING);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "STATUS_CHANGE", "Task entered TEST_WRITING, worktree created");
        return new ExecutionOutcome.Success(task);
    }

private ExecutionOutcome runTestWriting(Task task) {
        int maxTestRetries = orchestratorConfig.getMaxTestRetries();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        boolean isRetry = task.getStatus() == TaskStatus.TEST_WRITE_FAILED ||
                          task.getStatus() == TaskStatus.TEST_REVIEW_FAILED;
        String testPhaseFeedback = null;
        Integer attempt = null;

        if (isRetry) {
            attempt = task.getTestRetryCount() + 1;
            MdcSupport.setRetryCount(task.getTestRetryCount() + 1);
            if (task.getStatus() == TaskStatus.TEST_REVIEW_FAILED && task.getTestReviewOutput() != null) {
                testPhaseFeedback = "TestReviewer REQUEST_CHANGES feedback:\n" + task.getTestReviewOutput();
            } else if (task.getError() != null) {
                testPhaseFeedback = "TestWriter self-validation failure:\n" + task.getError();
                if (task.getTestOutput() != null && !task.getTestOutput().isBlank()) {
                    testPhaseFeedback += "\n\nPrevious TestWriter output:\n" + truncate(task.getTestOutput(), 3000);
                }
            }
        }

        task = moveToStatus(task, TaskStatus.TEST_WRITING);

        String worktreePath = task.getWorktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            String error = "Worktree path is not set for task: " + task.getId();
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "TEST_WRITE_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        ModelConfigSnapshot snapshot = runtimeModelConfig.snapshot();
        ModelSpec modelSpec = toDomainModelSpec(snapshot.testWriter());
        long timeout = opencodeConfig.getTimeoutSeconds();
        MdcSupport.setAgentContext("test_writer", modelSpec.model());
        MdcSupport.setWorktreeContext(worktreePath, task.getBranchName());
        log.info("Starting test writing phase: taskId={}, model={}, worktree={}, isRetry={}",
                task.getId(), modelSpec.model(), worktreePath, isRetry);

        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), null, modelSpec,
                task.getTitle(), task.getDescription(), worktreePath, timeout,
                task.isForceNoSplit(), task.getPlanOutput(),
                task.getTestOutput() != null ? task.getTestOutput() : null,
                task.getTestReviewOutput() != null ? task.getTestReviewOutput() : null,
                null, null, null, null, null, null, null,
                testPhaseFeedback, attempt
        );

        AgentResult<TestWriterResult> result;
        try {
            result = testWriterAgent.run(context);
        } catch (Exception e) {
            return handleTestWriteRetry(task, "TestWriter execution exception: " + e.getMessage(), maxTestRetries);
        }

        saveAgentRun(result.agentRun());
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
        }

        Task reloadedAfterTestWrite = reloadTask(task.getId());
        if (reloadedAfterTestWrite.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(reloadedAfterTestWrite);
        }

        if (result.agentRun().exitCode() != 0) {
            return handleTestWriteRetry(task, "TestWriter exited with non-zero code: " + result.agentRun().exitCode(), maxTestRetries);
        }

        if (result.extractionResult().hasCriticalError()) {
            String error = result.extractionResult().criticalError();
            return handleTestWriteRetry(task, "TestWriter extraction failed: " + error, maxTestRetries);
        }

        TestWriterResult twResult = result.extractionResult().result();
        task.setTestOutput(phaseOutput(result.agentRun()));
        task.setUpdatedAt(Instant.now());
        saveTask(task);

        if ("INVALID".equals(twResult.resultClassification())) {
            return handleTestWriteRetry(task, "TestWriter self-check result: INVALID", maxTestRetries);
        }

        String testCommitHash = twResult.commitHash();
        if (testCommitHash == null || testCommitHash.isBlank()) {
            testCommitHash = inferTestCommitHash(worktreePath);
        }

        if (testCommitHash == null || testCommitHash.isBlank()) {
            return handleTestWriteRetry(task, "TestWriter did not include a test commit", maxTestRetries);
        }

        if (!hasExecutableTestCommit(worktreePath, testCommitHash)) {
            return handleTestWriteRetry(task, "TestWriter commit does not include an executable test file", maxTestRetries);
        }

        if ("unknown".equals(twResult.testCommand())) {
            return handleTestWriteRetry(task, "TestWriter did not include a valid test command", maxTestRetries);
        }

        task.setStatus(TaskStatus.TEST_REVIEWING);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "TEST_WRITING_PASSED", "TestWriter result: " + twResult.resultClassification());
        return new ExecutionOutcome.Success(task);
    }

    private boolean hasExecutableTestCommit(String worktreePath, String commitHash) {
        try {
            List<String> files = worktreeManager.committedFiles(Path.of(worktreePath), commitHash);
            boolean hasExecutableTest = files.stream().anyMatch(this::isExecutableTestFile);
            if (!hasExecutableTest) {
                log.warn("TestWriter commit {} does not include executable test files: {}", commitHash, files);
            }
            return hasExecutableTest;
        } catch (RuntimeException e) {
            log.warn("Failed to inspect TestWriter commit {} in {}: {}", commitHash, worktreePath, e.getMessage());
            return false;
        }
    }

    private boolean isExecutableTestFile(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/').toLowerCase();
        if (normalized.endsWith(".md") || normalized.endsWith(".txt") || normalized.endsWith(".rst")
                || normalized.endsWith(".adoc")) {
            return false;
        }
        boolean inTestLocation = normalized.startsWith("src/test/")
                || normalized.startsWith("test/")
                || normalized.startsWith("tests/")
                || normalized.contains("/test/")
                || normalized.contains("/tests/");
        boolean hasExecutableTestExtension = normalized.endsWith(".java")
                || normalized.endsWith(".kt")
                || normalized.endsWith(".groovy")
                || normalized.endsWith(".js")
                || normalized.endsWith(".jsx")
                || normalized.endsWith(".ts")
                || normalized.endsWith(".tsx")
                || normalized.endsWith(".py")
                || normalized.endsWith(".go")
                || normalized.endsWith(".rs")
                || normalized.endsWith(".rb")
                || normalized.endsWith(".php")
                || normalized.endsWith(".cs")
                || normalized.endsWith(".c")
                || normalized.endsWith(".cc")
                || normalized.endsWith(".cpp")
                || normalized.endsWith(".h")
                || normalized.endsWith(".hpp")
                || normalized.endsWith(".feature");
        return inTestLocation && hasExecutableTestExtension;
    }

    private String inferTestCommitHash(String worktreePath) {
        try {
            Path path = Path.of(worktreePath);
            if (!worktreeManager.hasCommitsSinceBase(path)) {
                return null;
            }
            String head = worktreeManager.currentHead(path);
            log.info("Inferred TestWriter commit hash from worktree HEAD: {}", head);
            return head;
        } catch (RuntimeException e) {
            log.warn("Failed to infer TestWriter commit hash from worktree {}: {}", worktreePath, e.getMessage());
            return null;
        }
    }

    private ExecutionOutcome handleTestWriteRetry(Task task, String reason, int maxTestRetries) {
        task.setTestRetryCount(task.getTestRetryCount() + 1);
        task.setError(reason);

        if (task.getTestRetryCount() > maxTestRetries) {
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Max test retries exceeded: " + reason);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "NEEDS_ARBITRATION", "Max test retries exceeded after: " + reason);
            return new ExecutionOutcome.NeedsArbitration(task, "Max test retries exceeded");
        }

        task.setStatus(TaskStatus.TEST_WRITE_FAILED);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "TEST_WRITE_FAILED", reason + " (retry " + task.getTestRetryCount() + "/" + maxTestRetries + ")");
        task = reloadTask(task.getId());
        return new ExecutionOutcome.Success(task);
    }

    private ExecutionOutcome runTestReviewing(Task task) {
        int maxTestRetries = orchestratorConfig.getMaxTestRetries();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        task = moveToStatus(task, TaskStatus.TEST_REVIEWING);

        String worktreePath = task.getWorktreePath();
        ModelConfigSnapshot snapshot = runtimeModelConfig.snapshot();
        ModelSpec modelSpec = toDomainModelSpec(snapshot.testReviewer());
        long timeout = opencodeConfig.getTimeoutSeconds();
        MdcSupport.setAgentContext("test_reviewer", modelSpec.model());
        MdcSupport.setWorktreeContext(worktreePath, task.getBranchName());
        log.info("Starting test review phase: taskId={}, model={}", task.getId(), modelSpec.model());
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), null, modelSpec,
                task.getTitle(), task.getDescription(), worktreePath, timeout,
                task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                null, task.getTestOutput(), null, null, null, null, null, null, null, null
        );

        AgentResult<TestReviewerResult> result;
        try {
            result = testReviewerAgent.run(context);
        } catch (Exception e) {
            String error = "TestReviewer execution exception: " + e.getMessage();
            log.error(error, e);
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "TEST_REVIEW_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        saveAgentRun(result.agentRun());
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
        }

        Task reloadedAfterTestReview = reloadTask(task.getId());
        if (reloadedAfterTestReview.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(reloadedAfterTestReview);
        }

        if (result.extractionResult().hasCriticalError()) {
            String error = result.extractionResult().criticalError();
            task.setTestRetryCount(task.getTestRetryCount() + 1);
            if (task.getTestRetryCount() > maxTestRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max test retries exceeded after TestReviewer extraction failure: " + error);
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", "Max test retries exceeded");
                return new ExecutionOutcome.NeedsArbitration(task, "Max test retries exceeded");
            }

            task.setStatus(TaskStatus.TEST_REVIEW_FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "TEST_REVIEW_FAILED", "TestReviewer extraction failed: " + error);
            return new ExecutionOutcome.Success(task);
        }

        TestReviewerResult trResult = result.extractionResult().result();
        task.setTestReviewOutput(trResult.feedback());
        task.setUpdatedAt(Instant.now());
        saveTask(task);

        if (trResult.verdict() == ReviewVerdict.APPROVE) {
            task.setStatus(TaskStatus.CODING);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "TEST_REVIEW_PASSED", "TestReviewer approved");
            return new ExecutionOutcome.Success(task);
        }

        task.setTestRetryCount(task.getTestRetryCount() + 1);
        if (task.getTestRetryCount() > maxTestRetries) {
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Max test retries exceeded after TestReviewer REQUEST_CHANGES");
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "NEEDS_ARBITRATION", "Max test retries exceeded after TestReviewer rejection");
            return new ExecutionOutcome.NeedsArbitration(task, "Max test retries exceeded");
        }

        task.setStatus(TaskStatus.TEST_REVIEW_FAILED);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "TEST_REVIEW_FAILED", "TestReviewer requested changes: " + truncate(trResult.feedback(), 200));
        return new ExecutionOutcome.Success(task);
    }

    private ExecutionOutcome runCoding(Task task) {
        int maxCodeRetries = orchestratorConfig.getMaxCodeRetries();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        boolean isRetry = task.getStatus() == TaskStatus.REVIEW_FAILED || task.getCodeRetryCount() > 0;
        String reviewFeedback = null;
        Integer attempt = null;

        if (isRetry) {
            attempt = task.getCodeRetryCount() + 1;
            MdcSupport.setRetryCount(task.getCodeRetryCount() + 1);
            reviewFeedback = getLatestReviewFeedback(task);
            if (reviewFeedback == null || reviewFeedback.isBlank()) {
                reviewFeedback = task.getError() != null ? task.getError() : "Previous coder attempt failed.";
            }
        }

        task = moveToStatus(task, TaskStatus.CODING);

        String worktreePath = task.getWorktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            String error = "Worktree path is not set for task: " + task.getId();
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        ModelSpec modelSpec = getCoderModelSpec(task);
        String coderSessionId = getLatestCoderSessionId(task);

        MdcSupport.setAgentContext("coder", modelSpec.model());
        MdcSupport.setWorktreeContext(worktreePath, task.getBranchName());
        log.info("Starting coding phase: taskId={}, model={}, worktree={}, isRetry={}",
                task.getId(), modelSpec.model(), worktreePath, isRetry);

        if (isRetry && coderSessionId == null) {
            recordEvent(task, "CODER_SESSION_MISSING", "No coder session found for retry; opencode will create a new session.");
        }

        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), coderSessionId, modelSpec,
                task.getTitle(), task.getDescription(), worktreePath, timeout,
                task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                task.getTestReviewOutput(), null, task.getCodeOutput(),
                task.getReviewOutput() != null ? task.getReviewOutput() : null,
                null, null, null, null, reviewFeedback, attempt
        );

        AgentResult<CoderResult> result;
        try {
            result = coderAgent.run(context);
        } catch (Exception e) {
            String error = "Coder execution exception: " + e.getMessage();
            log.error(error, e);
            task.setCodeRetryCount(task.getCodeRetryCount() + 1);
            if (task.getCodeRetryCount() > maxCodeRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max code retries exceeded after Coder exception");
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", error);
                return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
            }
            task.setError(error);
            task.setStatus(TaskStatus.CODING);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", error + " (retry " + task.getCodeRetryCount() + "/" + maxCodeRetries + ")");
            return new ExecutionOutcome.Success(task);
        }

        saveAgentRun(result.agentRun());
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
        }

        Task reloadedAfterCoder = reloadTask(task.getId());
        if (reloadedAfterCoder.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(reloadedAfterCoder);
        }

        if (result.agentRun().exitCode() != 0) {
            task.setCodeRetryCount(task.getCodeRetryCount() + 1);
            if (task.getCodeRetryCount() > maxCodeRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max code retries exceeded after Coder non-zero exit");
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", "Max code retries exceeded");
                return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
            }
            task.setError("Coder exited with code: " + result.agentRun().exitCode());
            task.setStatus(TaskStatus.CODING);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", "Coder exited with code: " + result.agentRun().exitCode());
            return new ExecutionOutcome.Success(task);
        }

        if (result.extractionResult().hasCriticalError()) {
            task.setCodeRetryCount(task.getCodeRetryCount() + 1);
            if (task.getCodeRetryCount() > maxCodeRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max code retries exceeded after Coder extraction failure: " + result.extractionResult().criticalError());
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", "Max code retries exceeded");
                return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
            }
            task.setError("Coder extraction failed: " + result.extractionResult().criticalError());
            task.setStatus(TaskStatus.CODING);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", "Coder extraction failed: " + result.extractionResult().criticalError());
            return new ExecutionOutcome.Success(task);
        }

        CoderResult coderResult = result.extractionResult().result();
        task.setCodeOutput(phaseOutput(result.agentRun()));
        task.setStatus(TaskStatus.REVIEWING);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "CODING_PASSED", "Coder completed: " + truncate(coderResult.summary(), 200));
        return new ExecutionOutcome.Success(task);
    }

    private ExecutionOutcome runReviewing(Task task) {
        int maxCodeRetries = orchestratorConfig.getMaxCodeRetries();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        task = moveToStatus(task, TaskStatus.REVIEWING);

        String worktreePath = task.getWorktreePath();
        List<com.tddforge.config.ModelSpec> reviewerSpecs = getReviewerModelSpecList();
        String priorRejections = buildPriorRejections(task);
        long timeout = opencodeConfig.getTimeoutSeconds();

        log.info("Starting review phase: taskId={}, reviewerCount={}, worktree={}",
                task.getId(), reviewerSpecs.size(), worktreePath);

        boolean anyRejected = false;
        ReviewerResult firstRejection = null;

        for (int i = 0; i < reviewerSpecs.size(); i++) {
            ModelSpec modelSpec = toDomainModelSpec(reviewerSpecs.get(i));
            String reviewerId = "reviewer-" + (i + 1);

            MdcSupport.setAgentContext(reviewerId, modelSpec.model());
            log.info("Running reviewer {}: taskId={}, model={}", reviewerId, task.getId(), modelSpec.model());

            AgentContext context = new AgentContext(
                    task.getId(), Path.of(worktreePath), null, modelSpec,
                    task.getTitle(), task.getDescription(), worktreePath, timeout,
                    task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                    task.getTestReviewOutput(), null, task.getCodeOutput(),
                    priorRejections, null, null, null, reviewerId, null, null
            );

            AgentResult<ReviewerResult> result;
            try {
                result = reviewerAgent.run(context);
            } catch (Exception e) {
                String error = "Reviewer " + reviewerId + " execution exception: " + e.getMessage();
                log.error(error, e);
                task.setError(error);
                task.setStatus(TaskStatus.FAILED);
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "REVIEW_FAILED", error);
                return new ExecutionOutcome.Failed(task, error);
            }

            saveAgentRun(result.agentRun());
            if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
                task.addSessionId(result.agentRun().sessionId());
            }

            Task reloadedAfterReviewer = reloadTask(task.getId());
            if (reloadedAfterReviewer.getStatus() == TaskStatus.CANCELLED) {
                return new ExecutionOutcome.Cancelled(reloadedAfterReviewer);
            }

            if (result.extractionResult().hasCriticalError()) {
                String error = result.extractionResult().criticalError();
                task.setCodeRetryCount(task.getCodeRetryCount() + 1);
                if (task.getCodeRetryCount() > maxCodeRetries) {
                    task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                    task.setError("Max code retries exceeded after Reviewer " + reviewerId + " extraction failure");
                    task.setUpdatedAt(Instant.now());
                    saveTask(task);
                    recordEvent(task, "NEEDS_ARBITRATION", error);
                    return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
                }
                task.setStatus(TaskStatus.REVIEW_FAILED);
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "REVIEW_FAILED", "Reviewer " + reviewerId + " extraction failed: " + error);
                return new ExecutionOutcome.Success(task);
            }

            ReviewerResult reviewerResult = result.extractionResult().result();
            task.addReviewerResult(reviewerResult);
            task.setReviewOutput(reviewerResult.feedback());
            task.setUpdatedAt(Instant.now());
            saveTask(task);

            if (reviewerResult.verdict() == ReviewVerdict.REQUEST_CHANGES) {
                anyRejected = true;
                firstRejection = reviewerResult;
                recordEvent(task, "REVIEW_REJECTED",
                        "Reviewer " + reviewerId + " requested changes, category: " + reviewerResult.category()
                                + ", feedback: " + truncate(reviewerResult.feedback(), 200),
                        buildReviewEventData(reviewerResult, null, null));
                break;
            }

            recordEvent(task, "REVIEW_APPROVED", "Reviewer " + reviewerId + " approved");
        }

        if (!anyRejected) {
            task.setReviewPass(true);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "COMPLETED", "All reviewers approved, task completed");
            handleParentAggregationOnCompletion(task);
            return new ExecutionOutcome.Success(task);
        }

        String category = firstRejection.category();

        if ("test_issue".equals(category)) {
            task.setTestRetryCount(task.getTestRetryCount() + 1);
            int maxTestRetries = orchestratorConfig.getMaxTestRetries();
            if (task.getTestRetryCount() > maxTestRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max test retries exceeded after Reviewer identified test_issue");
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "REVIEW_CLASSIFIED",
                        "Reviewer classified as test_issue, max test retries exceeded",
                        buildReviewEventData(firstRejection, "test_issue", "NEEDS_ARBITRATION"));
                return new ExecutionOutcome.NeedsArbitration(task, "Max test retries exceeded");
            }
            task.setStatus(TaskStatus.TEST_REVIEW_FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_CLASSIFIED",
                    "Reviewer classified as test_issue, routing back to TestWriter",
                    buildReviewEventData(firstRejection, "test_issue", "TEST_WRITER"));
            return new ExecutionOutcome.Success(task);
        }

        if ("unclear".equals(category)) {
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Reviewer classified feedback as unclear; requires human arbitration");
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_CLASSIFIED",
                    "Reviewer classified as unclear, routing to NEEDS_ARBITRATION",
                    buildReviewEventData(firstRejection, "unclear", "NEEDS_ARBITRATION"));
            return new ExecutionOutcome.NeedsArbitration(task, "Reviewer feedback unclear, needs arbitration");
        }

        task.setCodeRetryCount(task.getCodeRetryCount() + 1);
        if (task.getCodeRetryCount() > maxCodeRetries) {
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Max code retries exceeded after Reviewer REQUEST_CHANGES");
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_CLASSIFIED",
                    "Reviewer classified as implementation_issue, max code retries exceeded",
                    buildReviewEventData(firstRejection, "implementation_issue", "NEEDS_ARBITRATION"));
            return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
        }

        task.setStatus(TaskStatus.REVIEW_FAILED);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "REVIEW_CLASSIFIED",
                "Reviewer classified as implementation_issue, routing back to Coder",
                buildReviewEventData(firstRejection, "implementation_issue", "CODER"));
        return new ExecutionOutcome.Success(task);
    }

    private ModelSpec getCoderModelSpec(Task task) {
        ModelConfigSnapshot snapshot = runtimeModelConfig.snapshot();
        String complexity = task.getComplexity();
        if (complexity != null && !complexity.isBlank()) {
            com.tddforge.config.ModelSpec byComplexity = snapshot.coderByComplexity().get(complexity);
            if (byComplexity != null) {
                return toDomainModelSpec(byComplexity);
            }
        }
        return toDomainModelSpec(snapshot.coderDefault());
    }

    private List<com.tddforge.config.ModelSpec> getReviewerModelSpecList() {
        ModelConfigSnapshot snapshot = runtimeModelConfig.snapshot();
        List<com.tddforge.config.ModelSpec> reviewers = snapshot.reviewers();
        if (reviewers != null && !reviewers.isEmpty()) {
            return reviewers;
        }
        log.warn("No reviewers configured, falling back to coderDefault model for review");
        return List.of(snapshot.coderDefault());
    }

    private String getLatestReviewFeedback(Task task) {
        if (task.getReviewerResults() != null && !task.getReviewerResults().isEmpty()) {
            ReviewerResult latest = task.getReviewerResults().get(task.getReviewerResults().size() - 1);
            return "Reviewer " + latest.reviewerId() + " " + latest.verdict() + ":\n" + latest.feedback();
        }
        if (task.getReviewOutput() != null && !task.getReviewOutput().isBlank()) {
            return task.getReviewOutput();
        }
        return null;
    }

    private ModelSpec toDomainModelSpec(com.tddforge.config.ModelSpec configSpec) {
        return new ModelSpec(
                configSpec.getModel(),
                configSpec.getVariant() != null && !configSpec.getVariant().isBlank() ? configSpec.getVariant() : null,
                configSpec.getAgent() != null && !configSpec.getAgent().isBlank() ? configSpec.getAgent() : null
        );
    }

    private String getLatestCoderSessionId(Task task) {
        return agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc(task.getId(), "coder")
                .map(AgentRunEntity::getSessionId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }

    private String buildPriorRejections(Task task) {
        if (task.getReviewerResults() == null || task.getReviewerResults().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ReviewerResult rr : task.getReviewerResults()) {
            sb.append("Reviewer ").append(rr.reviewerId()).append(": ").append(rr.verdict()).append("\n");
            sb.append(rr.feedback()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private Task moveToStatus(Task task, TaskStatus newStatus) {
        task.setStatus(newStatus);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "STATUS_CHANGE", "Task entered " + newStatus.name());
        return task;
    }

    private Task reloadTask(String taskId) {
        return taskRepository.findById(taskId).orElseThrow().toDomain();
    }

    private void saveTask(Task task) {
        TaskEntity entity = TaskEntity.fromDomain(task);
        taskRepository.save(entity);
    }

    private void saveAgentRun(AgentRun agentRun) {
        if (agentRun == null) return;
        AgentRunEntity entity = AgentRunEntity.fromDomain(agentRun);
        agentRunRepository.save(entity);
    }

    private String phaseOutput(AgentRun agentRun) {
        if (agentRun == null || agentRun.output() == null || agentRun.output().isBlank()) {
            return "";
        }
        String text = new OpenCodeNdjsonParser().parse(agentRun.output()).text();
        return text == null || text.isBlank() ? agentRun.output() : text;
    }

    private void recordEvent(Task task, String eventType, String message) {
        TaskEvent event = new TaskEvent(task.getId(), eventType, message);
        TaskEventEntity entity = TaskEventEntity.fromDomain(event);
        taskEventRepository.save(entity);
        log.info("TaskEvent: taskId={}, type={}, message={}", task.getId(), eventType, truncate(message, 200));
    }

    private void recordEvent(Task task, String eventType, String message, String dataJson) {
        TaskEvent event = new TaskEvent(task.getId(), eventType, message, dataJson);
        TaskEventEntity entity = TaskEventEntity.fromDomain(event);
        taskEventRepository.save(entity);
        log.info("TaskEvent: taskId={}, type={}, message={}", task.getId(), eventType, truncate(message, 200));
    }

    private String buildReviewEventData(ReviewerResult reviewerResult, @Nullable String category, @Nullable String route) {
        String effectiveCategory = category != null ? category : (reviewerResult.category() != null ? reviewerResult.category() : "unclear");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", effectiveCategory);
        if (route != null) {
            data.put("route", route);
        }
        data.put("reviewerId", reviewerResult.reviewerId());
        data.put("verdict", reviewerResult.verdict().name());
        String feedbackSnippet = reviewerResult.feedback();
        if (feedbackSnippet != null && feedbackSnippet.length() > 500) {
            feedbackSnippet = feedbackSnippet.substring(0, 500);
        }
        data.put("feedbackSnippet", feedbackSnippet);
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize review event data", e);
            return null;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private void handleParentAggregationOnCompletion(Task task) {
        if (task.getParentId() != null && !task.getParentId().isBlank()) {
            try {
                Task updatedParent = dependencyTracker.handleChildTaskCompletion(task.getId());
                if (updatedParent != null) {
                    recordEvent(updatedParent, "PARENT_STATUS_AGGREGATED",
                            "Parent task " + updatedParent.getId() + " status updated to " + updatedParent.getStatus()
                                    + " after child task " + task.getId() + " completed");
                }
            } catch (Exception e) {
                log.warn("Failed to aggregate parent status after child task {} completion: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void handleParentAggregationOnFailure(Task task) {
        if (task.getParentId() != null && !task.getParentId().isBlank()) {
            try {
                List<String> blockedSiblings = dependencyTracker.handleChildTaskFailure(task.getId());
                for (String siblingId : blockedSiblings) {
                    recordEvent(task, "SIBLING_BLOCKED_BY_FAILURE",
                            "Sibling task " + siblingId + " is blocked because task " + task.getId() + " "
                                    + task.getStatus());
                }
            } catch (Exception e) {
                log.warn("Failed to aggregate parent status after child task {} failure: {}", task.getId(), e.getMessage());
            }
        }
    }
}
