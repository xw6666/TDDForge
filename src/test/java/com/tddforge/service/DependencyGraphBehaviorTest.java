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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DependencyGraphBehaviorTest {

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
    class RootTasksReady {

        @Test
        void rootTaskWithNoDependenciesShouldBeReady() {
            TaskEntity task = createTaskEntity("t1", "Root Task", TaskStatus.PENDING, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
            assertThat(tracker.hasFailedDependency("t1")).isFalse();
        }

        @Test
        void rootTaskWithEmptyDependenciesShouldBeReady() {
            TaskEntity task = createTaskEntity("t1", "Root Task", TaskStatus.PENDING, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
        }

        @Test
        void parallelChildTasksWithNoDependenciesShouldAllBeReady() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.PENDING, "parent", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.PENDING, "parent", List.of(), "/repo");

            when(taskRepository.findById("c1")).thenReturn(Optional.of(child1));
            when(taskRepository.findById("c2")).thenReturn(Optional.of(child2));

            assertThat(tracker.isBlockedByDependencies("c1")).isFalse();
            assertThat(tracker.isBlockedByDependencies("c2")).isFalse();
        }

        @Test
        void multipleRootTasksWithNoDependenciesShouldAllBeReady() {
            TaskEntity t1 = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of(), "/repo");
            TaskEntity t2 = createTaskEntity("t2", "Task 2", TaskStatus.PENDING, null, List.of(), "/repo");
            TaskEntity t3 = createTaskEntity("t3", "Task 3", TaskStatus.PENDING, null, List.of(), "/repo");

            when(taskRepository.findById("t1")).thenReturn(Optional.of(t1));
            when(taskRepository.findById("t2")).thenReturn(Optional.of(t2));
            when(taskRepository.findById("t3")).thenReturn(Optional.of(t3));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
            assertThat(tracker.isBlockedByDependencies("t2")).isFalse();
            assertThat(tracker.isBlockedByDependencies("t3")).isFalse();
        }
    }

    @Nested
    class DependentTasksWaitForCompletedDependencies {

        @Test
        void dependentTaskShouldWaitWhenDependencyIsPending() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.PENDING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void dependentTaskShouldWaitWhenDependencyIsRunning() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void dependentTaskShouldBeReleasedWhenDependencyCompletes() {
            TaskEntity dep = createTaskEntity("dep1", "Dependency", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
        }

        @Test
        void dependentTaskShouldWaitWhenAnyDependencyNotCompleted() {
            TaskEntity dep1 = createTaskEntity("dep1", "Dep 1", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity dep2 = createTaskEntity("dep2", "Dep 2", TaskStatus.CODING, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep1));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(dep2));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void dependentTaskShouldBeReleasedWhenAllDependenciesComplete() {
            TaskEntity dep1 = createTaskEntity("dep1", "Dep 1", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity dep2 = createTaskEntity("dep2", "Dep 2", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep1));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(dep2));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("t1")).isTrue();
        }

        @Test
        void serialDependencyChainShouldBlockSecondTask() {
            TaskEntity task1 = createTaskEntity("t1", "Task 1", TaskStatus.CODING, "parent", List.of(), "/repo");
            TaskEntity task2 = createTaskEntity("t2", "Task 2", TaskStatus.PENDING, "parent", List.of("t1"), "/repo");

            when(taskRepository.findById("t1")).thenReturn(Optional.of(task1));
            when(taskRepository.findById("t2")).thenReturn(Optional.of(task2));

            assertThat(tracker.isBlockedByDependencies("t2")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t2")).isFalse();
        }

        @Test
        void serialDependencyChainShouldReleaseSecondTaskWhenFirstCompletes() {
            TaskEntity task1 = createTaskEntity("t1", "Task 1", TaskStatus.COMPLETED, "parent", List.of(), "/repo");
            TaskEntity task2 = createTaskEntity("t2", "Task 2", TaskStatus.PENDING, "parent", List.of("t1"), "/repo");

            when(taskRepository.findById("t1")).thenReturn(Optional.of(task1));
            when(taskRepository.findById("t2")).thenReturn(Optional.of(task2));

            assertThat(tracker.isBlockedByDependencies("t2")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("t2")).isTrue();
        }
    }

    @Nested
    class FailedAndArbitrationDependenciesBlockDependents {

        @Test
        void failedDependencyShouldBlockDependent() {
            TaskEntity dep = createTaskEntity("dep1", "Failed Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
            assertThat(tracker.hasFailedDependency("t1")).isTrue();

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("FAILED");
        }

        @Test
        void cancelledDependencyShouldBlockDependent() {
            TaskEntity dep = createTaskEntity("dep1", "Cancelled Dep", TaskStatus.CANCELLED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.hasFailedDependency("t1")).isTrue();

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("CANCELLED");
        }

        @Test
        void needsArbitrationDependencyShouldBlockDependent() {
            TaskEntity dep = createTaskEntity("dep1", "Arb Dep", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.hasFailedDependency("t1")).isTrue();

            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(1);
            assertThat(reasons.get(0).reason()).contains("arbitration");
        }

        @Test
        void multipleFailedDependenciesShouldAllBeReported() {
            TaskEntity dep1 = createTaskEntity("dep1", "Failed Dep", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity dep2 = createTaskEntity("dep2", "Arb Dep", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Dependent", TaskStatus.PENDING, null, List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(dep1));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(dep2));
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            List<DependencyTracker.BlockReason> reasons = tracker.getBlockingReasons("t1");
            assertThat(reasons).hasSize(2);
        }

        @Test
        void getReleasableDownstreamShouldNotReturnTasksWithFailedDependencies() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity failedDep = createTaskEntity("dep2", "Failed", TaskStatus.FAILED, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Waiting", TaskStatus.PENDING, "parent", List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, failedDep, task));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(failedDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).isEmpty();
        }

        @Test
        void getReleasableDownstreamShouldNotReturnTasksWithArbitrationDependencies() {
            TaskEntity completedDep = createTaskEntity("dep1", "Completed", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity arbDep = createTaskEntity("dep2", "Arbitration", TaskStatus.NEEDS_ARBITRATION, null, List.of(), "/repo");
            TaskEntity task = createTaskEntity("t1", "Waiting", TaskStatus.PENDING, "parent", List.of("dep1", "dep2"), "/repo");
            when(taskRepository.findAll()).thenReturn(List.of(completedDep, arbDep, task));
            when(taskRepository.findById("dep1")).thenReturn(Optional.of(completedDep));
            when(taskRepository.findById("dep2")).thenReturn(Optional.of(arbDep));

            List<String> releasable = tracker.getReleasableDownstreamTaskIds("dep1");
            assertThat(releasable).isEmpty();
        }
    }

    @Nested
    class ParentCompletionOnlyAfterAllChildrenComplete {

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
        void parentShouldCompleteWhenAllChildrenCompleted() {
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
        void parentShouldFailWhenAnyChildFails() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.FAILED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(result.blockReasons()).hasSize(1);
        }

        @Test
        void parentShouldShowNeedsArbitrationWhenChildNeedsArbitration() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
        }

        @Test
        void parentShouldShowCancelledWhenChildCancelled() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.CANCELLED, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.HAS_BLOCKED);
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.CANCELLED);
        }

        @Test
        void parentShouldPrioritizeFailedOverNeedsArbitration() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.FAILED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of(), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void parentShouldPrioritizeCancelledOverNeedsArbitration() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.CANCELLED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.NEEDS_ARBITRATION, "parent-1", List.of(), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.suggestedStatus()).isEqualTo(TaskStatus.CANCELLED);
        }

        @Test
        void parentShouldNotCompleteWhenOneChildStillRunning() {
            TaskEntity child1 = createTaskEntity("c1", "Child 1", TaskStatus.COMPLETED, "parent-1", List.of(), "/repo");
            TaskEntity child2 = createTaskEntity("c2", "Child 2", TaskStatus.TEST_WRITING, "parent-1", List.of("c1"), "/repo");
            TaskEntity child3 = createTaskEntity("c3", "Child 3", TaskStatus.PENDING, "parent-1", List.of("c1"), "/repo");
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child1, child2, child3));

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.WAITING);
        }

        @Test
        void handleChildTaskFailureShouldIdentifyBlockedSiblings() {
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
    }

    @Nested
    class MissingDependencyHandling {

        @Test
        void missingDependencyShouldBlockTask() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("nonexistent"), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
        }

        @Test
        void missingDependencyShouldReportAsFailedDependency() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("nonexistent"), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThat(tracker.hasFailedDependency("t1")).isFalse();
        }

        @Test
        void shouldThrowWhenGettingBlockingReasonsForMissingDependency() {
            TaskEntity task = createTaskEntity("t1", "Task 1", TaskStatus.PENDING, null, List.of("missing-dep"), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(taskRepository.findById("missing-dep")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tracker.getBlockingReasons("t1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWhenTaskItselfNotFound() {
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tracker.isBlockedByDependencies("nonexistent"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void missingDependencyInChainShouldBlockDownstream() {
            TaskEntity taskA = createTaskEntity("a", "Task A", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity taskB = createTaskEntity("b", "Task B", TaskStatus.PENDING, null, List.of("a", "missing"), "/repo");
            when(taskRepository.findById("a")).thenReturn(Optional.of(taskA));
            when(taskRepository.findById("b")).thenReturn(Optional.of(taskB));
            when(taskRepository.findById("missing")).thenReturn(Optional.empty());

            assertThat(tracker.isBlockedByDependencies("b")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("b")).isFalse();
        }
    }

    @Nested
    class CycleBehavior {

        @Test
        void cyclicDependencyShouldKeepBothTasksBlocked() {
            TaskEntity taskA = createTaskEntity("a", "Task A", TaskStatus.PENDING, null, List.of("b"), "/repo");
            TaskEntity taskB = createTaskEntity("b", "Task B", TaskStatus.PENDING, null, List.of("a"), "/repo");
            when(taskRepository.findById("a")).thenReturn(Optional.of(taskA));
            when(taskRepository.findById("b")).thenReturn(Optional.of(taskB));

            assertThat(tracker.isBlockedByDependencies("a")).isTrue();
            assertThat(tracker.isBlockedByDependencies("b")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("a")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("b")).isFalse();
        }

        @Test
        void threeWayCycleShouldKeepAllTasksBlocked() {
            TaskEntity taskA = createTaskEntity("a", "Task A", TaskStatus.PENDING, null, List.of("b"), "/repo");
            TaskEntity taskB = createTaskEntity("b", "Task B", TaskStatus.PENDING, null, List.of("c"), "/repo");
            TaskEntity taskC = createTaskEntity("c", "Task C", TaskStatus.PENDING, null, List.of("a"), "/repo");
            when(taskRepository.findById("a")).thenReturn(Optional.of(taskA));
            when(taskRepository.findById("b")).thenReturn(Optional.of(taskB));
            when(taskRepository.findById("c")).thenReturn(Optional.of(taskC));

            assertThat(tracker.isBlockedByDependencies("a")).isTrue();
            assertThat(tracker.isBlockedByDependencies("b")).isTrue();
            assertThat(tracker.isBlockedByDependencies("c")).isTrue();
        }

        @Test
        void selfDependencyShouldKeepTaskBlocked() {
            TaskEntity task = createTaskEntity("t1", "Self Dep", TaskStatus.PENDING, null, List.of("t1"), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(tracker.isBlockedByDependencies("t1")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("t1")).isFalse();
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
        void shouldNotFindTasksThatAreNotPending() {
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
    class HandleChildTaskCompletion {

        @Test
        void shouldReturnNullForTaskWithoutParent() {
            TaskEntity task = createTaskEntity("t1", "Standalone", TaskStatus.COMPLETED, null, List.of(), "/repo");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            Task result = tracker.handleChildTaskCompletion("t1");
            assertThat(result).isNull();
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
        void shouldUpdateParentToNeedsArbitrationWhenChildNeedsArbitration() {
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

        @Test
        void shouldReturnNullForNoChildrenParent() {
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of());

            DependencyTracker.ParentStatusResult result = tracker.aggregateParentStatus("parent-1");
            assertThat(result.type()).isEqualTo(DependencyTracker.ParentStatusType.NO_CHILDREN);
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
    class ComplexDependencyGraphs {

        @Test
        void diamondDependencyShouldCorrectlyTrackCompletion() {
            TaskEntity root = createTaskEntity("root", "Root", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity left = createTaskEntity("left", "Left", TaskStatus.COMPLETED, "parent", List.of("root"), "/repo");
            TaskEntity right = createTaskEntity("right", "Right", TaskStatus.COMPLETED, "parent", List.of("root"), "/repo");
            TaskEntity bottom = createTaskEntity("bottom", "Bottom", TaskStatus.PENDING, "parent", List.of("left", "right"), "/repo");

            when(taskRepository.findById("root")).thenReturn(Optional.of(root));
            when(taskRepository.findById("left")).thenReturn(Optional.of(left));
            when(taskRepository.findById("right")).thenReturn(Optional.of(right));
            when(taskRepository.findById("bottom")).thenReturn(Optional.of(bottom));

            assertThat(tracker.isBlockedByDependencies("left")).isFalse();
            assertThat(tracker.isBlockedByDependencies("right")).isFalse();
            assertThat(tracker.isBlockedByDependencies("bottom")).isFalse();
            assertThat(tracker.areDependenciesSatisfied("bottom")).isTrue();
        }

        @Test
        void diamondDependencyShouldBlockBottomWhenOneBranchIncomplete() {
            TaskEntity root = createTaskEntity("root", "Root", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity left = createTaskEntity("left", "Left", TaskStatus.COMPLETED, "parent", List.of("root"), "/repo");
            TaskEntity right = createTaskEntity("right", "Right", TaskStatus.CODING, "parent", List.of("root"), "/repo");
            TaskEntity bottom = createTaskEntity("bottom", "Bottom", TaskStatus.PENDING, "parent", List.of("left", "right"), "/repo");

            when(taskRepository.findById("root")).thenReturn(Optional.of(root));
            when(taskRepository.findById("left")).thenReturn(Optional.of(left));
            when(taskRepository.findById("right")).thenReturn(Optional.of(right));
            when(taskRepository.findById("bottom")).thenReturn(Optional.of(bottom));

            assertThat(tracker.isBlockedByDependencies("bottom")).isTrue();
            assertThat(tracker.areDependenciesSatisfied("bottom")).isFalse();
        }

        @Test
        void multiLevelChainShouldCorrectlyTrackBlocking() {
            TaskEntity a = createTaskEntity("a", "Task A", TaskStatus.COMPLETED, null, List.of(), "/repo");
            TaskEntity b = createTaskEntity("b", "Task B", TaskStatus.PENDING, null, List.of("a"), "/repo");
            TaskEntity c = createTaskEntity("c", "Task C", TaskStatus.PENDING, null, List.of("b"), "/repo");

            when(taskRepository.findById("a")).thenReturn(Optional.of(a));
            when(taskRepository.findById("b")).thenReturn(Optional.of(b));
            when(taskRepository.findById("c")).thenReturn(Optional.of(c));

            assertThat(tracker.isBlockedByDependencies("b")).isFalse();
            assertThat(tracker.isBlockedByDependencies("c")).isTrue();
        }
    }
}
