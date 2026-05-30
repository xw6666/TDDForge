package com.tddforge.service;

import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.orchestrator.Orchestrator;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StartupRecoveryServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private WorktreeManager worktreeManager;
    @Mock
    private Orchestrator orchestrator;

    private StartupRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new StartupRecoveryService(
                taskRepository, taskEventRepository, worktreeManager, orchestrator);
    }

    private TaskEntity createTaskEntity(String id, TaskStatus status) {
        return createTaskEntity(id, status, "", "");
    }

    private TaskEntity createTaskEntity(String id, TaskStatus status, String branchName, String worktreePath) {
        TaskEntity entity = new TaskEntity();
        entity.setId(id);
        entity.setTitle("Task " + id);
        entity.setDescription("Description for " + id);
        entity.setStatus(status);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setTaskMode("develop");
        entity.setDependsOn(new ArrayList<>());
        entity.setRepoPath("/repo");
        entity.setBranchName(branchName != null ? branchName : "");
        entity.setWorktreePath(worktreePath != null ? worktreePath : "");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Nested
    class ActiveTaskRecovery {

        @Test
        void shouldMarkPlanningTaskAsFailed() {
            TaskEntity planning = createTaskEntity("t1", TaskStatus.PLANNING);
            when(taskRepository.findAll()).thenReturn(List.of(planning));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(saved.getError()).isEqualTo("daemon restarted during active execution");
        }

        @Test
        void shouldMarkTestWritingTaskAsFailed() {
            TaskEntity testWriting = createTaskEntity("t2", TaskStatus.TEST_WRITING);
            when(taskRepository.findAll()).thenReturn(List.of(testWriting));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.FAILED);
            assertThat(captor.getValue().getError()).isEqualTo("daemon restarted during active execution");
        }

        @Test
        void shouldMarkTestReviewingTaskAsFailed() {
            TaskEntity testReviewing = createTaskEntity("t3", TaskStatus.TEST_REVIEWING);
            when(taskRepository.findAll()).thenReturn(List.of(testReviewing));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldMarkCodingTaskAsFailed() {
            TaskEntity coding = createTaskEntity("t4", TaskStatus.CODING);
            when(taskRepository.findAll()).thenReturn(List.of(coding));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldMarkReviewingTaskAsFailed() {
            TaskEntity reviewing = createTaskEntity("t5", TaskStatus.REVIEWING);
            when(taskRepository.findAll()).thenReturn(List.of(reviewing));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldPreserveWorktreeAndBranchFieldsOnRecovery() {
            TaskEntity coding = createTaskEntity("t6", TaskStatus.CODING, "task/t6/slug", "/worktrees/t6");
            when(taskRepository.findAll()).thenReturn(List.of(coding));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getBranchName()).isEqualTo("task/t6/slug");
            assertThat(saved.getWorktreePath()).isEqualTo("/worktrees/t6");
        }

        @Test
        void shouldMarkAllActiveStatusesAsFailed() {
            List<TaskEntity> activeTasks = List.of(
                    createTaskEntity("a1", TaskStatus.PLANNING),
                    createTaskEntity("a2", TaskStatus.TEST_WRITING),
                    createTaskEntity("a3", TaskStatus.TEST_REVIEWING),
                    createTaskEntity("a4", TaskStatus.CODING),
                    createTaskEntity("a5", TaskStatus.REVIEWING)
            );
            when(taskRepository.findAll()).thenReturn(activeTasks);
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(taskRepository, times(5)).save(any(TaskEntity.class));
            verify(taskEventRepository, times(5)).save(any(TaskEventEntity.class));
        }
    }

    @Nested
    class PendingTaskPreservation {

        @Test
        void shouldKeepPendingTaskUnchanged() {
            TaskEntity pending = createTaskEntity("t1", TaskStatus.PENDING);
            when(taskRepository.findAll()).thenReturn(List.of(pending));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(taskRepository, never()).save(any(TaskEntity.class));
        }

        @Test
        void shouldNotWriteRecoveryEventForPendingTask() {
            TaskEntity pending = createTaskEntity("t1", TaskStatus.PENDING);
            when(taskRepository.findAll()).thenReturn(List.of(pending));

            recoveryService.recoverAndStart();

            verify(taskEventRepository, never()).save(any(TaskEventEntity.class));
        }
    }

    @Nested
    class CompletedTaskPreservation {

        @Test
        void shouldKeepCompletedTaskUnchanged() {
            TaskEntity completed = createTaskEntity("t1", TaskStatus.COMPLETED);
            when(taskRepository.findAll()).thenReturn(List.of(completed));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(taskRepository, never()).save(any(TaskEntity.class));
        }

        @Test
        void shouldNotWriteRecoveryEventForCompletedTask() {
            TaskEntity completed = createTaskEntity("t1", TaskStatus.COMPLETED);
            when(taskRepository.findAll()).thenReturn(List.of(completed));

            recoveryService.recoverAndStart();

            verify(taskEventRepository, never()).save(any(TaskEventEntity.class));
        }
    }

    @Nested
    class RecoveryEvents {

        @Test
        void shouldWriteRecoveryEventForActiveTask() {
            TaskEntity coding = createTaskEntity("t1", TaskStatus.CODING);
            when(taskRepository.findAll()).thenReturn(List.of(coding));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEventEntity> eventCaptor = ArgumentCaptor.forClass(TaskEventEntity.class);
            verify(taskEventRepository).save(eventCaptor.capture());
            TaskEventEntity savedEvent = eventCaptor.getValue();
            assertThat(savedEvent.getTaskId()).isEqualTo("t1");
            assertThat(savedEvent.getEventType()).isEqualTo("RECOVERY");
            assertThat(savedEvent.getMessage()).contains("CODING");
            assertThat(savedEvent.getMessage()).contains("FAILED");
            assertThat(savedEvent.getMessage()).contains("daemon restarted during active execution");
        }

        @Test
        void shouldWriteRecoveryEventsForAllActiveTasks() {
            List<TaskEntity> tasks = List.of(
                    createTaskEntity("a1", TaskStatus.PLANNING),
                    createTaskEntity("a2", TaskStatus.TEST_WRITING),
                    createTaskEntity("p1", TaskStatus.PENDING),
                    createTaskEntity("c1", TaskStatus.COMPLETED)
            );
            when(taskRepository.findAll()).thenReturn(tasks);
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(taskEventRepository, times(2)).save(any(TaskEventEntity.class));
        }
    }

    @Nested
    class DependencyGraphRebuild {

        @Test
        void shouldRebuildDependencyGraphFromDatabaseAfterRecovery() {
            TaskEntity parent = createTaskEntity("parent", TaskStatus.PLANNING);
            TaskEntity child1 = createTaskEntity("c1", TaskStatus.COMPLETED);
            TaskEntity child2 = createTaskEntity("c2", TaskStatus.PENDING);
            child2.setDependsOn(List.of("c1"));

            when(taskRepository.findAll()).thenReturn(List.of(parent, child1, child2));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepository.findById("c1")).thenReturn(java.util.Optional.of(child1));
            when(taskRepository.findById("c2")).thenReturn(java.util.Optional.of(child2));

            recoveryService.recoverAndStart();

            DependencyTracker freshTracker = new DependencyTracker(taskRepository);
            assertThat(freshTracker.areDependenciesSatisfied("c2")).isTrue();
            assertThat(freshTracker.isBlockedByDependencies("c2")).isFalse();
        }

        @Test
        void shouldBlockDownstreamTasksWhenDependencyFailedByRecovery() {
            TaskEntity parent = createTaskEntity("parent", TaskStatus.PLANNING);
            TaskEntity depTask = createTaskEntity("dep1", TaskStatus.CODING);
            TaskEntity downstream = createTaskEntity("down1", TaskStatus.PENDING);
            downstream.setDependsOn(List.of("dep1"));

            when(taskRepository.findAll()).thenReturn(List.of(parent, depTask, downstream));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepository.findById("down1")).thenReturn(java.util.Optional.of(downstream));

            recoveryService.recoverAndStart();

            ArgumentCaptor<TaskEntity> saveCaptor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository, atLeastOnce()).save(saveCaptor.capture());
            TaskEntity recoveredDep = saveCaptor.getAllValues().stream()
                    .filter(e -> e.getId().equals("dep1"))
                    .findFirst()
                    .orElseThrow();
            assertThat(recoveredDep.getStatus()).isEqualTo(TaskStatus.FAILED);

            when(taskRepository.findById("dep1")).thenReturn(java.util.Optional.of(recoveredDep));

            DependencyTracker freshTracker = new DependencyTracker(taskRepository);
            assertThat(freshTracker.isBlockedByDependencies("down1")).isTrue();
            assertThat(freshTracker.hasFailedDependency("down1")).isTrue();
        }

        @Test
        void shouldAllowPendingDispatchAfterRecoveryForUnblockedTasks() {
            TaskEntity completed = createTaskEntity("dep1", TaskStatus.COMPLETED);
            TaskEntity pending = createTaskEntity("t1", TaskStatus.PENDING);
            pending.setDependsOn(List.of("dep1"));

            when(taskRepository.findAll()).thenReturn(List.of(completed, pending));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskRepository.findById("dep1")).thenReturn(java.util.Optional.of(completed));
            when(taskRepository.findById("t1")).thenReturn(java.util.Optional.of(pending));

            recoveryService.recoverAndStart();

            DependencyTracker freshTracker = new DependencyTracker(taskRepository);
            assertThat(freshTracker.areDependenciesSatisfied("t1")).isTrue();
            assertThat(freshTracker.isBlockedByDependencies("t1")).isFalse();
        }
    }

    @Nested
    class ResourceSnapshotChecks {

        @Test
        void shouldBuildResourceSnapshotWithWorktreeInfo() {
            TaskEntity task = createTaskEntity("t1", TaskStatus.COMPLETED, "task/t1/slug", "/worktrees/t1");
            when(taskRepository.findAll()).thenReturn(List.of(task));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(worktreeManager.branchExists("task/t1/slug")).thenReturn(true);
            when(worktreeManager.worktreeExists(any())).thenReturn(true);

            recoveryService.recoverAndStart();

            com.tddforge.service.ResourceSnapshot snapshot = recoveryService.getLastResourceSnapshot();
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.tasks()).hasSize(1);
            com.tddforge.service.ResourceSnapshot.TaskResourceStatus status = snapshot.tasks().get(0);
            assertThat(status.taskId()).isEqualTo("t1");
            assertThat(status.branchName()).isEqualTo("task/t1/slug");
            assertThat(status.worktreePath()).isEqualTo("/worktrees/t1");
            assertThat(status.branchExists()).isTrue();
            assertThat(status.worktreeExists()).isTrue();
        }

        @Test
        void shouldReportMissingBranchAndWorktree() {
            TaskEntity task = createTaskEntity("t2", TaskStatus.FAILED, "task/t2/slug", "/worktrees/t2");
            when(taskRepository.findAll()).thenReturn(List.of(task));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(worktreeManager.branchExists("task/t2/slug")).thenReturn(false);
            when(worktreeManager.worktreeExists(any())).thenReturn(false);

            recoveryService.recoverAndStart();

            com.tddforge.service.ResourceSnapshot snapshot = recoveryService.getLastResourceSnapshot();
            com.tddforge.service.ResourceSnapshot.TaskResourceStatus status = snapshot.tasks().get(0);
            assertThat(status.branchExists()).isFalse();
            assertThat(status.worktreeExists()).isFalse();
        }

        @Test
        void shouldNotCheckResourcesForTaskWithBlankBranchAndWorktree() {
            TaskEntity task = createTaskEntity("t3", TaskStatus.PENDING, "", "");
            when(taskRepository.findAll()).thenReturn(List.of(task));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(worktreeManager, never()).branchExists(any());
            verify(worktreeManager, never()).worktreeExists(any());

            com.tddforge.service.ResourceSnapshot snapshot = recoveryService.getLastResourceSnapshot();
            com.tddforge.service.ResourceSnapshot.TaskResourceStatus status = snapshot.tasks().get(0);
            assertThat(status.branchExists()).isFalse();
            assertThat(status.worktreeExists()).isFalse();
        }
    }

    @Nested
    class OrchestratorStartup {

        @Test
        void shouldStartOrchestratorAfterRecovery() {
            when(taskRepository.findAll()).thenReturn(List.of());
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(orchestrator).start();
        }

        @Test
        void shouldStartOrchestratorEvenWhenDatabaseUnavailable() {
            when(taskRepository.findAll()).thenThrow(new RuntimeException("DB error"));

            recoveryService.recoverAndStart();

            verify(orchestrator).start();
        }

        @Test
        void shouldSetEmptyResourceSnapshotWhenDatabaseUnavailable() {
            when(taskRepository.findAll()).thenThrow(new RuntimeException("DB error"));

            recoveryService.recoverAndStart();

            ResourceSnapshot snapshot = recoveryService.getLastResourceSnapshot();
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.tasks()).isEmpty();
        }
    }

    @Nested
    class MixedStatuses {

        @Test
        void shouldHandleMixOfAllStatuses() {
            List<TaskEntity> tasks = List.of(
                    createTaskEntity("p1", TaskStatus.PENDING),
                    createTaskEntity("pl1", TaskStatus.PLANNING),
                    createTaskEntity("tw1", TaskStatus.TEST_WRITING),
                    createTaskEntity("tr1", TaskStatus.TEST_REVIEWING),
                    createTaskEntity("co1", TaskStatus.CODING),
                    createTaskEntity("re1", TaskStatus.REVIEWING),
                    createTaskEntity("cp1", TaskStatus.COMPLETED),
                    createTaskEntity("f1", TaskStatus.FAILED),
                    createTaskEntity("c1", TaskStatus.CANCELLED),
                    createTaskEntity("na1", TaskStatus.NEEDS_ARBITRATION)
            );
            when(taskRepository.findAll()).thenReturn(tasks);
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            recoveryService.recoverAndStart();

            verify(taskRepository, times(5)).save(any(TaskEntity.class));
            verify(taskEventRepository, times(5)).save(any(TaskEventEntity.class));
        }
    }

    @Nested
    class ActiveStatuses {

        @Test
        void shouldDefineCorrectActiveStatuses() {
            Set<TaskStatus> active = StartupRecoveryService.getActiveStatuses();
            assertThat(active).containsExactlyInAnyOrder(
                    TaskStatus.PLANNING,
                    TaskStatus.TEST_WRITING,
                    TaskStatus.TEST_REVIEWING,
                    TaskStatus.CODING,
                    TaskStatus.REVIEWING
            );
        }

        @Test
        void activeStatusesShouldNotIncludePending() {
            assertThat(StartupRecoveryService.getActiveStatuses()).doesNotContain(TaskStatus.PENDING);
        }

        @Test
        void activeStatusesShouldNotIncludeCompleted() {
            assertThat(StartupRecoveryService.getActiveStatuses()).doesNotContain(TaskStatus.COMPLETED);
        }

        @Test
        void activeStatusesShouldNotIncludeFailed() {
            assertThat(StartupRecoveryService.getActiveStatuses()).doesNotContain(TaskStatus.FAILED);
        }

        @Test
        void activeStatusesShouldNotIncludeCancelled() {
            assertThat(StartupRecoveryService.getActiveStatuses()).doesNotContain(TaskStatus.CANCELLED);
        }

        @Test
        void activeStatusesShouldNotIncludeNeedsArbitration() {
            assertThat(StartupRecoveryService.getActiveStatuses()).doesNotContain(TaskStatus.NEEDS_ARBITRATION);
        }
    }
}
