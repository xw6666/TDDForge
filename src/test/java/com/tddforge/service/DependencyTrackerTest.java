package com.tddforge.service;

import com.tddforge.domain.*;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DependencyTrackerTest {

    @Mock
    private TaskRepository taskRepository;

    private DependencyTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DependencyTracker(taskRepository);
    }

    private TaskEntity createTaskEntity(String id, String title, TaskStatus status, String parentId,
                                         List<String> dependsOn, String repoPath) {
        TaskEntity entity = new TaskEntity();
        entity.setId(id);
        entity.setTitle(title);
        entity.setDescription("Description for " + title);
        entity.setStatus(status);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setTaskMode("develop");
        entity.setParentId(parentId);
        entity.setDependsOn(dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>());
        entity.setRepoPath(repoPath != null ? repoPath : "/repo");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Nested
    class IsBlockedByDependencies {

        @Test
        void shouldNotBlockTaskWithNoDependencies() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
        }

        @Test
        void shouldNotBlockWhenAllDependenciesCompleted() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
        }

        @Test
        void shouldBlockWhenDependencyIsPending() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.PENDING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
        }

        @Test
        void shouldBlockWhenDependencyIsRunning() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
        }

        @Test
        void shouldBlockWhenAnyDependencyIsFailed() {
            TaskEntity dep1 = createTaskEntity("dep1", "Dependency 1", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity dep2 = createTaskEntity("dep2", "Dependency 2", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep1));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(dep2));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
        }

        @Test
        void shouldBlockWhenAnyDependencyIsCancelled() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.CANCELLED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
        }

        @Test
        void shouldBlockWhenAnyDependencyNeedsArbitration() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
        }

        @Test
        void shouldThrowWhenTaskNotFound() {
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> tracker.isBlockedByDependencies("nonexistent"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class GetBlockingReasons {

        @Test
        void shouldReturnEmptyForNoDependencies() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.getBlockingReasons("t1")).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenAllDependenciesCompleted() {
            TaskEntity dep = createTaskEntity("dep1", "Dep Task", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.getBlockingReasons("t1")).isEmpty();
        }

        @Test
        void shouldReportPendingDependency() {
            TaskEntity dep = createTaskEntity("dep1", "Dep Task", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).taskId()).isEqualTo("dep1");
            assertThat(reasons.get(0).reason()).contains("not yet COMPLETED");
            assertThat(reasons.get(0).reason()).contains("PLANNING");
        }

        @Test
        void shouldReportFailedDependency() {
            TaskEntity dep = createTaskEntity("dep1", "Failed Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("FAILED");
            assertThat(reasons.get(0).reason()).contains("Failed Dep");
        }

        @Test
        void shouldReportCancelledDependency() {
            TaskEntity dep = createTaskEntity("dep1", "Cancelled Dep", TaskStatus.CANCELLED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("CANCELLED");
        }

        @Test
        void shouldReportNeedsArbitrationDependency() {
            TaskEntity dep = createTaskEntity("dep1", "Arb Dep", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("arbitration");
        }

        @Test
        void shouldReportMultipleBlockingDependencies() {
            TaskEntity dep1 = createTaskEntity("dep1", "Running Dep", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity dep2 = createTaskEntity("dep2", "Failed Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep1));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(dep2));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(2);
        }
    }

    @Nested
    class AreDependenciesSatisfied {

        @Test
        void shouldReturnTrueForNoDependencies() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
        }

        @Test
        void shouldReturnTrueWhenAllDependenciesCompleted() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
        }

        @Test
        void shouldReturnFalseWhenDependencyNotCompleted() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void shouldReturnFalseWhenDependencyFailed() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void shouldAcceptTaskDomainObject() {
            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of(), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.areDependenciesSatisfied(task)).isTrue();
        }
    }

    @Nested
    class HasFailedDependency {

        @Test
        void shouldReturnFalseForNoDependencies() {
            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of(), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.hasFailedDependency(task)).isFalse();
        }

        @Test
        void shouldReturnTrueWhenDependencyFailed() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));

            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of("dep1"), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.hasFailedDependency(task)).isTrue();
        }

        @Test
        void shouldReturnTrueWhenDependencyCancelled() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.CANCELLED, null, List.of(), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));

            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of("dep1"), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.hasFailedDependency(task)).isTrue();
        }

        @Test
        void shouldReturnTrueWhenDependencyNeedsArbitration() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));

            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of("dep1"), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.hasFailedDependency(task)).isTrue();
        }

        @Test
        void shouldReturnFalseWhenDependencyIsRunning() {
            TaskEntity dep = createTaskEntity("dep1", "Dep", TaskStatus.CODING, null, List.of(), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));

            Task task = new Task("t1", "Task 1", "Desc", TaskStatus.PENDING, TaskPriority.MEDIUM,
                    TaskSource.MANUAL, "develop", null, List.of("dep1"), false, "/repo", "", "",
                    "", null, null, null, null, null, false,
                    List.of(), List.of(), 0, 0, 0, 2, 4, null, null,
                    Instant.now(), Instant.now(), null, null, null);

            assertThat(tracker.hasFailedDependency(task)).isFalse();
        }
    }

    @Nested
    class GetReleasableDownstreamTaskIds {

        @Test
        void shouldFindTasksWhoseDependenciesAreAllCompleted() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity taskWaiting = createTaskEntity("t1", "Waiting", TaskStatus.PENDING, "parent", List.of("dep1"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, taskWaiting));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).containsExactly("t1");
        }

        @Test
        void shouldNotFindTasksWithUnmetDependencies() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity anotherDep = createTaskEntity("dep2", "Still Running", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity taskWaiting = createTaskEntity("t1", "Waiting", TaskStatus.PENDING, "parent", List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, anotherDep, taskWaiting));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(anotherDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).isEmpty();
        }

        @Test
        void shouldNotFindTasksThatAreAlreadyRunning() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity runningTask = createTaskEntity("t1", "Running", TaskStatus.CODING, "parent", List.of("dep1"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, runningTask));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).isEmpty();
        }

        @Test
        void shouldFindMultipleReleasableTasks() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task1 = createTaskEntity("t1", "Waiting 1", TaskStatus.PENDING, "parent", List.of("dep1"), "/repo");
            TaskEntity task2 = createTaskEntity("t2", "Waiting 2", TaskStatus.PENDING, "parent", List.of("dep1"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, task1, task2));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).containsExactlyInAnyOrder("t1", "t2");
        }
    }

    @Nested
    class AggregateParentStatus {

        @Test
        void shouldReturnNoChildrenForTaskWithoutChildTasks() {
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of());

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.NO_CHILDREN);
        }

        @Test
        void shouldReturnAllCompletedWhenAllChildrenCompleted() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.COMPLETED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.ALL_COMPLETED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void shouldReturnWaitingWhenChildrenAreStillRunning() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.CODING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
            assertThat(result.suggestedStatus()).isNull();
        }

        @Test
        void shouldReturnHasBlockedWhenAChildFailed() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.FAILED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.blockReasons()).hasSize(1);
            assertThat(result.blockReasons().get(0).taskTitle()).isEqualTo("Child 2");
        }

        @Test
        void shouldReturnHasBlockedWhenAChildCancelled() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.CANCELLED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.CANCELLED);
        }

        @Test
        void shouldReturnHasBlockedWhenAChildNeedsArbitration() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
        }

        @Test
        void shouldReturnWaitingWhenChildrenArePending() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.PENDING, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
        }

        @Test
        void shouldPrioritizeFailedOverNeedsArbitration() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.FAILED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.FAILED);
        }
    }

    @Nested
    class HandleChildTaskCompletion {

        @Test
        void shouldReturnNullForTaskWithoutParent() {
            TaskEntity task = createTaskEntity("t1", "Standalone", TaskStatus.COMPLETED, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            Task result = tracker.handleChildTaskCompletion("t1");
            assertThat(result).isNull();
        }

        @Test
        void shouldCompleteParentWhenAllChildrenCompleted() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.COMPLETED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Task result = tracker.handleChildTaskCompletion("c2");
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        void shouldNotCompleteParentWhenOtherChildrenStillRunning() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.CODING, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.COMPLETED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));

            Task result = tracker.handleChildTaskCompletion("c2");
            assertThat(result).isNull();
        }

        @Test
        void shouldUpdateParentToNeedsArbitrationWhenChildFails() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Task result = tracker.handleChildTaskCompletion("c2");
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(result.getError()).isNotBlank();
        }
    }

    @Nested
    class HandleChildTaskFailure {

        @Test
        void shouldReturnEmptyListForTaskWithoutParent() {
            TaskEntity task = createTaskEntity("t1", "Standalone", TaskStatus.FAILED, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            List<String> blocked = tracker.handleChildTaskFailure("t1");
            assertThat(blocked).isEmpty();
        }

        @Test
        void shouldIdentifyBlockedSiblings() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity failedChild = createTaskEntity("c1", "Child 1", TaskStatus.FAILED, "parent-1", List.of(), "/repo");
            TaskEntity sibling = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            TaskEntity independentSibling = createTaskEntity("c3", "Child 3", TaskStatus.PENDING, "parent-1", List.of(), "/repo");
            when(taskRepository.findById("c1")).thenReturn(Optional.of(failedChild));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(failedChild, sibling, independentSibling));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<String> blocked = tracker.handleChildTaskFailure("c1");
            assertThat(blocked).containsExactly("c2");
        }

        @Test
        void shouldUpdateParentStatusOnFailure() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity failedChild = createTaskEntity("c1", "Child 1", TaskStatus.FAILED, "parent-1", List.of(), "/repo");
            TaskEntity sibling = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findById("c1")).thenReturn(Optional.of(failedChild));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(failedChild, sibling));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            tracker.handleChildTaskFailure("c1");
            verify(taskRepository).save(any(TaskEntity.class));
        }
    }

    @Nested
    class ParallelAndSerialDependencies {

        @Test
        void parallelChildTasksShouldAllBeUnblockedInitially() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.PENDING, "parent", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent", List.of(), "/repo");

            when(taskRepository.findById("c1")).thenReturn(Optional.of(child1));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));

            assertThat(tracker.isBlockedByDependencies("c1")).isFalse();
            assertThat(tracker.isBlockedByDependencies("c2")).isFalse();
        }

        @Test
        void serialDependencyShouldBlockSecondTask() {
            TaskEntity task1 = createTaskEntity("c1", "Task 1", TaskStatus.CODING, "parent", List.of(), "/repo");
            TaskEntity task2 = createTaskEntity("c2", "Task 2", TaskStatus.PENDING, "parent", List.of("c1"), "/repo");

            when(taskRepository.findById("c1")).thenReturn(Optional.of(task1));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(task2));

            assertThat(tracker.isBlockedByDependencies("c2")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("c2")).isFalse();
        }

        @Test
        void serialDependencyShouldReleaseSecondTaskWhenFirstCompletes() {
            TaskEntity task1 = createTaskEntity("c1", "Task 1", TaskStatus.COMPLETED, "parent", List.of(), "/repo");
            TaskEntity task2 = createTaskEntity("c2", "Task 2", TaskStatus.PENDING, "parent", List.of("c1"), "/repo");

            when(taskRepository.findById("c1")).thenReturn(Optional.of(task1));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(task2));

            assertThat(tracker.isBlockedByDependencies("c2")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("c2")).isTrue();
        }
    }

    @Nested
    class DependencyFailureBlocksDownstream {

        @Test
        void failedDependencyShouldBlockDownstream() {
            TaskEntity failedDep = createTaskEntity("dep1", "Failed", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Blocked Task", TaskStatus.PENDING, null, List.of("dep1"), "/repo");

            when(taskRepository.findById("dep1")).thenReturn(Optional.of(failedDep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
            assertThat(tracker.hasFailedDependency("t1")).isTrue();
        }

        @Test
        void cancelledDependencyShouldBlockDownstream() {
            TaskEntity cancelledDep = createTaskEntity("dep1", "Cancelled", TaskStatus.CANCELLED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Blocked Task", TaskStatus.PENDING, null, List.of("dep1"), "/repo");

            when(taskRepository.findById("dep1")).thenReturn(Optional.of(cancelledDep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.hasFailedDependency("t1")).isTrue();
        }

        @Test
        void needsArbitrationDependencyShouldBlockDownstream() {
            TaskEntity arbDep = createTaskEntity("dep1", "Arbitration", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Blocked Task", TaskStatus.PENDING, null, List.of("dep1"), "/repo");

            when(taskRepository.findById("dep1")).thenReturn(Optional.of(arbDep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.hasFailedDependency("t1")).isTrue();
        }
    }

    @Nested
    class RebuildAfterRestart {

        @Test
        void shouldRebuildDependencyGraphFromDatabase() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));
            when(taskRepository.findById("c1")).thenReturn(Optional.of(child1));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));

            DependencyTracker freshTracker = new DependencyTracker(taskRepository);

            assertThat(freshTracker.areDependenciesSatisfied("c2")).isTrue();
            DependencyTracker.ParentStatusResult result = freshTracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
        }

        @Test
        void shouldComputeParentCompletedFromDatabaseAfterRestart() {
            TaskEntity parent = createTaskEntity("parent-1", "Parent", TaskStatus.PLANNING, null, List.of(), "/repo");
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.COMPLETED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            DependencyTracker freshTracker = new DependencyTracker(taskRepository);
            Task result = freshTracker.handleChildTaskCompletion("c2");
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }
    }

    @Nested
    class ParentAggregation {

        @Test
        void parentShouldNotCompleteUntilAllChildrenCompleted() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.CODING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
            assertThat(result.suggestedStatus()).isNull();
        }

        @Test
        void parentShouldFailWhenAnyChildFails() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.FAILED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void parentShouldNotCompleteButWaitWhenChildrenStillRunning() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.TEST_WRITING, "parent-1", List.of("c1"), "/repo");
            TaskEntity child3 = createTaskEntity("c3", "Child 3", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2, child3));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
        }
    }
}