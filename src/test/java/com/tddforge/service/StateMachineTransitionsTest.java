package com.tddforge.service;

import com.tddforge.agent.*;
import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.config.RuntimeModelConfig;
import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.persistence.AgentRunEntity;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StateMachineTransitionsTest {

    @Mock private TaskRepository taskRepository;
    @Mock private AgentRunRepository agentRunRepository;
    @Mock private TaskEventRepository taskEventRepository;
    @Mock private WorktreeManager worktreeManager;
    @Mock private PlannerAgent plannerAgent;
    @Mock private TestWriterAgent testWriterAgent;
    @Mock private TestReviewerAgent testReviewerAgent;
    @Mock private CoderAgent coderAgent;
    @Mock private ReviewerAgent reviewerAgent;

    private AgentOutputExtractor realExtractor;
    private PlannerService plannerService;
    private OpencodeConfig opencodeConfig;
    private RuntimeModelConfig runtimeModelConfig;
    private OrchestratorConfig orchestratorConfig;
    private RepoConfig repoConfig;
    private DependencyTracker dependencyTracker;
    private TaskExecutionService service;

    @BeforeEach
    void setUp() {
        realExtractor = new AgentOutputExtractor();
        PlannerResultValidator validator = new PlannerResultValidator();
        AtomicInteger idCounter = new AtomicInteger(0);
        PlannerResultProcessor.IdGenerator idGenerator = () -> String.format("child-%032d", idCounter.incrementAndGet());
        PlannerResultProcessor processor = new PlannerResultProcessor(idGenerator);
        plannerService = new PlannerService(realExtractor, validator, processor);

        opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("/tmp/test-config.json");
        com.tddforge.config.ModelSpec modelSpec = new com.tddforge.config.ModelSpec();
        modelSpec.setModel("test-model");
        opencodeConfig.setPlanner(modelSpec);
        opencodeConfig.setTestWriter(modelSpec);
        opencodeConfig.setTestReviewer(modelSpec);
        opencodeConfig.setCoderDefault(modelSpec);
        opencodeConfig.setReviewers(List.of(modelSpec));

        orchestratorConfig = new OrchestratorConfig();
        orchestratorConfig.setMaxTestRetries(2);
        orchestratorConfig.setMaxCodeRetries(4);

        repoConfig = new RepoConfig();
        repoConfig.setPath("/repo");
        repoConfig.setBaseBranch("master");
        repoConfig.setWorktreeDir("/worktrees");

        dependencyTracker = new DependencyTracker(taskRepository);

        runtimeModelConfig = new RuntimeModelConfig(opencodeConfig);
        runtimeModelConfig.init();

        service = new TaskExecutionService(
                taskRepository, agentRunRepository, taskEventRepository,
                plannerAgent, testWriterAgent, testReviewerAgent, coderAgent, reviewerAgent,
                plannerService, dependencyTracker, worktreeManager,
                orchestratorConfig, opencodeConfig, runtimeModelConfig, repoConfig
        );
    }

    private Task createDefaultTask() {
        Task task = new Task("task-1", "Test Task", "Test Description", "/repo");
        task.setBranchName("task-1/test-task");
        task.setWorktreePath("/worktrees/task-1");
        task.setMaxTestRetries(2);
        task.setMaxCodeRetries(4);
        return task;
    }

    private AgentRun createAgentRun(String output, int exitCode) {
        return new AgentRun("run-1", "task-1", "test-type", "test-model",
                null, null, "prompt", output, exitCode, 100L, "session-1", 0, Instant.now());
    }

    private String ndjsonWithText(String text) {
        return "{\"type\":\"step_start\",\"step_start\":{\"type\":\"thinking\"}}\n" +
                "{\"type\":\"text\",\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n" +
                "{\"type\":\"step_finish\",\"step_finish\":{\"reason\":\"stop\"}}";
    }

    private void setupTaskSaveWithReload(Task task) {
        AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
        lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            latestEntity.set(entity);
            return entity;
        });
        lenient().when(taskRepository.findById("task-1")).thenAnswer(invocation ->
                Optional.of(latestEntity.get()));
        lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setupPlannerMock(String plannerJson) {
        AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
        ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
        when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
    }

    private void setupWorktreeMock() {
        when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
        when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));
    }

    private void setupTestWriterMock(String classification, String commitHash) {
        AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter " + classification), 0);
        ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                new TestWriterResult("Test summary", "mvn test", classification, commitHash, List.of("Test.java")), "");
        when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));
    }

    private void setupTestReviewerMock(ReviewVerdict verdict, String feedback) {
        String text = verdict.name() + "\n" + feedback;
        AgentRun trRun = createAgentRun(ndjsonWithText(text), 0);
        ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                new TestReviewerResult(verdict, feedback), "");
        when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));
    }

    private void setupCoderMock() {
        AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
        ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of("Main.java")), "");
        when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));
    }

    private void setupReviewerMock(ReviewVerdict verdict, String feedback, String category) {
        String text = verdict.name() + "\n" + feedback;
        AgentRun reviewerRun = createAgentRun(ndjsonWithText(text), 0);
        ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                new ReviewerResult("reviewer-1", verdict, feedback, category), "");
        when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));
    }

    @Nested
    class NormalFlow {

        @Test
        void shouldTransitionThroughAllStatesInHappyPath() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good tests.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "Looks good.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.isReviewPass()).isTrue();
            assertThat(result.getCompletedAt()).isNotNull();

            verify(plannerAgent).run(any());
            verify(testWriterAgent).run(any());
            verify(testReviewerAgent).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent).run(any());
        }

        @Test
        void shouldCompleteSuccessfullyWithPlannerOutput() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Overall objective: Fix bug. Step 1: Fix it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good tests.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "Looks good.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.isReviewPass()).isTrue();
        }
    }

    @Nested
    class TerminalStateHandling {

        @Test
        void shouldSilentlyReExecuteWhenTaskAlreadyCompleted() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(Instant.now());
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                    .isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void shouldSilentlyReExecuteWhenTaskAlreadyFailed() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.FAILED);
            task.setError("Previous failure");
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                    .isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void shouldSilentlyReExecuteWhenTaskAlreadyNeedsArbitration() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.NEEDS_ARBITRATION);
            task.setError("Max retries exceeded");
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                    .isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void shouldRejectCancelledAndExitImmediatelyWithoutRunningAgents() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.CANCELLED);
            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent, never()).run(any());
            verify(testWriterAgent, never()).run(any());
            verify(coderAgent, never()).run(any());
            verify(reviewerAgent, never()).run(any());
        }

        @Test
        void shouldReturnFailedForUnexpectedStatusInMainLoop() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twRun = createAgentRun(ndjsonWithText("INVALID"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            java.util.concurrent.atomic.AtomicBoolean injectedUnexpected = new java.util.concurrent.atomic.AtomicBoolean(false);
            when(taskRepository.findById("task-1")).thenAnswer(invocation -> {
                TaskEntity entity = latestEntity.get();
                if (!injectedUnexpected.get() && entity.getStatus() == TaskStatus.TEST_WRITE_FAILED) {
                    entity.setStatus(TaskStatus.PLANNING);
                    injectedUnexpected.set(true);
                }
                return Optional.of(entity);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);
            TaskExecutionService.ExecutionOutcome.Failed failed = (TaskExecutionService.ExecutionOutcome.Failed) outcome;
            assertThat(failed.error()).contains("Unexpected status");
        }
    }

    @Nested
    class DependencyBlockedBeforeExecution {

        @Test
        void shouldFailWhenDependencyIsNotCompleted() {
            Task task = createDefaultTask();
            task.setDependsOn(List.of("dep-1"));
            TaskEntity depEntity = new TaskEntity();
            depEntity.setId("dep-1");
            depEntity.setTitle("Dependency");
            depEntity.setDescription("Desc");
            depEntity.setStatus(TaskStatus.CODING);
            depEntity.setRepoPath("/repo");
            depEntity.setCreatedAt(Instant.now());
            depEntity.setUpdatedAt(Instant.now());

            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));
            when(taskRepository.findById("dep-1")).thenReturn(Optional.of(depEntity));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);
            TaskExecutionService.ExecutionOutcome.Failed failed = (TaskExecutionService.ExecutionOutcome.Failed) outcome;
            assertThat(failed.error()).contains("dep-1");

            verify(plannerAgent, never()).run(any());
        }

        @Test
        void shouldFailWhenDependencyIsFailed() {
            Task task = createDefaultTask();
            task.setDependsOn(List.of("dep-1"));
            TaskEntity depEntity = new TaskEntity();
            depEntity.setId("dep-1");
            depEntity.setTitle("Failed Dep");
            depEntity.setDescription("Desc");
            depEntity.setStatus(TaskStatus.FAILED);
            depEntity.setRepoPath("/repo");
            depEntity.setCreatedAt(Instant.now());
            depEntity.setUpdatedAt(Instant.now());

            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));
            when(taskRepository.findById("dep-1")).thenReturn(Optional.of(depEntity));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);

            verify(plannerAgent, never()).run(any());
        }

        @Test
        void shouldFailWhenDependencyNeedsArbitration() {
            Task task = createDefaultTask();
            task.setDependsOn(List.of("dep-1"));
            TaskEntity depEntity = new TaskEntity();
            depEntity.setId("dep-1");
            depEntity.setTitle("Arb Dep");
            depEntity.setDescription("Desc");
            depEntity.setStatus(TaskStatus.NEEDS_ARBITRATION);
            depEntity.setRepoPath("/repo");
            depEntity.setCreatedAt(Instant.now());
            depEntity.setUpdatedAt(Instant.now());

            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));
            when(taskRepository.findById("dep-1")).thenReturn(Optional.of(depEntity));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);

            verify(plannerAgent, never()).run(any());
        }

        @Test
        void shouldProceedWhenAllDependenciesCompleted() {
            Task task = createDefaultTask();
            task.setDependsOn(List.of("dep-1"));
            setupTaskSaveWithReload(task);

            TaskEntity depEntity = new TaskEntity();
            depEntity.setId("dep-1");
            depEntity.setTitle("Completed Dep");
            depEntity.setDescription("Desc");
            depEntity.setStatus(TaskStatus.COMPLETED);
            depEntity.setRepoPath("/repo");
            depEntity.setCreatedAt(Instant.now());
            depEntity.setUpdatedAt(Instant.now());

            when(taskRepository.findById("dep-1")).thenReturn(Optional.of(depEntity));
            setupPlannerMock("{\"complexity\":\"simple\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(plannerAgent).run(any());
        }
    }

    @Nested
    class TestWriterSelfValidationRetry {

        @Test
        void shouldRetryTestWriterOnInvalidClassificationAndEventuallyReachNeedsArbitration() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twRun = createAgentRun(ndjsonWithText("INVALID tests"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(result.getTestRetryCount()).isEqualTo(orchestratorConfig.getMaxTestRetries() + 1);

            verify(testWriterAgent, times(orchestratorConfig.getMaxTestRetries() + 1)).run(any());
        }

        @Test
        void shouldRetryTestWriterOnMissingCommitHash() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twNoCommitRun = createAgentRun(ndjsonWithText("No commit"), 0);
            ExtractionResult<TestWriterResult> twNoCommitExtract = ExtractionResult.success(
                    new TestWriterResult("No commit", "mvn test", "EXPECTED_RED", null, List.of()), "");
            AgentRun twValidRun = createAgentRun(ndjsonWithText("Valid"), 0);
            ExtractionResult<TestWriterResult> twValidExtract = ExtractionResult.success(
                    new TestWriterResult("Valid", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twNoCommitRun, twNoCommitExtract))
                    .thenReturn(new AgentResult<>(twValidRun, twValidExtract));

            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
        }

        @Test
        void shouldRetryTestWriterOnInvalidThenSucceedOnNextAttempt() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twInvalidRun = createAgentRun(ndjsonWithText("INVALID"), 0);
            ExtractionResult<TestWriterResult> twInvalidExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid", "mvn test", "INVALID", null, List.of()), "");
            AgentRun twValidRun = createAgentRun(ndjsonWithText("EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twValidExtract = ExtractionResult.success(
                    new TestWriterResult("Valid", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twInvalidRun, twInvalidExtract))
                    .thenReturn(new AgentResult<>(twValidRun, twValidExtract));

            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
            verify(testReviewerAgent).run(any());
        }
    }

    @Nested
    class TestReviewerRejectionRetry {

        @Test
        void shouldRetryTestWriterWhenTestReviewerRejects() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.REQUEST_CHANGES, "Tests are weak.");

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            verify(testReviewerAgent, atLeast(1)).run(any());
            verify(testWriterAgent, atLeast(2)).run(any());
        }

        @Test
        void shouldPassTestPhaseAfterTestReviewerRejectionThenRetrySuccess() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twRun1 = createAgentRun(ndjsonWithText("EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract1 = ExtractionResult.success(
                    new TestWriterResult("Tests", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            AgentRun twRun2 = createAgentRun(ndjsonWithText("EXPECTED_RED retry"), 0);
            ExtractionResult<TestWriterResult> twExtract2 = ExtractionResult.success(
                    new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "def456", List.of()), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twRun1, twExtract1))
                    .thenReturn(new AgentResult<>(twRun2, twExtract2));

            AgentRun trRejectRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nWeak."), 0);
            ExtractionResult<TestReviewerResult> trRejectExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.REQUEST_CHANGES, "Weak."), "");
            AgentRun trApproveRun = createAgentRun(ndjsonWithText("APPROVE\nGood."), 0);
            ExtractionResult<TestReviewerResult> trApproveExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good."), "");

            when(testReviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(trRejectRun, trRejectExtract))
                    .thenReturn(new AgentResult<>(trApproveRun, trApproveExtract));

            setupCoderMock();
            setupReviewerMock(ReviewVerdict.APPROVE, "OK.", null);

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
            verify(testReviewerAgent, times(2)).run(any());
        }
    }

    @Nested
    class ReviewerImplementationIssueRouting {

        @Test
        void shouldRouteImplementationIssueBackToCoder() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");

            AgentRun coderRun1 = createAgentRun(ndjsonWithText("First attempt"), 0);
            ExtractionResult<CoderResult> coderExtract1 = ExtractionResult.success(
                    new CoderResult("First attempt", "mvn test", "pass", "def456", List.of()), "");
            AgentRun coderRun2 = createAgentRun(ndjsonWithText("Second attempt"), 0);
            ExtractionResult<CoderResult> coderExtract2 = ExtractionResult.success(
                    new CoderResult("Second attempt", "mvn test", "pass", "ghi789", List.of()), "");

            when(coderAgent.run(any()))
                    .thenReturn(new AgentResult<>(coderRun1, coderExtract1))
                    .thenReturn(new AgentResult<>(coderRun2, coderExtract2));

            AgentRun reviewerRejectRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nBugs."), 0);
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs.", "implementation_issue"), "");
            AgentRun reviewerApproveRun = createAgentRun(ndjsonWithText("APPROVE\nFixed."), 0);
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Fixed.", null), "");

            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            AgentRun coderSessionRun = new AgentRun("run-c1", "task-1", "coder", "test-model",
                    null, null, "prompt", "First attempt", 0, 100L, "coder-session-abc", 0, Instant.now());
            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderSessionRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.getCodeRetryCount()).isEqualTo(1);

            verify(coderAgent, times(2)).run(any());
            verify(reviewerAgent, times(2)).run(any());
        }
    }

    @Nested
    class ReviewerTestIssueRouting {

        @Test
        void shouldRouteTestIssueBackToTestWriter() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twRun1 = createAgentRun(ndjsonWithText("EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract1 = ExtractionResult.success(
                    new TestWriterResult("Tests", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            AgentRun twRun2 = createAgentRun(ndjsonWithText("EXPECTED_RED retry"), 0);
            ExtractionResult<TestWriterResult> twExtract2 = ExtractionResult.success(
                    new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "def456", List.of()), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twRun1, twExtract1))
                    .thenReturn(new AgentResult<>(twRun2, twExtract2));

            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun reviewerRejectRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nTest coverage weak."), 0);
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Test coverage weak.", "test_issue"), "");
            AgentRun reviewerApproveRun = createAgentRun(ndjsonWithText("APPROVE\nAll good."), 0);
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "All good.", null), "");

            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(result.getTestRetryCount()).isGreaterThan(0);
            assertThat(result.getCodeRetryCount()).isEqualTo(0);

            verify(testWriterAgent, times(2)).run(any());
            verify(coderAgent, times(2)).run(any());
            verify(reviewerAgent, times(2)).run(any());
        }

        @Test
        void shouldIncrementTestRetryNotCodeRetryForTestIssue() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nAssertion wrong."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Assertion wrong.", "test_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getTestRetryCount()).isGreaterThan(0);
            assertThat(result.getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class ReviewerUnclearRouting {

        @Test
        void shouldRouteUnclearFeedbackToNeedsArbitration() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES"), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.successWithWarnings(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "(no feedback provided)", "unclear"),
                    List.of("Reviewer feedback body is empty"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getTestRetryCount()).isEqualTo(0);
            assertThat(result.getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class RetryLimitTransitions {

        @Test
        void shouldEnterNeedsArbitrationWhenMaxCodeRetriesExceededByReviewerRejections() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nBugs."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs.", "implementation_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            AgentRun coderSessionRun = createAgentRun("coder output", 0);
            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderSessionRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(result.getCodeRetryCount()).isGreaterThan(orchestratorConfig.getMaxCodeRetries());
        }

        @Test
        void shouldEnterNeedsArbitrationWhenCoderExceptionExceedsRetries() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");

            when(coderAgent.run(any())).thenThrow(new RuntimeException("opencode crashed"));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(result.getCodeRetryCount()).isEqualTo(orchestratorConfig.getMaxCodeRetries() + 1);
        }

        @Test
        void shouldNotAffectCodeRetryCountWhenTestRetriesFail() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();

            AgentRun twRun = createAgentRun(ndjsonWithText("INVALID"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
            assertThat(result.getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class CancellationTerminalBehavior {

        @Test
        void shouldReturnCancelledWhenTaskCancelledBeforeExecution() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.CANCELLED);
            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent, never()).run(any());
        }

        @Test
        void shouldPreserveWorktreeWhenCancelled() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.CANCELLED);
            task.setWorktreePath("/worktrees/task-1");
            task.setBranchName("task-1/test-task");
            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            Task result = ((TaskExecutionService.ExecutionOutcome.Cancelled) outcome).task();
            assertThat(result.getWorktreePath()).isEqualTo("/worktrees/task-1");
            assertThat(result.getBranchName()).isEqualTo("task-1/test-task");
            verify(worktreeManager, never()).removeWorktree(any());
        }

        @Test
        void shouldStopAfterPlanningWhenCancelledDuringPlanning() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                latestEntity.set(invocation.getArgument(0));
                return invocation.getArgument(0);
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");

            java.util.concurrent.atomic.AtomicInteger findByIdCalls = new java.util.concurrent.atomic.AtomicInteger(0);
            when(taskRepository.findById("task-1")).thenAnswer(invocation -> {
                TaskEntity entity = latestEntity.get();
                int call = findByIdCalls.incrementAndGet();
                if (call >= 2 && entity.getStatus() != TaskStatus.CANCELLED) {
                    entity.setStatus(TaskStatus.CANCELLED);
                }
                return Optional.of(entity);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent).run(any());
            verify(testWriterAgent, never()).run(any());
        }
    }

    @Nested
    class PlannerSplitBehavior {

        @Test
        void shouldReturnSplitParentWaitingWhenPlannerSplits() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\"," +
                    "\"sub_tasks\":[{\"title\":\"Task A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]}," +
                    "{\"title\":\"Task B\",\"description\":\"Do B\",\"priority\":\"medium\",\"depends_on\":[0]}]}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.SplitParentWaiting.class);
            TaskExecutionService.ExecutionOutcome.SplitParentWaiting split =
                    (TaskExecutionService.ExecutionOutcome.SplitParentWaiting) outcome;
            assertThat(split.childTasks()).hasSize(2);
            assertThat(split.childTasks().get(0).getParentId()).isEqualTo("task-1");
            assertThat(split.childTasks().get(1).getParentId()).isEqualTo("task-1");
            assertThat(split.childTasks().get(1).getDependsOn()).contains(split.childTasks().get(0).getId());

            verify(worktreeManager, never()).createWorktree(any(), any());
        }
    }

    @Nested
    class PlannerFailure {

        @Test
        void shouldFailWhenPlannerExtractionFails() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            AgentRun plannerRun = createAgentRun("garbage output", 0);
            ExtractionResult<PlannerResult> plannerExtract = ExtractionResult.criticalError("No JSON found", "");
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);
            verify(testWriterAgent, never()).run(any());
        }

        @Test
        void shouldFailWhenPlannerThrowsException() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            when(plannerAgent.run(any())).thenThrow(new RuntimeException("opencode crashed"));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);
            verify(testWriterAgent, never()).run(any());
        }
    }

    @Nested
    class WorktreeCreationFailure {

        @Test
        void shouldFailWhenWorktreeCreationFails() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);
            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");

            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenThrow(new RuntimeException("disk full"));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Failed.class);
            verify(testWriterAgent, never()).run(any());
        }
    }

    @Nested
    class MultiReviewerBehavior {

        @Test
        void shouldCompleteWhenAllReviewersApprove() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            com.tddforge.config.ModelSpec r1 = new com.tddforge.config.ModelSpec();
            r1.setModel("reviewer-1");
            com.tddforge.config.ModelSpec r2 = new com.tddforge.config.ModelSpec();
            r2.setModel("reviewer-2");
            opencodeConfig.setReviewers(List.of(r1, r2));
            runtimeModelConfig.setReviewers(List.of(r1, r2));

            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun r1Run = createAgentRun(ndjsonWithText("APPROVE\nGood."), 0);
            ExtractionResult<ReviewerResult> r1Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Good.", null), "");
            AgentRun r2Run = createAgentRun(ndjsonWithText("APPROVE\nAlso good."), 0);
            ExtractionResult<ReviewerResult> r2Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-2", ReviewVerdict.APPROVE, "Also good.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(r1Run, r1Extract))
                    .thenReturn(new AgentResult<>(r2Run, r2Extract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(reviewerAgent, times(2)).run(any());
        }

        @Test
        void shouldShortCircuitWhenFirstReviewerRejects() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            com.tddforge.config.ModelSpec r1 = new com.tddforge.config.ModelSpec();
            r1.setModel("reviewer-1");
            com.tddforge.config.ModelSpec r2 = new com.tddforge.config.ModelSpec();
            r2.setModel("reviewer-2");
            opencodeConfig.setReviewers(List.of(r1, r2));
            runtimeModelConfig.setReviewers(List.of(r1, r2));

            setupPlannerMock("{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do it\"}");
            setupWorktreeMock();
            setupTestWriterMock("EXPECTED_RED", "abc123");
            setupTestReviewerMock(ReviewVerdict.APPROVE, "Good.");
            setupCoderMock();

            AgentRun r1Run = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nBugs."), 0);
            ExtractionResult<ReviewerResult> r1Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs.", "implementation_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(r1Run, r1Extract));

            AgentRun coderSessionRun = createAgentRun("coder output", 0);
            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderSessionRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            verify(reviewerAgent, atLeast(1)).run(any());
        }
    }
}
