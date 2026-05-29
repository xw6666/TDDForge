package com.tddforge.web;

import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.opencode.OpenCodeClient;
import com.tddforge.orchestrator.Orchestrator;
import com.tddforge.persistence.AgentRunEntity;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskWebService {

    private static final Logger log = LoggerFactory.getLogger(TaskWebService.class);

    private final TaskRepository taskRepository;
    private final AgentRunRepository agentRunRepository;
    private final Orchestrator orchestrator;
    private final OpenCodeClient openCodeClient;
    private final WorktreeManager worktreeManager;
    private final OrchestratorConfig orchestratorConfig;
    private final RepoConfig repoConfig;

    public TaskWebService(TaskRepository taskRepository,
                          AgentRunRepository agentRunRepository,
                          Orchestrator orchestrator,
                          OpenCodeClient openCodeClient,
                          WorktreeManager worktreeManager,
                          OrchestratorConfig orchestratorConfig,
                          RepoConfig repoConfig) {
        this.taskRepository = taskRepository;
        this.agentRunRepository = agentRunRepository;
        this.orchestrator = orchestrator;
        this.openCodeClient = openCodeClient;
        this.worktreeManager = worktreeManager;
        this.orchestratorConfig = orchestratorConfig;
        this.repoConfig = repoConfig;
    }

    @Transactional
    public TaskDetailResponse createTask(CreateTaskRequest request) {
        String id = generateTaskId();
        Task task = new Task(id, request.title(), request.description(), repoConfig.getPath());

        if (request.priority() != null && !request.priority().isBlank()) {
            task.setPriority(TaskPriority.fromString(request.priority()));
        }
        if (request.forceNoSplit() != null) {
            task.setForceNoSplit(request.forceNoSplit());
        }

        TaskEntity entity = TaskEntity.fromDomain(task);
        taskRepository.save(entity);
        log.info("Created task {} with title '{}'", id, task.getTitle());

        return toDetailResponse(task, List.of());
    }

    public List<TaskSummary> listTasks() {
        return taskRepository.findAll().stream()
                .map(entity -> toSummary(entity.toDomain()))
                .toList();
    }

    public TaskDetailResponse getTask(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        Task task = entity.toDomain();

        List<TaskEntity> childEntities = taskRepository.findByParentId(id);
        List<TaskSummary> children = childEntities.stream()
                .map(e -> toSummary(e.toDomain()))
                .toList();

        return toDetailResponse(task, children);
    }

    public OperationResponse dispatchTask(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidTaskStateException(id, task.getStatus(), "dispatch",
                    "Task must be in PENDING status to dispatch, current status: " + task.getStatus());
        }

        boolean accepted = orchestrator.dispatchTask(id);
        if (!accepted) {
            throw new InvalidTaskStateException(id, task.getStatus(), "dispatch",
                    "Orchestrator rejected dispatch for task " + id);
        }

        log.info("Task {} dispatched to orchestrator", id);
        return OperationResponse.success(id, "Task dispatched successfully");
    }

    @Transactional
    public OperationResponse cancelTask(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new InvalidTaskStateException(id, task.getStatus(), "cancel",
                    "Cannot cancel task in " + task.getStatus() + " status");
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(TaskEntity.fromDomain(task));

        openCodeClient.killTask(id);
        log.info("Task {} cancelled, opencode process killed", id);

        return OperationResponse.success(id, "Task cancelled successfully");
    }

    @Transactional
    public OperationResponse reviseTask(String id, String feedback) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        if (task.getStatus() != TaskStatus.NEEDS_ARBITRATION) {
            throw new InvalidTaskStateException(id, task.getStatus(), "revise",
                    "Task must be in NEEDS_ARBITRATION status to revise, current status: " + task.getStatus());
        }

        task.setUserFeedback(feedback);
        task.setStatus(TaskStatus.PENDING);
        task.setError(null);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(TaskEntity.fromDomain(task));

        log.info("Task {} revised with feedback, status reset to PENDING", id);
        return OperationResponse.success(id, "Task revised successfully");
    }

    @Transactional
    public OperationResponse cleanTask(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        if (task.getStatus() == TaskStatus.PLANNING ||
            task.getStatus() == TaskStatus.TEST_WRITING ||
            task.getStatus() == TaskStatus.TEST_REVIEWING ||
            task.getStatus() == TaskStatus.CODING ||
            task.getStatus() == TaskStatus.REVIEWING) {
            throw new InvalidTaskStateException(id, task.getStatus(), "clean",
                    "Cannot clean task in active status: " + task.getStatus());
        }

        if (task.getWorktreePath() != null && !task.getWorktreePath().isBlank()) {
            try {
                worktreeManager.removeWorktree(Path.of(task.getWorktreePath()));
            } catch (Exception e) {
                log.warn("Failed to remove worktree for task {}: {}", id, e.getMessage());
            }
        }

        if (task.getBranchName() != null && !task.getBranchName().isBlank()) {
            try {
                worktreeManager.deleteBranch(task.getBranchName());
            } catch (Exception e) {
                log.warn("Failed to delete branch for task {}: {}", id, e.getMessage());
            }
        }

        log.info("Task {} cleaned: worktree and branch removed", id);
        return OperationResponse.success(id, "Task cleaned successfully");
    }

    @Transactional
    public OperationResponse publishTask(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new InvalidTaskStateException(id, task.getStatus(), "publish",
                    "Task must be in COMPLETED status to publish, current status: " + task.getStatus());
        }

        if (task.getBranchName() == null || task.getBranchName().isBlank()) {
            throw new InvalidTaskStateException(id, task.getStatus(), "publish",
                    "Task has no branch name set");
        }

        String result = worktreeManager.publish(task.getBranchName());
        log.info("Task {} published: {}", id, result);
        return OperationResponse.success(id, "Task published successfully");
    }

    public List<AgentRun> getTaskRuns(String id) {
        taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        return agentRunRepository.findByTaskIdOrderByCreatedAtDesc(id).stream()
                .map(AgentRunEntity::toDomain)
                .toList();
    }

    public TaskStatusResponse getTaskStatus(String id) {
        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        Task task = entity.toDomain();
        return new TaskStatusResponse(task.getId(), task.getStatus(), task.getError());
    }

    public SystemStatusResponse getSystemStatus() {
        return new SystemStatusResponse(
                orchestrator.isStarted(),
                orchestrator.getRunningCount(),
                orchestrator.getPendingCount(),
                orchestratorConfig.getMaxParallelTasks()
        );
    }

    private TaskDetailResponse toDetailResponse(Task task, List<TaskSummary> children) {
        AgentRunSummary latestRun = null;
        List<AgentRunEntity> runs = agentRunRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        if (!runs.isEmpty()) {
            AgentRunEntity latest = runs.get(0);
            AgentRun domainRun = latest.toDomain();
            latestRun = new AgentRunSummary(
                    domainRun.id(),
                    domainRun.agentType(),
                    domainRun.model(),
                    domainRun.exitCode(),
                    domainRun.durationMs(),
                    domainRun.sessionId(),
                    domainRun.continueCount(),
                    domainRun.createdAt()
            );
        }

        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getSource(),
                task.getTaskMode(),
                task.getParentId(),
                children,
                task.getDependsOn(),
                task.isForceNoSplit(),
                task.getBranchName(),
                task.getWorktreePath(),
                task.getComplexity(),
                task.getRetryCount(),
                task.getTestRetryCount(),
                task.getCodeRetryCount(),
                task.getMaxTestRetries(),
                task.getMaxCodeRetries(),
                task.getSessionIds(),
                task.isReviewPass(),
                task.getReviewerResults(),
                task.getError(),
                task.getUserFeedback(),
                latestRun,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getPublishedAt()
        );
    }

    private TaskSummary toSummary(Task task) {
        return new TaskSummary(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getParentId(),
                task.getBranchName(),
                task.getError(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }
}
