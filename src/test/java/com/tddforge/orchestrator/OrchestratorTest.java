package com.tddforge.orchestrator;

import com.tddforge.config.OrchestratorConfig;
import com.tddforge.domain.*;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.service.DependencyTracker;
import com.tddforge.service.TaskExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrchestratorTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private DependencyTracker dependencyTracker;
    @Mock
    private TaskExecutionService taskExecutionService;

    private OrchestratorConfig orchestratorConfig;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestratorConfig = new OrchestratorConfig();
        orchestratorConfig.setMaxParallelTasks(3);
        orchestrator = new Orchestrator(
                taskRepository, taskEventRepository,
                dependencyTracker, taskExecutionService, orchestratorConfig
        );
    }

    private TaskEntity createPendingTaskEntity(String id) {
        return createTaskEntity(id, TaskStatus.PENDING, null, List.of());
    }

    private TaskEntity createTaskEntity(String id, TaskStatus status, String parentId, List<String> dependsOn) {
        TaskEntity entity = new TaskEntity();
        entity.setId(id);
        entity.setTitle("Task " + id);
        entity.setDescription("Description for " + id);
        entity.setStatus(status);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setTaskMode("develop");
        entity.setParentId(parentId);
        entity.setDependsOn(new ArrayList<>(dependsOn));
        entity.setRepoPath("/repo");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    @Nested
    class StartStop {

        @Test
        void shouldStartAndStopOrchestrator() {
            assertThat(orchestrator.isStarted()).isFalse();
            orchestrator.start();
            assertThat(orchestrator.isStarted()).isTrue();
            orchestrator.stop();
            assertThat(orchestrator.isStarted()).isFalse();
        }

        @Test
        void shouldNotStartTwice() {
            orchestrator.start();
            orchestrator.start();
            assertThat(orchestrator.isStarted()).isTrue();
            orchestrator.stop();
        }

        @Test
        void shouldNotDispatchWhenNotStarted() {
            TaskEntity task = createPendingTaskEntity("t1");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(taskEventRepository.save(any())).thenReturn(null);
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isFalse();
        }

        @Test
        void shouldNotDispatchAfterStop() {
            orchestrator.start();
            orchestrator.stop();

            TaskEntity task = createPendingTaskEntity("t1");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isFalse();
        }
    }

    @Nested
    class MaxParallelTasks {

        @Test
        void shouldRespectMaxParallelTasksLimit() throws Exception {
            orchestratorConfig.setMaxParallelTasks(2);
            orchestrator.start();

            CountDownLatch executionStarted = new CountDownLatch(2);
            CountDownLatch allowCompletion = new CountDownLatch(1);
            AtomicInteger executionCount = new AtomicInteger(0);

            when(taskRepository.findById(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                return Optional.of(createPendingTaskEntity(id));
            });
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                executionStarted.countDown();
                executionCount.incrementAndGet();
                allowCompletion.await(30, TimeUnit.SECONDS);
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(invocation.getArgument(0), "title", "desc", "/repo"));
            });

            boolean r1 = orchestrator.dispatchTask("t1");
            boolean r2 = orchestrator.dispatchTask("t2");
            boolean r3 = orchestrator.dispatchTask("t3");

            assertThat(r1).isTrue();
            assertThat(r2).isTrue();
            assertThat(r3).isTrue();

            assertThat(executionStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(orchestrator.getRunningCount()).isEqualTo(2);
            assertThat(orchestrator.getPendingCount()).isEqualTo(1);

            allowCompletion.countDown();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(orchestrator.getRunningCount()).isLessThanOrEqualTo(2);
            });

            orchestrator.stop();
        }

        @Test
        void shouldNotRunMoreThanMaxParallelTasksSimultaneously() throws Exception {
            int maxParallel = 3;
            orchestratorConfig.setMaxParallelTasks(maxParallel);
            orchestrator.start();

            CountDownLatch executionStarted = new CountDownLatch(maxParallel);
            CountDownLatch allowCompletion = new CountDownLatch(1);

            when(taskRepository.findById(any())).thenAnswer(invocation ->
                    Optional.of(createPendingTaskEntity(invocation.getArgument(0))));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                executionStarted.countDown();
                allowCompletion.await(60, TimeUnit.SECONDS);
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(invocation.getArgument(0), "title", "desc", "/repo"));
            });

            for (int i = 0; i < maxParallel + 5; i++) {
                orchestrator.dispatchTask("task-" + i);
            }

            assertThat(executionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(orchestrator.getRunningCount()).isEqualTo(maxParallel);
            assertThat(orchestrator.getPendingCount()).isEqualTo(5);

            allowCompletion.countDown();
            orchestrator.stop();
        }
    }

    @Nested
    class DuplicateDispatch {

        @Test
        void shouldPreventDuplicateDispatch() {
            orchestrator.start();

            CountDownLatch allowCompletion = new CountDownLatch(1);

            when(taskRepository.findById(any())).thenAnswer(invocation ->
                    Optional.of(createPendingTaskEntity(invocation.getArgument(0))));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                allowCompletion.await(60, TimeUnit.SECONDS);
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(invocation.getArgument(0), "title", "desc", "/repo"));
            });

            boolean first = orchestrator.dispatchTask("t1");
            boolean second = orchestrator.dispatchTask("t1");

            assertThat(first).isTrue();
            assertThat(second).isFalse();

            allowCompletion.countDown();
            orchestrator.stop();
        }
    }

    @Nested
    class PendingQueue {

        @Test
        void shouldQueueTaskWhenCapacityFullAndDispatchAfterCompletion() throws Exception {
            orchestratorConfig.setMaxParallelTasks(1);
            orchestrator.start();

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch allowFirstCompletion = new CountDownLatch(1);
            AtomicInteger secondExecutions = new AtomicInteger(0);

            when(taskRepository.findById(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                TaskEntity entity = createPendingTaskEntity(id);
                if (id.equals("t2-completed")) {
                    entity.setStatus(TaskStatus.COMPLETED);
                }
                return Optional.of(entity);
            });
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                if (id.equals("t1")) {
                    firstStarted.countDown();
                    allowFirstCompletion.await(30, TimeUnit.SECONDS);
                } else {
                    secondExecutions.incrementAndGet();
                }
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(id, "title", "desc", "/repo"));
            });

            orchestrator.dispatchTask("t1");
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(orchestrator.getRunningCount()).isEqualTo(1);

            boolean r2 = orchestrator.dispatchTask("t2");
            assertThat(r2).isTrue();
            assertThat(orchestrator.getPendingCount()).isEqualTo(1);

            allowFirstCompletion.countDown();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(secondExecutions.get()).isGreaterThanOrEqualTo(1);
            });

            orchestrator.stop();
        }

        @Test
        void shouldNotDispatchPendingTaskThatBecomesCancelled() throws Exception {
            orchestratorConfig.setMaxParallelTasks(1);
            orchestrator.start();

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch allowFirstCompletion = new CountDownLatch(1);

            TaskEntity t1Entity = createPendingTaskEntity("t1");
            TaskEntity t2Entity = createPendingTaskEntity("t2");
            TaskEntity t2Cancelled = createTaskEntity("t2", TaskStatus.CANCELLED, null, List.of());

            when(taskRepository.findById("t1")).thenReturn(Optional.of(t1Entity));
            when(taskRepository.findById("t2"))
                    .thenReturn(Optional.of(t2Entity));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask("t1")).thenAnswer(invocation -> {
                firstStarted.countDown();
                allowFirstCompletion.await(30, TimeUnit.SECONDS);
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task("t1", "title", "desc", "/repo"));
            });

            orchestrator.dispatchTask("t1");
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

            orchestrator.dispatchTask("t2");
            assertThat(orchestrator.getPendingCount()).isGreaterThanOrEqualTo(1);

            when(taskRepository.findById("t2")).thenReturn(Optional.of(t2Cancelled));

            allowFirstCompletion.countDown();

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(orchestrator.getPendingCount()).isEqualTo(0);
            });

            verify(taskExecutionService, never()).executeTask("t2");

            orchestrator.stop();
        }

@Test
        void shouldNotDispatchPendingTaskThatBecomesBlocked() throws Exception {
            orchestratorConfig.setMaxParallelTasks(1);
            orchestrator.start();

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch allowFirstCompletion = new CountDownLatch(1);

            TaskEntity t1Entity = createPendingTaskEntity("t1");
            TaskEntity t2Entity = createPendingTaskEntity("t2");

            when(taskRepository.findById("t1")).thenReturn(Optional.of(t1Entity));
            when(taskRepository.findById("t2")).thenReturn(Optional.of(t2Entity));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);
            when(dependencyTracker.isBlockedByDependencies("t2"))
                    .thenReturn(false)
                    .thenReturn(true);
            when(taskExecutionService.executeTask("t1")).thenAnswer(invocation -> {
                firstStarted.countDown();
                allowFirstCompletion.await(30, TimeUnit.SECONDS);
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task("t1", "title", "desc", "/repo"));
            });

            orchestrator.dispatchTask("t1");
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

            boolean r2 = orchestrator.dispatchTask("t2");
            assertThat(r2).isTrue();
            assertThat(orchestrator.getPendingCount()).isGreaterThanOrEqualTo(1);

            allowFirstCompletion.countDown();

            Thread.sleep(500);

            verify(taskExecutionService, never()).executeTask("t2");

            assertThat(orchestrator.getPendingCount()).isGreaterThanOrEqualTo(0);

            orchestrator.stop();
        }
    }

    @Nested
    class AutoRefill {

        @Test
        void shouldAutoRefillPendingQueueAfterTaskCompletion() throws Exception {
            orchestratorConfig.setMaxParallelTasks(1);
            orchestrator.start();

            CountDownLatch t1Started = new CountDownLatch(1);
            CountDownLatch allowT1Completion = new CountDownLatch(1);
            AtomicInteger t2ExecutionCount = new AtomicInteger(0);

            when(taskRepository.findById(any())).thenAnswer(invocation ->
                    Optional.of(createPendingTaskEntity(invocation.getArgument(0))));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                if (id.equals("t1")) {
                    t1Started.countDown();
                    allowT1Completion.await(30, TimeUnit.SECONDS);
                } else if (id.equals("t2")) {
                    t2ExecutionCount.incrementAndGet();
                }
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(id, "title", "desc", "/repo"));
            });

            orchestrator.dispatchTask("t1");
            assertThat(t1Started.await(5, TimeUnit.SECONDS)).isTrue();

            orchestrator.dispatchTask("t2");
            assertThat(orchestrator.getPendingCount()).isEqualTo(1);

            allowT1Completion.countDown();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(t2ExecutionCount.get()).isGreaterThanOrEqualTo(1);
            });

            orchestrator.stop();
        }

        @Test
        void shouldRefillMultipleSlotsWhenMultipleTasksComplete() throws Exception {
            orchestratorConfig.setMaxParallelTasks(2);
            orchestrator.start();

            CountDownLatch firstBatchStarted = new CountDownLatch(2);
            CountDownLatch allowFirstBatchCompletion = new CountDownLatch(1);
            Set<String> executedTaskIds = ConcurrentHashMap.newKeySet();

            when(taskRepository.findById(any())).thenAnswer(invocation ->
                    Optional.of(createPendingTaskEntity(invocation.getArgument(0))));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                executedTaskIds.add(id);
                if (id.equals("t1") || id.equals("t2")) {
                    firstBatchStarted.countDown();
                    allowFirstBatchCompletion.await(30, TimeUnit.SECONDS);
                }
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(id, "title", "desc", "/repo"));
            });

            orchestrator.dispatchTask("t1");
            orchestrator.dispatchTask("t2");
            assertThat(firstBatchStarted.await(5, TimeUnit.SECONDS)).isTrue();

            orchestrator.dispatchTask("t3");
            orchestrator.dispatchTask("t4");
            assertThat(orchestrator.getPendingCount()).isEqualTo(2);

            allowFirstBatchCompletion.countDown();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(executedTaskIds).containsExactlyInAnyOrder("t1", "t2", "t3", "t4");
            });

            orchestrator.stop();
        }
    }

    @Nested
    class CancelledTask {

        @Test
        void shouldNotDispatchCancelledTask() {
            orchestrator.start();

            TaskEntity task = createTaskEntity("t1", TaskStatus.CANCELLED, null, List.of());
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isFalse();

            verify(taskExecutionService, never()).executeTask(any());

            orchestrator.stop();
        }
    }

    @Nested
    class DependencyBlocked {

        @Test
        void shouldNotDispatchTaskBlockedByDependencies() {
            orchestrator.start();

            TaskEntity task = createPendingTaskEntity("t1");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(true);

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isFalse();

            verify(taskExecutionService, never()).executeTask(any());

            orchestrator.stop();
        }

        @Test
        void shouldNotPutDependencyBlockedTaskIntoPendingQueue() {
            orchestrator.start();

            TaskEntity task = createPendingTaskEntity("t1");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(true);

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isFalse();
            assertThat(orchestrator.getPendingCount()).isEqualTo(0);

            orchestrator.stop();
        }
    }

    @Nested
    class NonRunnableStatus {

        @Test
        void shouldNotDispatchTaskWithNonPendingStatus() {
            orchestrator.start();

            for (TaskStatus status : TaskStatus.values()) {
                if (status == TaskStatus.PENDING) continue;
                TaskEntity task = createTaskEntity("t-" + status.name(), status, null, List.of());
                when(taskRepository.findById("t-" + status.name())).thenReturn(Optional.of(task));
                when(dependencyTracker.isBlockedByDependencies("t-" + status.name())).thenReturn(false);

                boolean result = orchestrator.dispatchTask("t-" + status.name());
                assertThat(result).as("Status %s should not be dispatchable", status).isFalse();
            }

            verify(taskExecutionService, never()).executeTask(any());

            orchestrator.stop();
        }

        @Test
        void shouldDispatchPENDINGTask() {
            orchestrator.start();

            TaskEntity task = createPendingTaskEntity("t1");
            when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);
            when(taskExecutionService.executeTask("t1")).thenReturn(
                    new TaskExecutionService.ExecutionOutcome.Success(new Task("t1", "t", "d", "/repo")));

            boolean result = orchestrator.dispatchTask("t1");
            assertThat(result).isTrue();

            orchestrator.stop();
        }
    }

    @Nested
    class TaskNotFound {

        @Test
        void shouldReturnFalseWhenTaskNotFound() {
            orchestrator.start();

            when(taskRepository.findById("nonexistent")).thenReturn(Optional.empty());

            boolean result = orchestrator.dispatchTask("nonexistent");
            assertThat(result).isFalse();

            verify(taskExecutionService, never()).executeTask(any());

            orchestrator.stop();
        }
    }

    @Nested
    class TaskCompletion {

        @Test
        void shouldRemoveTaskFromRunningAfterCompletion() throws Exception {
            orchestrator.start();

            when(taskRepository.findById("t1")).thenReturn(Optional.of(createPendingTaskEntity("t1")));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);
            when(taskExecutionService.executeTask("t1")).thenReturn(
                    new TaskExecutionService.ExecutionOutcome.Success(new Task("t1", "t", "d", "/repo")));

            orchestrator.dispatchTask("t1");

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(orchestrator.getRunningCount()).isEqualTo(0);
            });

            orchestrator.stop();
        }

        @Test
        void shouldRemoveTaskFromRunningAfterExecutionException() throws Exception {
            orchestrator.start();

            when(taskRepository.findById("t1")).thenReturn(Optional.of(createPendingTaskEntity("t1")));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);
            when(taskExecutionService.executeTask("t1")).thenThrow(new RuntimeException("execution failed"));

            orchestrator.dispatchTask("t1");

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(orchestrator.getRunningCount()).isEqualTo(0);
            });

            orchestrator.stop();
        }
    }

    @Nested
    class ConcurrencyCap {

        @Test
        void shouldNeverExceedMaxParallelTasks() throws Exception {
            int maxParallel = 3;
            orchestratorConfig.setMaxParallelTasks(maxParallel);
            orchestrator.start();

            int totalTasks = 10;
            CountDownLatch allStarted = new CountDownLatch(maxParallel);
            CountDownLatch allowCompletion = new CountDownLatch(1);
            AtomicInteger peakRunning = new AtomicInteger(0);
            AtomicInteger currentRunning = new AtomicInteger(0);

            when(taskRepository.findById(any())).thenAnswer(invocation ->
                    Optional.of(createPendingTaskEntity(invocation.getArgument(0))));
            when(dependencyTracker.isBlockedByDependencies(any())).thenReturn(false);
            when(taskExecutionService.executeTask(any())).thenAnswer(invocation -> {
                int running = currentRunning.incrementAndGet();
                peakRunning.updateAndGet(p -> Math.max(p, running));
                allStarted.countDown();
                allowCompletion.await(60, TimeUnit.SECONDS);
                currentRunning.decrementAndGet();
                return new TaskExecutionService.ExecutionOutcome.Success(
                        new Task(invocation.getArgument(0), "title", "desc", "/repo"));
            });

            for (int i = 0; i < totalTasks; i++) {
                orchestrator.dispatchTask("task-" + i);
            }

            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peakRunning.get()).isLessThanOrEqualTo(maxParallel);

            allowCompletion.countDown();
            orchestrator.stop();
        }
    }

    @Nested
    class ReDispatchAfterCompletion {

        @Test
        void shouldAllowReDispatchOfSameTaskAfterCompletion() throws Exception {
            orchestratorConfig.setMaxParallelTasks(1);
            orchestrator.start();

            when(taskRepository.findById("t1")).thenReturn(Optional.of(createPendingTaskEntity("t1")));
            when(dependencyTracker.isBlockedByDependencies("t1")).thenReturn(false);
            when(taskExecutionService.executeTask("t1")).thenReturn(
                    new TaskExecutionService.ExecutionOutcome.Success(new Task("t1", "t", "d", "/repo")));

            boolean first = orchestrator.dispatchTask("t1");
            assertThat(first).isTrue();

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(orchestrator.getRunningCount()).isEqualTo(0));

            boolean second = orchestrator.dispatchTask("t1");
            assertThat(second).isTrue();

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(orchestrator.getRunningCount()).isEqualTo(0));

            orchestrator.stop();
        }
    }
}