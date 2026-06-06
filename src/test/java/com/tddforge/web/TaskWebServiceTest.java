package com.tddforge.web;

import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.domain.Task;
import com.tddforge.domain.TaskPriority;
import com.tddforge.domain.TaskStatus;
import com.tddforge.git.WorktreeManager;
import com.tddforge.git.WorktreeManagerException;
import com.tddforge.opencode.OpenCodeClient;
import com.tddforge.orchestrator.Orchestrator;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.service.StartupRecoveryService;
import com.tddforge.web.dto.OperationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskWebServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private AgentRunRepository agentRunRepository;
    @Mock private Orchestrator orchestrator;
    @Mock private OpenCodeClient openCodeClient;
    @Mock private WorktreeManager worktreeManager;
    @Mock private StartupRecoveryService startupRecoveryService;

    private RepoConfig repoConfig;
    private OrchestratorConfig orchestratorConfig;
    private TaskWebService service;

    @BeforeEach
    void setUp() {
        repoConfig = new RepoConfig();
        repoConfig.setPath("/tmp/test-repo");
        repoConfig.setBaseBranch("master");
        repoConfig.setWorktreeDir("/tmp/test-worktrees");

        orchestratorConfig = new OrchestratorConfig();
        orchestratorConfig.setMaxParallelTasks(3);

        service = new TaskWebService(
                taskRepository, agentRunRepository, orchestrator,
                openCodeClient, worktreeManager, orchestratorConfig, repoConfig,
                startupRecoveryService);
    }

    private TaskEntity createTaskEntity(String id, TaskStatus status) {
        Task task = new Task(id, "Test Task", "Description", "/tmp/test-repo");
        task.setStatus(status);
        task.setBranchName("task/" + id + "/test-task");
        task.setWorktreePath("/tmp/test-worktrees/" + id);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return TaskEntity.fromDomain(task);
    }

    private TaskEntity createChildTaskEntity(String id, String parentId, TaskStatus status) {
        TaskEntity entity = createTaskEntity(id, status);
        entity.setParentId(parentId);
        return entity;
    }

    private TaskEntity createCompletedTaskEntity(String id) {
        TaskEntity entity = createTaskEntity(id, TaskStatus.COMPLETED);
        entity.setCompletedAt(Instant.now());
        return entity;
    }

    @Nested
    class DispatchTask {

        @Test
        void shouldRejectDirectChildDispatch() {
            TaskEntity child = createChildTaskEntity("child-1", "parent-1", TaskStatus.PENDING);
            when(taskRepository.findById("child-1")).thenReturn(Optional.of(child));

            assertThatThrownBy(() -> service.dispatchTask("child-1"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .hasMessageContaining("Dispatch the parent task first");

            verify(orchestrator, never()).dispatchTask(any());
        }

        @Test
        void shouldDispatchExistingChildrenWhenParentIsDispatched() {
            TaskEntity parent = createTaskEntity("parent-1", TaskStatus.PENDING);
            TaskEntity low = createChildTaskEntity("child-low", "parent-1", TaskStatus.PENDING);
            low.setPriority(TaskPriority.LOW);
            TaskEntity high = createChildTaskEntity("child-high", "parent-1", TaskStatus.PENDING);
            high.setPriority(TaskPriority.HIGH);
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(low, high));
            when(orchestrator.dispatchTask("child-high")).thenReturn(true);
            when(orchestrator.dispatchTask("child-low")).thenReturn(true);

            OperationResponse response = service.dispatchTask("parent-1");

            assertThat(response.success()).isTrue();
            verify(orchestrator, never()).dispatchTask("parent-1");
            var inOrder = inOrder(orchestrator);
            inOrder.verify(orchestrator).dispatchTask("child-high");
            inOrder.verify(orchestrator).dispatchTask("child-low");
        }

        @Test
        void shouldResetRestartFailedParentAndChildrenBeforeDispatchingExistingChildren() {
            TaskEntity parent = createTaskEntity("parent-1", TaskStatus.FAILED);
            parent.setError(StartupRecoveryService.RECOVERY_ERROR_MESSAGE);
            TaskEntity child = createChildTaskEntity("child-1", "parent-1", TaskStatus.FAILED);
            child.setError(StartupRecoveryService.RECOVERY_ERROR_MESSAGE);
            when(taskRepository.findById("parent-1")).thenReturn(Optional.of(parent));
            when(taskRepository.findByParentId("parent-1")).thenReturn(List.of(child));
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(orchestrator.dispatchTask("child-1")).thenReturn(true);

            OperationResponse response = service.dispatchTask("parent-1");

            assertThat(response.success()).isTrue();
            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(TaskEntity::getStatus)
                    .containsExactly(TaskStatus.PENDING, TaskStatus.PENDING);
            assertThat(captor.getAllValues()).extracting(TaskEntity::getError)
                    .containsExactly(null, null);
            verify(orchestrator).dispatchTask("child-1");
        }
    }

    @Nested
    class ReviseTask {

        @Test
        void shouldAllowRevisingNeedsArbitrationTaskAndResetRetryState() {
            TaskEntity entity = createTaskEntity("r1", TaskStatus.NEEDS_ARBITRATION);
            entity.setTestRetryCount(3);
            entity.setCodeRetryCount(5);
            entity.setReviewPass(true);
            entity.setCompletedAt(Instant.now());
            entity.setError("Max code retries exceeded after Reviewer REQUEST_CHANGES");
            when(taskRepository.findById("r1")).thenReturn(Optional.of(entity));

            OperationResponse response = service.reviseTask("r1", "Use the simpler API boundary.");

            assertThat(response.success()).isTrue();
            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(saved.getUserFeedback()).isEqualTo("Use the simpler API boundary.");
            assertThat(saved.getTestRetryCount()).isZero();
            assertThat(saved.getCodeRetryCount()).isZero();
            assertThat(saved.isReviewPass()).isFalse();
            assertThat(saved.getCompletedAt()).isNull();
            assertThat(saved.getError()).contains("Max code retries exceeded");
        }

        @Test
        void shouldAllowRevisingFailedTask() {
            TaskEntity entity = createTaskEntity("r2", TaskStatus.FAILED);
            entity.setTestRetryCount(1);
            entity.setCodeRetryCount(2);
            entity.setError("TestReviewer extraction failed");
            when(taskRepository.findById("r2")).thenReturn(Optional.of(entity));

            service.reviseTask("r2", "The reviewer output means approve.");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(TaskStatus.PENDING);
            assertThat(saved.getUserFeedback()).isEqualTo("The reviewer output means approve.");
            assertThat(saved.getTestRetryCount()).isZero();
            assertThat(saved.getCodeRetryCount()).isZero();
        }

        @Test
        void shouldRejectRevisingRunningTask() {
            TaskEntity entity = createTaskEntity("r3", TaskStatus.CODING);
            when(taskRepository.findById("r3")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.reviseTask("r3", "Try again"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .hasMessageContaining("NEEDS_ARBITRATION or FAILED");
        }
    }

    @Nested
    class CleanTask {

        @Test
        void shouldRejectCleaningRunningTaskInPlanning() {
            TaskEntity entity = createTaskEntity("t1", TaskStatus.PLANNING);
            when(taskRepository.findById("t1")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t1"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .satisfies(ex -> {
                        InvalidTaskStateException itse = (InvalidTaskStateException) ex;
                        assertThat(itse.getOperation()).isEqualTo("clean");
                        assertThat(itse.getCurrentStatus()).isEqualTo(TaskStatus.PLANNING);
                    });
        }

        @Test
        void shouldRejectCleaningRunningTaskInTestWriting() {
            TaskEntity entity = createTaskEntity("t2", TaskStatus.TEST_WRITING);
            when(taskRepository.findById("t2")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t2"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningRunningTaskInCoding() {
            TaskEntity entity = createTaskEntity("t3", TaskStatus.CODING);
            when(taskRepository.findById("t3")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t3"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningRunningTaskInReviewing() {
            TaskEntity entity = createTaskEntity("t4", TaskStatus.REVIEWING);
            when(taskRepository.findById("t4")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t4"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningRunningTaskInTestReviewing() {
            TaskEntity entity = createTaskEntity("t5", TaskStatus.TEST_REVIEWING);
            when(taskRepository.findById("t5")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t5"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningPendingTask() {
            TaskEntity entity = createTaskEntity("t6", TaskStatus.PENDING);
            when(taskRepository.findById("t6")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t6"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .hasMessageContaining("Only COMPLETED, FAILED, CANCELLED, or NEEDS_ARBITRATION");
        }

        @Test
        void shouldRejectCleaningTestWriteFailedTask() {
            TaskEntity entity = createTaskEntity("t7", TaskStatus.TEST_WRITE_FAILED);
            when(taskRepository.findById("t7")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t7"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningTestReviewFailedTask() {
            TaskEntity entity = createTaskEntity("t8", TaskStatus.TEST_REVIEW_FAILED);
            when(taskRepository.findById("t8")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t8"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectCleaningReviewFailedTask() {
            TaskEntity entity = createTaskEntity("t9", TaskStatus.REVIEW_FAILED);
            when(taskRepository.findById("t9")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t9"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldAllowCleaningCompletedTask() {
            TaskEntity entity = createCompletedTaskEntity("t10");
            when(taskRepository.findById("t10")).thenReturn(Optional.of(entity));

            OperationResponse response = service.cleanTask("t10");

            assertThat(response.success()).isTrue();
            verify(worktreeManager).removeWorktree(Path.of("/tmp/test-worktrees/t10"));
            verify(worktreeManager).deleteBranch("task/t10/test-task");
        }

        @Test
        void shouldAllowCleaningFailedTask() {
            TaskEntity entity = createTaskEntity("t11", TaskStatus.FAILED);
            when(taskRepository.findById("t11")).thenReturn(Optional.of(entity));

            OperationResponse response = service.cleanTask("t11");

            assertThat(response.success()).isTrue();
            verify(worktreeManager).removeWorktree(any());
            verify(worktreeManager).deleteBranch(any());
        }

        @Test
        void shouldAllowCleaningCancelledTask() {
            TaskEntity entity = createTaskEntity("t12", TaskStatus.CANCELLED);
            when(taskRepository.findById("t12")).thenReturn(Optional.of(entity));

            OperationResponse response = service.cleanTask("t12");

            assertThat(response.success()).isTrue();
        }

        @Test
        void shouldAllowCleaningNeedsArbitrationTask() {
            TaskEntity entity = createTaskEntity("t13", TaskStatus.NEEDS_ARBITRATION);
            when(taskRepository.findById("t13")).thenReturn(Optional.of(entity));

            OperationResponse response = service.cleanTask("t13");

            assertThat(response.success()).isTrue();
        }

        @Test
        void shouldPersistClearedWorktreePathAndBranchName() {
            TaskEntity entity = createCompletedTaskEntity("t20");
            when(taskRepository.findById("t20")).thenReturn(Optional.of(entity));

            service.cleanTask("t20");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getWorktreePath()).isEmpty();
            assertThat(saved.getBranchName()).isEmpty();
        }

        @Test
        void shouldRefreshUpdatedAtOnClean() {
            TaskEntity entity = createCompletedTaskEntity("t20b");
            Instant oldUpdatedAt = entity.getUpdatedAt();
            when(taskRepository.findById("t20b")).thenReturn(Optional.of(entity));

            service.cleanTask("t20b");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);
        }

        @Test
        void shouldSkipWorktreeRemovalWhenPathIsBlank() {
            TaskEntity entity = createTaskEntity("t21", TaskStatus.COMPLETED);
            entity.setWorktreePath("");
            when(taskRepository.findById("t21")).thenReturn(Optional.of(entity));

            service.cleanTask("t21");

            verify(worktreeManager, never()).removeWorktree(any());
            verify(worktreeManager).deleteBranch(any());
        }

        @Test
        void shouldSkipBranchDeletionWhenBranchIsBlank() {
            TaskEntity entity = createTaskEntity("t22", TaskStatus.COMPLETED);
            entity.setBranchName("");
            when(taskRepository.findById("t22")).thenReturn(Optional.of(entity));

            service.cleanTask("t22");

            verify(worktreeManager).removeWorktree(any());
            verify(worktreeManager, never()).deleteBranch(any());
        }

        @Test
        void shouldPropagateWorktreePathBoundaryViolation() {
            TaskEntity entity = createTaskEntity("t23", TaskStatus.COMPLETED);
            entity.setWorktreePath("/etc/passwd");
            when(taskRepository.findById("t23")).thenReturn(Optional.of(entity));
            doThrow(new WorktreeManagerException("Path /etc/passwd is not under configured worktree_dir"))
                    .when(worktreeManager).removeWorktree(Path.of("/etc/passwd"));

            assertThatThrownBy(() -> service.cleanTask("t23"))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("not under configured worktree_dir");
        }

        @Test
        void shouldRejectDeletingNonTaskBranch() {
            TaskEntity entity = createTaskEntity("t24", TaskStatus.COMPLETED);
            entity.setBranchName("master");
            when(taskRepository.findById("t24")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.cleanTask("t24"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .hasMessageContaining("Refusing to delete non-task branch")
                    .hasMessageContaining("master");
        }

        @Test
        void shouldThrowWhenTaskNotFound() {
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cleanTask("nonexistent"))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    @Nested
    class PublishTask {

        @Test
        void shouldRejectPublishingNonCompletedTask() {
            TaskEntity entity = createTaskEntity("p1", TaskStatus.CODING);
            when(taskRepository.findById("p1")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.publishTask("p1"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .satisfies(ex -> {
                        InvalidTaskStateException itse = (InvalidTaskStateException) ex;
                        assertThat(itse.getOperation()).isEqualTo("publish");
                        assertThat(itse.getCurrentStatus()).isEqualTo(TaskStatus.CODING);
                    });
        }

        @Test
        void shouldRejectPublishingPendingTask() {
            TaskEntity entity = createTaskEntity("p2", TaskStatus.PENDING);
            when(taskRepository.findById("p2")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.publishTask("p2"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectPublishingFailedTask() {
            TaskEntity entity = createTaskEntity("p3", TaskStatus.FAILED);
            when(taskRepository.findById("p3")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.publishTask("p3"))
                    .isInstanceOf(InvalidTaskStateException.class);
        }

        @Test
        void shouldRejectPublishingTaskWithNoBranchName() {
            TaskEntity entity = createCompletedTaskEntity("p4");
            entity.setBranchName("");
            when(taskRepository.findById("p4")).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.publishTask("p4"))
                    .isInstanceOf(InvalidTaskStateException.class)
                    .hasMessageContaining("no branch name");
        }

        @Test
        void shouldPublishCompletedTaskSuccessfully() {
            TaskEntity entity = createCompletedTaskEntity("p10");
            when(taskRepository.findById("p10")).thenReturn(Optional.of(entity));
            when(worktreeManager.publish("task/p10/test-task")).thenReturn("push output");

            OperationResponse response = service.publishTask("p10");

            assertThat(response.success()).isTrue();
            verify(worktreeManager).publish("task/p10/test-task");
        }

        @Test
        void shouldPersistPublishedAtAfterSuccessfulPublish() {
            TaskEntity entity = createCompletedTaskEntity("p20");
            Instant oldUpdatedAt = entity.getUpdatedAt();
            when(taskRepository.findById("p20")).thenReturn(Optional.of(entity));
            when(worktreeManager.publish("task/p20/test-task")).thenReturn("push output");

            Instant before = Instant.now();
            service.publishTask("p20");

            ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
            verify(taskRepository).save(captor.capture());
            TaskEntity saved = captor.getValue();
            assertThat(saved.getPublishedAt()).isNotNull();
            assertThat(saved.getPublishedAt()).isAfterOrEqualTo(before);
            assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);
        }

        @Test
        void shouldThrowWhenTaskNotFound() {
            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishTask("nonexistent"))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }
}
