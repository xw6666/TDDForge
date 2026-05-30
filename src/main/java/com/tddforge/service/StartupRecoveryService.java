package com.tddforge.service;

import com.tddforge.domain.Task;
import com.tddforge.domain.TaskEvent;
import com.tddforge.domain.TaskStatus;
import com.tddforge.git.WorktreeManager;
import com.tddforge.orchestrator.Orchestrator;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class StartupRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);

    static final String RECOVERY_ERROR_MESSAGE = "daemon restarted during active execution";
    static final String RECOVERY_EVENT_TYPE = "RECOVERY";

    private static final Set<TaskStatus> ACTIVE_STATUSES = Set.of(
            TaskStatus.PLANNING,
            TaskStatus.TEST_WRITING,
            TaskStatus.TEST_REVIEWING,
            TaskStatus.CODING,
            TaskStatus.REVIEWING
    );

    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final WorktreeManager worktreeManager;
    private final Orchestrator orchestrator;

    private volatile ResourceSnapshot lastResourceSnapshot;

    public StartupRecoveryService(TaskRepository taskRepository,
                                   TaskEventRepository taskEventRepository,
                                   WorktreeManager worktreeManager,
                                   Orchestrator orchestrator) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.worktreeManager = worktreeManager;
        this.orchestrator = orchestrator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        recoverAndStart();
    }

    public void recoverAndStart() {
        log.info("Starting daemon recovery...");
        Instant recoveryTime = Instant.now();

        List<TaskEntity> allTasks;
        try {
            allTasks = taskRepository.findAll();
        } catch (Exception e) {
            log.warn("Failed to query tasks during recovery (database may not be initialized): {}", e.getMessage());
            log.info("Skipping recovery, starting orchestrator directly");
            this.lastResourceSnapshot = new ResourceSnapshot(List.of());
            orchestrator.start();
            return;
        }

        log.info("Found {} tasks in database", allTasks.size());

        List<TaskEntity> recoveredTasks = recoverActiveTasks(allTasks, recoveryTime);
        ResourceSnapshot snapshot = buildResourceSnapshot(allTasks);
        this.lastResourceSnapshot = snapshot;

        logRecoverySummary(recoveredTasks, allTasks, snapshot);

        orchestrator.start();
        log.info("Orchestrator started after recovery");
    }

    private List<TaskEntity> recoverActiveTasks(List<TaskEntity> allTasks, Instant recoveryTime) {
        List<TaskEntity> recovered = new ArrayList<>();

        for (TaskEntity entity : allTasks) {
            if (ACTIVE_STATUSES.contains(entity.getStatus())) {
                Task task = entity.toDomain();
                task.setStatus(TaskStatus.FAILED);
                task.setError(RECOVERY_ERROR_MESSAGE);
                task.setUpdatedAt(recoveryTime);
                TaskEntity saved = taskRepository.save(TaskEntity.fromDomain(task));

                TaskEvent event = new TaskEvent(
                        task.getId(),
                        RECOVERY_EVENT_TYPE,
                        "Task recovered: status changed from " + entity.getStatus() + " to FAILED (" + RECOVERY_ERROR_MESSAGE + ")"
                );
                taskEventRepository.save(TaskEventEntity.fromDomain(event));

                recovered.add(saved);
                log.info("Recovered active task {} (was {}): marked as FAILED", task.getId(), entity.getStatus());
            }
        }

        return recovered;
    }

    private ResourceSnapshot buildResourceSnapshot(List<TaskEntity> allTasks) {
        List<ResourceSnapshot.TaskResourceStatus> statuses = new ArrayList<>();

        for (TaskEntity entity : allTasks) {
            String branchName = entity.getBranchName();
            String worktreePath = entity.getWorktreePath();

            boolean branchExists = false;
            boolean worktreeExists = false;

            if (branchName != null && !branchName.isBlank()) {
                branchExists = worktreeManager.branchExists(branchName);
            }
            if (worktreePath != null && !worktreePath.isBlank()) {
                worktreeExists = worktreeManager.worktreeExists(Path.of(worktreePath));
            }

            statuses.add(new ResourceSnapshot.TaskResourceStatus(
                    entity.getId(),
                    branchName,
                    worktreePath,
                    branchExists,
                    worktreeExists
            ));
        }

        return new ResourceSnapshot(statuses);
    }

    private void logRecoverySummary(List<TaskEntity> recovered, List<TaskEntity> allTasks, ResourceSnapshot snapshot) {
        long pendingCount = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long completedCount = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long otherCount = allTasks.size() - recovered.size() - pendingCount - completedCount;

        log.info("Recovery summary: {} active tasks recovered to FAILED, {} pending preserved, {} completed preserved, {} other",
                recovered.size(), pendingCount, completedCount, otherCount);

        long branchesExist = snapshot.tasks().stream().filter(ResourceSnapshot.TaskResourceStatus::branchExists).count();
        long worktreesExist = snapshot.tasks().stream().filter(ResourceSnapshot.TaskResourceStatus::worktreeExists).count();
        log.info("Resource snapshot: {} branches exist, {} worktrees exist out of {} tasks with resources",
                branchesExist, worktreesExist, snapshot.tasks().size());
    }

    public ResourceSnapshot getLastResourceSnapshot() {
        return lastResourceSnapshot;
    }

    public static Set<TaskStatus> getActiveStatuses() {
        return ACTIVE_STATUSES;
    }
}
