package com.tddforge.service;

import com.tddforge.domain.Task;
import com.tddforge.domain.TaskStatus;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DependencyTracker {

    private static final Logger log = LoggerFactory.getLogger(DependencyTracker.class);

    private final TaskRepository taskRepository;

    public DependencyTracker(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
    }

    public boolean isBlockedByDependencies(String taskId) {
        Task task = findTaskOrThrow(taskId);
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return false;
        }
        for (String depId : task.getDependsOn()) {
            TaskStatus depStatus = getTaskStatus(depId);
            if (depStatus != TaskStatus.COMPLETED) {
                return true;
            }
        }
        return false;
    }

    public List<BlockReason> getBlockingReasons(String taskId) {
        Task task = findTaskOrThrow(taskId);
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockReason> reasons = new ArrayList<>();
        for (String depId : task.getDependsOn()) {
            Task depTask = findTaskOrThrow(depId);
            TaskStatus depStatus = depTask.getStatus();
            if (depStatus == TaskStatus.COMPLETED) {
                continue;
            }
            String reason;
            if (depStatus == TaskStatus.FAILED) {
                reason = "Dependency " + depId + " (" + depTask.getTitle() + ") has FAILED";
            } else if (depStatus == TaskStatus.CANCELLED) {
                reason = "Dependency " + depId + " (" + depTask.getTitle() + ") has been CANCELLED";
            } else if (depStatus == TaskStatus.NEEDS_ARBITRATION) {
                reason = "Dependency " + depId + " (" + depTask.getTitle() + ") needs arbitration";
            } else {
                reason = "Dependency " + depId + " (" + depTask.getTitle() + ") is not yet COMPLETED (current status: " + depStatus + ")";
            }
            reasons.add(new BlockReason(depId, depTask.getTitle(), depStatus, reason));
        }
        return reasons;
    }

    public boolean areDependenciesSatisfied(String taskId) {
        Task task = findTaskOrThrow(taskId);
        return areDependenciesSatisfied(task);
    }

    public boolean areDependenciesSatisfied(Task task) {
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return true;
        }
        for (String depId : task.getDependsOn()) {
            if (getTaskStatus(depId) != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public boolean hasFailedDependency(String taskId) {
        Task task = findTaskOrThrow(taskId);
        return hasFailedDependency(task);
    }

    public boolean hasFailedDependency(Task task) {
        if (task.getDependsOn() == null || task.getDependsOn().isEmpty()) {
            return false;
        }
        for (String depId : task.getDependsOn()) {
            TaskStatus status = getTaskStatus(depId);
            if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED || status == TaskStatus.NEEDS_ARBITRATION) {
                return true;
            }
        }
        return false;
    }

    public List<String> getReleasableDownstreamTaskIds(String completedTaskId) {
        List<TaskEntity> allTasksWithDep = taskRepository.findAll().stream()
                .filter(t -> t.getDependsOn() != null && t.getDependsOn().contains(completedTaskId))
                .toList();

        List<String> releasable = new ArrayList<>();
        for (TaskEntity entity : allTasksWithDep) {
            Task task = entity.toDomain();
            if (task.getStatus() == TaskStatus.PENDING && areDependenciesSatisfied(task)) {
                releasable.add(task.getId());
            }
        }
        return releasable;
    }

    public ParentStatusResult aggregateParentStatus(String parentId) {
        List<TaskEntity> childEntities = taskRepository.findByParentId(parentId);
        if (childEntities.isEmpty()) {
            return new ParentStatusResult(parentId, ParentStatusType.NO_CHILDREN, Collections.emptyList(), null);
        }

        List<Task> children = childEntities.stream().map(TaskEntity::toDomain).toList();

        boolean allCompleted = children.stream().allMatch(c -> c.getStatus() == TaskStatus.COMPLETED);
        if (allCompleted) {
            return new ParentStatusResult(parentId, ParentStatusType.ALL_COMPLETED, Collections.emptyList(), TaskStatus.COMPLETED);
        }

        boolean anyFailed = children.stream().anyMatch(c -> c.getStatus() == TaskStatus.FAILED);
        boolean anyCancelled = children.stream().anyMatch(c -> c.getStatus() == TaskStatus.CANCELLED);
        boolean anyArbitration = children.stream().anyMatch(c -> c.getStatus() == TaskStatus.NEEDS_ARBITRATION);

        if (anyFailed || anyCancelled || anyArbitration) {
            List<BlockReason> blockingReasons = new ArrayList<>();
            for (Task child : children) {
                TaskStatus cs = child.getStatus();
                if (cs == TaskStatus.FAILED || cs == TaskStatus.CANCELLED || cs == TaskStatus.NEEDS_ARBITRATION) {
                    String reason;
                    if (cs == TaskStatus.FAILED) {
                        reason = "Child task " + child.getId() + " (" + child.getTitle() + ") has FAILED";
                    } else if (cs == TaskStatus.CANCELLED) {
                        reason = "Child task " + child.getId() + " (" + child.getTitle() + ") has been CANCELLED";
                    } else {
                        reason = "Child task " + child.getId() + " (" + child.getTitle() + ") needs arbitration";
                    }
                    blockingReasons.add(new BlockReason(child.getId(), child.getTitle(), cs, reason));
                }
            }

            TaskStatus parentStatus;
            if (anyFailed) {
                parentStatus = TaskStatus.FAILED;
            } else if (anyCancelled) {
                parentStatus = TaskStatus.CANCELLED;
            } else {
                parentStatus = TaskStatus.NEEDS_ARBITRATION;
            }
            return new ParentStatusResult(parentId, ParentStatusType.HAS_BLOCKED, blockingReasons, parentStatus);
        }

        boolean anyPendingOrActive = children.stream().anyMatch(c ->
                c.getStatus() != TaskStatus.COMPLETED &&
                c.getStatus() != TaskStatus.FAILED &&
                c.getStatus() != TaskStatus.CANCELLED &&
                c.getStatus() != TaskStatus.NEEDS_ARBITRATION
        );

        if (anyPendingOrActive) {
            return new ParentStatusResult(parentId, ParentStatusType.WAITING, Collections.emptyList(), null);
        }

        return new ParentStatusResult(parentId, ParentStatusType.WAITING, Collections.emptyList(), null);
    }

    public Task handleChildTaskCompletion(String childTaskId) {
        Task completedChild = findTaskOrThrow(childTaskId);

        if (completedChild.getParentId() == null || completedChild.getParentId().isBlank()) {
            return null;
        }

        ParentStatusResult parentStatus = aggregateParentStatus(completedChild.getParentId());

        if (parentStatus.type() == ParentStatusType.ALL_COMPLETED) {
            Task parentTask = findTaskOrThrow(completedChild.getParentId());
            parentTask.setStatus(TaskStatus.COMPLETED);
            parentTask.setCompletedAt(java.time.Instant.now());
            parentTask.setUpdatedAt(java.time.Instant.now());
            parentTask.setError(null);
            TaskEntity saved = taskRepository.save(TaskEntity.fromDomain(parentTask));
            log.info("Parent task {} completed: all child tasks COMPLETED", parentTask.getId());
            return saved.toDomain();
        }

        if (parentStatus.type() == ParentStatusType.HAS_BLOCKED && parentStatus.suggestedStatus() != null) {
            Task parentTask = findTaskOrThrow(completedChild.getParentId());
            String error = parentStatus.blockReasons().stream()
                    .map(BlockReason::reason)
                    .collect(Collectors.joining("; "));
            parentTask.setStatus(parentStatus.suggestedStatus());
            parentTask.setError(error);
            parentTask.setUpdatedAt(java.time.Instant.now());
            TaskEntity saved = taskRepository.save(TaskEntity.fromDomain(parentTask));
            log.info("Parent task {} status updated to {}: {}", parentTask.getId(), parentStatus.suggestedStatus(), error);
            return saved.toDomain();
        }

        return null;
    }

    public List<String> handleChildTaskFailure(String childTaskId) {
        Task failedChild = findTaskOrThrow(childTaskId);

        if (failedChild.getParentId() == null || failedChild.getParentId().isBlank()) {
            return Collections.emptyList();
        }

        List<TaskEntity> siblingEntities = taskRepository.findByParentId(failedChild.getParentId());
        List<String> blockedSiblingIds = new ArrayList<>();
        for (TaskEntity entity : siblingEntities) {
            Task sibling = entity.toDomain();
            if (sibling.getId().equals(childTaskId)) {
                continue;
            }
            if (sibling.getStatus() == TaskStatus.PENDING && sibling.getDependsOn() != null && sibling.getDependsOn().contains(childTaskId)) {
                blockedSiblingIds.add(sibling.getId());
            }
        }

        ParentStatusResult parentStatus = aggregateParentStatus(failedChild.getParentId());
        if (parentStatus.type() == ParentStatusType.HAS_BLOCKED && parentStatus.suggestedStatus() != null) {
            Task parentTask = findTaskOrThrow(failedChild.getParentId());
            String error = parentStatus.blockReasons().stream()
                    .map(BlockReason::reason)
                    .collect(Collectors.joining("; "));
            parentTask.setStatus(parentStatus.suggestedStatus());
            parentTask.setError(error);
            parentTask.setUpdatedAt(java.time.Instant.now());
            taskRepository.save(TaskEntity.fromDomain(parentTask));
        }

        return blockedSiblingIds;
    }

    private Task findTaskOrThrow(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId))
                .toDomain();
    }

    private TaskStatus getTaskStatus(String taskId) {
        return taskRepository.findById(taskId)
                .map(TaskEntity::getStatus)
                .orElse(null);
    }

    public enum ParentStatusType {
        NO_CHILDREN,
        ALL_COMPLETED,
        HAS_BLOCKED,
        WAITING
    }

    public record BlockReason(String taskId, String taskTitle, TaskStatus dependencyStatus, String reason) {}

    public record ParentStatusResult(
            String parentId,
            ParentStatusType type,
            List<BlockReason> blockReasons,
            TaskStatus suggestedStatus
    ) {}
}