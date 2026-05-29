package com.tddforge.service;

import com.tddforge.agent.*;
import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.persistence.AgentRunEntity;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final TaskRepository taskRepository;
    private final AgentRunRepository agentRunRepository;
    private final TaskEventRepository taskEventRepository;
    private final PlannerAgent plannerAgent;
    private final TestWriterAgent testWriterAgent;
    private final TestReviewerAgent testReviewerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final AgentOutputExtractor outputExtractor;
    private final PlannerService plannerService;
    private final WorktreeManager worktreeManager;
    private final OrchestratorConfig orchestratorConfig;
    private final OpencodeConfig opencodeConfig;
    private final RepoConfig repoConfig;

    public TaskExecutionService(TaskRepository taskRepository,
                                AgentRunRepository agentRunRepository,
                                TaskEventRepository taskEventRepository,
                                PlannerAgent plannerAgent,
                                TestWriterAgent testWriterAgent,
                                TestReviewerAgent testReviewerAgent,
                                CoderAgent coderAgent,
                                ReviewerAgent reviewerAgent,
                                AgentOutputExtractor outputExtractor,
                                PlannerService plannerService,
                                WorktreeManager worktreeManager,
                                OrchestratorConfig orchestratorConfig,
                                OpencodeConfig opencodeConfig,
                                RepoConfig repoConfig) {
        this.taskRepository = taskRepository;
        this.agentRunRepository = agentRunRepository;
        this.taskEventRepository = taskEventRepository;
        this.plannerAgent = plannerAgent;
        this.testWriterAgent = testWriterAgent;
        this.testReviewerAgent = testReviewerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.outputExtractor = outputExtractor;
        this.plannerService = plannerService;
        this.worktreeManager = worktreeManager;
        this.orchestratorConfig = orchestratorConfig;
        this.opencodeConfig = opencodeConfig;
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
        TaskEntity taskEntity = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Task task = taskEntity.toDomain();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        task.setStatus(TaskStatus.PLANNING);
        task.setStartedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "STATUS_CHANGE", "Task entered PLANNING");

        ExecutionOutcome outcome = runPlanning(task);
        if (outcome instanceof ExecutionOutcome.Cancelled) return outcome;
        if (outcome instanceof ExecutionOutcome.Failed) return outcome;
        if (outcome instanceof ExecutionOutcome.SplitParentWaiting) return outcome;
        if (outcome instanceof ExecutionOutcome.NeedsArbitration) return outcome;

        task = reloadTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            return new ExecutionOutcome.Cancelled(task);
        }

        task = moveToStatus(task, TaskStatus.TEST_WRITING);

        while (true) {
            if (task.getStatus() == TaskStatus.CANCELLED) {
                return new ExecutionOutcome.Cancelled(task);
            }
            if (task.getStatus() == TaskStatus.NEEDS_ARBITRATION) {
                return new ExecutionOutcome.NeedsArbitration(task, task.getError());
            }
            if (task.getStatus() == TaskStatus.FAILED) {
                return new ExecutionOutcome.Failed(task, task.getError());
            }
            if (task.getStatus() == TaskStatus.COMPLETED) {
                return new ExecutionOutcome.Success(task);
            }

            switch (task.getStatus()) {
                case TEST_WRITING, TEST_WRITE_FAILED, TEST_REVIEW_FAILED -> {
                    outcome = runTestWriting(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) return outcome;
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
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) return outcome;
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
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) return outcome;
                    task = reloadTask(taskId);
                    if (task.getStatus() == TaskStatus.REVIEW_FAILED) {
                        continue;
                    }
                    if (task.getStatus() == TaskStatus.REVIEWING) {
                        continue;
                    }
                }
                case REVIEWING -> {
                    outcome = runReviewing(task);
                    if (outcome instanceof ExecutionOutcome.Failed || outcome instanceof ExecutionOutcome.Cancelled || outcome instanceof ExecutionOutcome.NeedsArbitration) return outcome;
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

        ModelSpec modelSpec = toDomainModelSpec(opencodeConfig.getPlanner());
        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(task.getRepoPath()), null, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), null, null, null, null, null, null, null, null, null, null
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

        saveAgentRun(result.agentRun(), task.getId(), "planner");

        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
            task.setUpdatedAt(Instant.now());
            saveTask(task);
        }

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
                    updatedTask.setStatus(TaskStatus.COMPLETED);
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

        ModelSpec modelSpec = toDomainModelSpec(opencodeConfig.getTestWriter());
        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), null, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), task.getPlanOutput(), null, null, null, null, null, null, null, null, null
        );

        AgentResult<TestWriterResult> result;
        try {
            result = testWriterAgent.run(context);
        } catch (Exception e) {
            return handleTestWriteFailure(task, "TestWriter execution exception: " + e.getMessage());
        }

        saveAgentRun(result.agentRun(), task.getId(), "test_writer");
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
        }

        if (result.agentRun().exitCode() != 0) {
            return handleTestWriteRetry(task, "TestWriter exited with non-zero code: " + result.agentRun().exitCode(), maxTestRetries);
        }

        if (result.extractionResult().hasCriticalError()) {
            String error = result.extractionResult().criticalError();
            return handleTestWriteRetry(task, "TestWriter extraction failed: " + error, maxTestRetries);
        }

        TestWriterResult twResult = result.extractionResult().result();
        task.setTestOutput(result.agentRun().output());
        task.setUpdatedAt(Instant.now());
        saveTask(task);

if ("INVALID".equals(twResult.resultClassification())) {
                return handleTestWriteRetry(task, "TestWriter self-check result: INVALID", maxTestRetries);
            }

            task.setStatus(TaskStatus.TEST_REVIEWING);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "TEST_WRITING_PASSED", "TestWriter result: " + twResult.resultClassification());
            return new ExecutionOutcome.Success(task);
    }

    private ExecutionOutcome handleTestWriteFailure(Task task, String error) {
        task.setError(error);
        task.setStatus(TaskStatus.FAILED);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "TEST_WRITE_FAILED", error);
        return new ExecutionOutcome.Failed(task, error);
    }

    private ExecutionOutcome handleTestWriteRetry(Task task, String reason, int maxTestRetries) {
        task.setTestRetryCount(task.getTestRetryCount() + 1);

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
        ModelSpec modelSpec = toDomainModelSpec(opencodeConfig.getTestReviewer());
        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), null, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                null, task.getTestOutput(), null, null, null, null, null, null
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

        saveAgentRun(result.agentRun(), task.getId(), "test_reviewer");
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
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
        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), coderSessionId, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                task.getTestReviewOutput(), null, task.getCodeOutput(),
                task.getReviewOutput() != null ? task.getReviewOutput() : null,
                null, null, null, null
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
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", error + " (retry " + task.getCodeRetryCount() + "/" + maxCodeRetries + ")");
            return new ExecutionOutcome.Success(task);
        }

        saveAgentRun(result.agentRun(), task.getId(), "coder");
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
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
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "CODER_FAILED", "Coder extraction failed: " + result.extractionResult().criticalError());
            return new ExecutionOutcome.Success(task);
        }

CoderResult coderResult = result.extractionResult().result();
            task.setCodeOutput(result.agentRun().output());
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
        ModelSpec modelSpec = getReviewerModelSpec();
        String priorRejections = buildPriorRejections(task);
        long timeout = opencodeConfig.getTimeoutSeconds();
        AgentContext context = new AgentContext(
                task.getId(), Path.of(worktreePath), null, modelSpec,
                task.getTitle(), task.getDescription(), task.getRepoPath(), timeout,
                task.isForceNoSplit(), task.getPlanOutput(), task.getTestOutput(),
                task.getTestReviewOutput(), null, task.getCodeOutput(),
                priorRejections, null, null, null, "reviewer-1"
        );

        AgentResult<ReviewerResult> result;
        try {
            result = reviewerAgent.run(context);
        } catch (Exception e) {
            String error = "Reviewer execution exception: " + e.getMessage();
            log.error(error, e);
            task.setError(error);
            task.setStatus(TaskStatus.FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_FAILED", error);
            return new ExecutionOutcome.Failed(task, error);
        }

        saveAgentRun(result.agentRun(), task.getId(), "reviewer");
        if (result.agentRun().sessionId() != null && !result.agentRun().sessionId().isBlank()) {
            task.addSessionId(result.agentRun().sessionId());
        }

        if (result.extractionResult().hasCriticalError()) {
            String error = result.extractionResult().criticalError();
            task.setCodeRetryCount(task.getCodeRetryCount() + 1);
            if (task.getCodeRetryCount() > maxCodeRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max code retries exceeded after Reviewer extraction failure");
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", error);
                return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
            }
            task.setStatus(TaskStatus.REVIEW_FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_FAILED", "Reviewer extraction failed: " + error);
            return new ExecutionOutcome.Success(task);
        }

        ReviewerResult reviewerResult = result.extractionResult().result();
        task.addReviewerResult(reviewerResult);
        task.setReviewOutput(reviewerResult.feedback());
        task.setUpdatedAt(Instant.now());

        if (reviewerResult.verdict() == ReviewVerdict.APPROVE) {
            task.setReviewPass(true);
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "COMPLETED", "Reviewer approved, task completed");
            return new ExecutionOutcome.Success(task);
        }

        String category = reviewerResult.category();
        recordEvent(task, "REVIEW_REJECTED", "Reviewer requested changes, category: " + category + ", feedback: " + truncate(reviewerResult.feedback(), 200));

        if ("test_issue".equals(category)) {
            task.setTestRetryCount(task.getTestRetryCount() + 1);
            int maxTestRetries = orchestratorConfig.getMaxTestRetries();
            if (task.getTestRetryCount() > maxTestRetries) {
                task.setStatus(TaskStatus.NEEDS_ARBITRATION);
                task.setError("Max test retries exceeded after Reviewer identified test_issue");
                task.setUpdatedAt(Instant.now());
                saveTask(task);
                recordEvent(task, "NEEDS_ARBITRATION", "Max test retries exceeded");
                return new ExecutionOutcome.NeedsArbitration(task, "Max test retries exceeded");
            }
            task.setStatus(TaskStatus.TEST_REVIEW_FAILED);
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "REVIEW_ROUTE_TO_TEST_WRITER", "Reviewer identified test_issue, routing back to TestWriter");
            return new ExecutionOutcome.Success(task);
        }

        task.setCodeRetryCount(task.getCodeRetryCount() + 1);
        if (task.getCodeRetryCount() > maxCodeRetries) {
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Max code retries exceeded after Reviewer REQUEST_CHANGES");
            task.setUpdatedAt(Instant.now());
            saveTask(task);
            recordEvent(task, "NEEDS_ARBITRATION", "Max code retries exceeded");
            return new ExecutionOutcome.NeedsArbitration(task, "Max code retries exceeded");
        }

        task.setStatus(TaskStatus.REVIEW_FAILED);
        task.setUpdatedAt(Instant.now());
        saveTask(task);
        recordEvent(task, "REVIEW_FAILED", "Reviewer requested changes (implementation_issue)");
        return new ExecutionOutcome.Success(task);
    }

    private ModelSpec getCoderModelSpec(Task task) {
        String complexity = task.getComplexity();
        if (complexity != null && !complexity.isBlank()) {
            com.tddforge.config.ModelSpec byComplexity = opencodeConfig.getCoderByComplexity().get(complexity);
            if (byComplexity != null) {
                return toDomainModelSpec(byComplexity);
            }
        }
        return toDomainModelSpec(opencodeConfig.getCoderDefault());
    }

    private ModelSpec getReviewerModelSpec() {
        List<com.tddforge.config.ModelSpec> reviewers = opencodeConfig.getReviewers();
        if (reviewers != null && !reviewers.isEmpty()) {
            return toDomainModelSpec(reviewers.get(0));
        }
        return toDomainModelSpec(opencodeConfig.getCoderDefault());
    }

    private ModelSpec toDomainModelSpec(com.tddforge.config.ModelSpec configSpec) {
        return new ModelSpec(
                configSpec.getModel(),
                configSpec.getVariant() != null && !configSpec.getVariant().isBlank() ? configSpec.getVariant() : null,
                configSpec.getAgent() != null && !configSpec.getAgent().isBlank() ? configSpec.getAgent() : null
        );
    }

    private String getLatestCoderSessionId(Task task) {
        List<String> sessionIds = task.getSessionIds();
        if (sessionIds != null && !sessionIds.isEmpty()) {
            return sessionIds.get(sessionIds.size() - 1);
        }
        return null;
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

    private void saveAgentRun(AgentRun agentRun, String taskId, String agentType) {
        if (agentRun == null) return;
        AgentRunEntity entity = AgentRunEntity.fromDomain(agentRun);
        agentRunRepository.save(entity);
    }

    private void recordEvent(Task task, String eventType, String message) {
        TaskEvent event = new TaskEvent(task.getId(), eventType, message);
        TaskEventEntity entity = TaskEventEntity.fromDomain(event);
        taskEventRepository.save(entity);
        log.info("TaskEvent: taskId={}, type={}, message={}", task.getId(), eventType, truncate(message, 200));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}