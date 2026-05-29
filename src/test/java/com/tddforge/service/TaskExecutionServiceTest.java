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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskExecutionServiceTest {

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

        service = new TaskExecutionService(
                taskRepository, agentRunRepository, taskEventRepository,
                plannerAgent, testWriterAgent, testReviewerAgent, coderAgent, reviewerAgent,
                plannerService, dependencyTracker, worktreeManager,
                orchestratorConfig, opencodeConfig, repoConfig
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

    @Nested
    class SuccessfulChain {

        @Test
        void shouldCompleteFullPipelineSuccessfully() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter output EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of("Test.java")), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            String trText = "APPROVE\nTests look good.";
            AgentRun trRun = createAgentRun(ndjsonWithText(trText), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Tests look good."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed the bug", "mvn test", "pass", "def456", List.of("Main.java")), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nEverything looks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Everything looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            TaskExecutionService.ExecutionOutcome.Success success = (TaskExecutionService.ExecutionOutcome.Success) outcome;
            assertThat(success.task().getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(success.task().isReviewPass()).isTrue();

            verify(plannerAgent).run(any());
            verify(testWriterAgent).run(any());
            verify(testReviewerAgent).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent).run(any());
            verify(worktreeManager).createWorktree("task-1", "task-1/test-task");
        }
    }

    @Nested
    class PlannerSplitParentWaits {

        @Test
        void shouldNotCreateWorktreeWhenPlannerSplits() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Task A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]}]}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.SplitParentWaiting.class);
            TaskExecutionService.ExecutionOutcome.SplitParentWaiting split = (TaskExecutionService.ExecutionOutcome.SplitParentWaiting) outcome;
            assertThat(split.parentTask().getPlanOutput()).isNotNull();
            assertThat(split.childTasks()).hasSize(1);
            assertThat(split.childTasks().get(0).getParentId()).isEqualTo("task-1");

            verify(worktreeManager, never()).createWorktree(any(), any());
        }
    }

    @Nested
    class TestWriterInvalidResult {

        @Test
        void shouldRetryOnInvalidAndEventuallyReachNeedsArbitration() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("Test classification: INVALID"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid tests", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getTestRetryCount()).isGreaterThan(0);
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);

            verify(testWriterAgent, atLeast(1)).run(any());
        }

        @Test
        void shouldRetryTestWriterOnInvalidThenSucceedOnSecondAttempt() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twInvalidRun = createAgentRun(ndjsonWithText("Test classification: INVALID"), 0);
            ExtractionResult<TestWriterResult> twInvalidExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid tests", "mvn test", "INVALID", null, List.of()), "");
            AgentRun twValidRun = createAgentRun(ndjsonWithText("Test classification: EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twValidExtract = ExtractionResult.success(
                    new TestWriterResult("Valid tests", "mvn test", "EXPECTED_RED", "abc123", List.of("Test.java")), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twInvalidRun, twInvalidExtract))
                    .thenReturn(new AgentResult<>(twValidRun, twValidExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nEverything looks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Everything looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
            verify(testReviewerAgent).run(any());
        }
    }

    @Nested
    class TestWriterMissingCommit {

        @Test
        void shouldRetryWhenTestWriterReturnsNoCommitHash() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twNoCommitRun = createAgentRun(ndjsonWithText("Test classification: EXPECTED_RED no commit"), 0);
            ExtractionResult<TestWriterResult> twNoCommitExtract = ExtractionResult.success(
                    new TestWriterResult("No commit", "mvn test", "EXPECTED_RED", null, List.of()), "");

            AgentRun twValidRun = createAgentRun(ndjsonWithText("Test classification: EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twValidExtract = ExtractionResult.success(
                    new TestWriterResult("Valid tests", "mvn test", "EXPECTED_RED", "abc123", List.of("Test.java")), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twNoCommitRun, twNoCommitExtract))
                    .thenReturn(new AgentResult<>(twValidRun, twValidExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nEverything looks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Everything looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
        }
    }

    @Nested
    class ExpectedRedReachesReviewer {

        @Test
        void shouldReachTestReviewerWhenResultIsExpectedRed() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nEverything looks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Everything looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            TaskExecutionService.ExecutionOutcome.Success success = (TaskExecutionService.ExecutionOutcome.Success) outcome;
            assertThat(success.task().getStatus()).isEqualTo(TaskStatus.COMPLETED);

            verify(testWriterAgent).run(any());
            verify(testReviewerAgent).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent).run(any());
        }
    }

    @Nested
    class TestReviewerRejection {

        @Test
        void shouldReRouteToTestWriterWhenTestReviewerRejects() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            String trText = "REQUEST_CHANGES\nTests are weak.";
            AgentRun trRun = createAgentRun(ndjsonWithText(trText), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.REQUEST_CHANGES, "Tests are weak."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            verify(testReviewerAgent, atLeast(1)).run(any());
            verify(testWriterAgent, atLeast(2)).run(any());
        }

        @Test
        void shouldPassTestPhaseAfterReviewerRejectionAndTestWriterRetrySuccess() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun1 = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract1 = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");

            AgentRun twRun2 = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED retry"), 0);
            ExtractionResult<TestWriterResult> twExtract2 = ExtractionResult.success(
                    new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "def456", List.of()), "");

            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twRun1, twExtract1))
                    .thenReturn(new AgentResult<>(twRun2, twExtract2));

            String trRejectText = "REQUEST_CHANGES\nTests are weak.";
            AgentRun trRejectRun = createAgentRun(ndjsonWithText(trRejectText), 0);
            ExtractionResult<TestReviewerResult> trRejectExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.REQUEST_CHANGES, "Tests are weak."), "");
            AgentRun trApproveRun = createAgentRun(ndjsonWithText("APPROVE\nTests are good now."), 0);
            ExtractionResult<TestReviewerResult> trApproveExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Tests are good now."), "");

            when(testReviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(trRejectRun, trRejectExtract))
                    .thenReturn(new AgentResult<>(trApproveRun, trApproveExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "ghi789", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nEverything looks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Everything looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            verify(testWriterAgent, times(2)).run(any());
            verify(testReviewerAgent, times(2)).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent).run(any());
        }
    }

    @Nested
    class MaxTestRetriesExceeded {

        @Test
        void shouldEnterNeedsArbitrationWhenMaxTestRetriesExceeded() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("Test classification: INVALID"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid tests", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getTestRetryCount()).isEqualTo(3);

            verify(testWriterAgent, times(3)).run(any());
        }

        @Test
        void shouldNotExceedCodeRetryCountWhenTestRetriesFail() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("Test classification: INVALID"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Invalid tests", "mvn test", "INVALID", null, List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class ReviewerRejection {

        @Test
        void shouldEnterNeedsArbitrationAfterMaxCodeRetriesExceededByReviewerRejections() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRunApprove = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtractApprove = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRunApprove, trExtractApprove));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nImplementation has bugs."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Implementation has bugs.", "implementation_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getCodeRetryCount()).isGreaterThan(orchestratorConfig.getMaxCodeRetries());
            verify(reviewerAgent, atLeast(2)).run(any());
            verify(coderAgent, atLeast(2)).run(any());
        }
    }

    @Nested
    class MultiReviewerAllApprove {

        @Test
        void shouldCompleteWhenAllReviewersApprove() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            com.tddforge.config.ModelSpec reviewer1Spec = new com.tddforge.config.ModelSpec();
            reviewer1Spec.setModel("reviewer-model-1");
            com.tddforge.config.ModelSpec reviewer2Spec = new com.tddforge.config.ModelSpec();
            reviewer2Spec.setModel("reviewer-model-2");
            opencodeConfig.setReviewers(List.of(reviewer1Spec, reviewer2Spec));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewer1Run = createAgentRun(ndjsonWithText("APPROVE\nLooks good."), 0);
            ExtractionResult<ReviewerResult> reviewer1Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Looks good.", null), "");
            AgentRun reviewer2Run = createAgentRun(ndjsonWithText("APPROVE\nAlso good."), 0);
            ExtractionResult<ReviewerResult> reviewer2Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-2", ReviewVerdict.APPROVE, "Also good.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewer1Run, reviewer1Extract))
                    .thenReturn(new AgentResult<>(reviewer2Run, reviewer2Extract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            TaskExecutionService.ExecutionOutcome.Success success = (TaskExecutionService.ExecutionOutcome.Success) outcome;
            assertThat(success.task().getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(success.task().isReviewPass()).isTrue();
            verify(reviewerAgent, times(2)).run(any());
        }
    }

    @Nested
    class MultiReviewerFirstRejectShortCircuit {

        @Test
        void shouldNotRunSecondReviewerWhenFirstRejects() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            com.tddforge.config.ModelSpec reviewer1Spec = new com.tddforge.config.ModelSpec();
            reviewer1Spec.setModel("reviewer-model-1");
            com.tddforge.config.ModelSpec reviewer2Spec = new com.tddforge.config.ModelSpec();
            reviewer2Spec.setModel("reviewer-model-2");
            opencodeConfig.setReviewers(List.of(reviewer1Spec, reviewer2Spec));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewer1Run = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nHas bugs."), 0);
            ExtractionResult<ReviewerResult> reviewer1Extract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Has bugs.", "implementation_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewer1Run, reviewer1Extract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            verify(reviewerAgent, atLeast(1)).run(any());
            verify(reviewerAgent, never()).run(argThat(ctx ->
                    ctx instanceof com.tddforge.agent.AgentContext ac && "reviewer-2".equals(ac.reviewerId())));
        }
    }

    @Nested
    class ReviewerRejectThenCoderRetryWithSessionReuse {

        @Test
        void shouldRouteBackToCoderAndReuseSessionId() {
            Task task = createDefaultTask();
            task.setSessionIds(new ArrayList<>(List.of("coder-session-abc")));
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun1 = createAgentRun(ndjsonWithText("First attempt"), 0);
            coderRun1 = new AgentRun("run-coder-1", "task-1", "coder", "test-model",
                    null, null, "prompt", "First attempt", 0, 100L, "coder-session-abc", 0, Instant.now());
            ExtractionResult<CoderResult> coderExtract1 = ExtractionResult.success(
                    new CoderResult("First attempt", "mvn test", "pass", "def456", List.of()), "");

            AgentRun coderRun2 = createAgentRun(ndjsonWithText("Second attempt"), 0);
            coderRun2 = new AgentRun("run-coder-2", "task-1", "coder", "test-model",
                    null, null, "prompt", "Second attempt", 0, 100L, "coder-session-abc", 0, Instant.now());
            ExtractionResult<CoderResult> coderExtract2 = ExtractionResult.success(
                    new CoderResult("Second attempt", "mvn test", "pass", "ghi789", List.of()), "");

            when(coderAgent.run(any()))
                    .thenReturn(new AgentResult<>(coderRun1, coderExtract1))
                    .thenReturn(new AgentResult<>(coderRun2, coderExtract2));

            AgentRun reviewerRejectRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nBugs found."), 0);
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs found.", "implementation_issue"), "");
            AgentRun reviewerApproveRun = createAgentRun(ndjsonWithText("APPROVE\nFixed."), 0);
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Fixed.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderRun1)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            TaskExecutionService.ExecutionOutcome.Success success = (TaskExecutionService.ExecutionOutcome.Success) outcome;
            assertThat(success.task().getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(success.task().getCodeRetryCount()).isEqualTo(1);
            verify(reviewerAgent, times(2)).run(any());
            verify(coderAgent, times(2)).run(any());
        }

        @Test
        void shouldUseCoderSessionEvenWhenReviewerSessionAppendedAfter() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            Instant coderTime = Instant.now().minusSeconds(60);
            AgentRun coderRun1 = new AgentRun("run-coder-1", "task-1", "coder", "test-model",
                    null, null, "prompt", "First attempt", 0, 100L, "coder-session-xyz", 0, coderTime);
            ExtractionResult<CoderResult> coderExtract1 = ExtractionResult.success(
                    new CoderResult("First attempt", "mvn test", "pass", "def456", List.of()), "");

            AgentRun coderRun2 = new AgentRun("run-coder-2", "task-1", "coder", "test-model",
                    null, null, "prompt", "Second attempt", 0, 100L, "coder-session-xyz", 0, Instant.now());
            ExtractionResult<CoderResult> coderExtract2 = ExtractionResult.success(
                    new CoderResult("Second attempt", "mvn test", "pass", "ghi789", List.of()), "");

            when(coderAgent.run(any()))
                    .thenReturn(new AgentResult<>(coderRun1, coderExtract1))
                    .thenReturn(new AgentResult<>(coderRun2, coderExtract2));

            Instant reviewerTime = Instant.now().minusSeconds(30);
            AgentRun reviewerRejectRun = new AgentRun("run-rev-1", "task-1", "reviewer", "test-model",
                    null, null, "prompt", ndjsonWithText("REQUEST_CHANGES\nBugs found."), 0, 100L, "reviewer-session-999", 0, reviewerTime);
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs found.", "implementation_issue"), "");
            AgentRun reviewerApproveRun = new AgentRun("run-rev-2", "task-1", "reviewer", "test-model",
                    null, null, "prompt", ndjsonWithText("APPROVE\nFixed."), 0, 100L, "reviewer-session-999", 0, Instant.now());
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Fixed.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(java.util.Optional.of(AgentRunEntity.fromDomain(coderRun1)));

            AtomicReference<AgentContext> capturedCoderRetryContext = new AtomicReference<>();
            when(coderAgent.run(any())).thenAnswer(invocation -> {
                AgentContext ctx = invocation.getArgument(0);
                if (ctx.sessionId() != null) {
                    capturedCoderRetryContext.set(ctx);
                }
                if (capturedCoderRetryContext.get() == null) {
                    return new AgentResult<>(coderRun1, coderExtract1);
                }
                return new AgentResult<>(coderRun2, coderExtract2);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            assertThat(capturedCoderRetryContext.get()).isNotNull();
            assertThat(capturedCoderRetryContext.get().sessionId()).isEqualTo("coder-session-xyz");
        }

        @Test
        void shouldRecordEventAndCreateNewSessionWhenCoderSessionMissing() {
            Task task = createDefaultTask();
            task.setSessionIds(new ArrayList<>());
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = new AgentRun("run-p", "task-1", "planner", "test-model",
                    null, null, "prompt", ndjsonWithText(plannerJson), 0, 100L, null, 0, Instant.now());
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = new AgentRun("run-tw", "task-1", "test-writer", "test-model",
                    null, null, "prompt", ndjsonWithText("TestWriter EXPECTED_RED"), 0, 100L, null, 0, Instant.now());
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = new AgentRun("run-tr", "task-1", "test-reviewer", "test-model",
                    null, null, "prompt", ndjsonWithText("APPROVE\nGood tests."), 0, 100L, null, 0, Instant.now());
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = new AgentRun("run-c1", "task-1", "coder", "test-model",
                    null, null, "prompt", ndjsonWithText("Implementation done"), 0, 100L, null, 0, Instant.now());
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRejectRun = new AgentRun("run-r1", "task-1", "reviewer", "test-model",
                    null, null, "prompt", ndjsonWithText("REQUEST_CHANGES\nBugs."), 0, 100L, null, 0, Instant.now());
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Bugs.", "implementation_issue"), "");
            AgentRun reviewerApproveRun = new AgentRun("run-r2", "task-1", "reviewer", "test-model",
                    null, null, "prompt", ndjsonWithText("APPROVE\nGood now."), 0, 100L, null, 0, Instant.now());
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Good now.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            AtomicReference<TaskEventEntity> capturedEvent = new AtomicReference<>();
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> {
                TaskEventEntity event = invocation.getArgument(0);
                if ("CODER_SESSION_MISSING".equals(event.getEventType())) {
                    capturedEvent.set(event);
                }
                return event;
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            assertThat(capturedEvent.get()).isNotNull();
            assertThat(capturedEvent.get().getMessage()).contains("No coder session found for retry");
        }
    }

    @Nested
    class MaxCodeRetriesExceededByReviewer {

        @Test
        void shouldEnterNeedsArbitrationWhenCodeRetriesExceeded() {
            orchestratorConfig.setMaxCodeRetries(0);
            Task task = createDefaultTask();
            task.setMaxCodeRetries(0);
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nHas bugs."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Has bugs.", "implementation_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(1);
        }
    }

    @Nested
    class ReviewerTestIssueRouting {

        @Test
        void shouldRouteTestIssueBackToTestWriter() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun1 = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract1 = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            AgentRun twRun2 = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED retry"), 0);
            ExtractionResult<TestWriterResult> twExtract2 = ExtractionResult.success(
                    new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "def456", List.of()), "");
            when(testWriterAgent.run(any()))
                    .thenReturn(new AgentResult<>(twRun1, twExtract1))
                    .thenReturn(new AgentResult<>(twRun2, twExtract2));

            AgentRun trApproveRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trApproveExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trApproveRun, trApproveExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "ghi789", List.of()), "");
            AgentRun coderRun2 = createAgentRun(ndjsonWithText("Second implementation"), 0);
            ExtractionResult<CoderResult> coderExtract2 = ExtractionResult.success(
                    new CoderResult("Second attempt", "mvn test", "pass", "jkl012", List.of()), "");
            when(coderAgent.run(any()))
                    .thenReturn(new AgentResult<>(coderRun, coderExtract))
                    .thenReturn(new AgentResult<>(coderRun2, coderExtract2));

            AgentRun reviewerRejectRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nTest is invalid and coverage is weak."), 0);
            ExtractionResult<ReviewerResult> reviewerRejectExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Test is invalid and coverage is weak.", "test_issue"), "");
            AgentRun reviewerApproveRun = createAgentRun(ndjsonWithText("APPROVE\nAll good now."), 0);
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "All good now.", null), "");
            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRejectRun, reviewerRejectExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            TaskExecutionService.ExecutionOutcome.Success success = (TaskExecutionService.ExecutionOutcome.Success) outcome;
            assertThat(success.task().getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(success.task().getTestRetryCount()).isGreaterThan(0);
            assertThat(success.task().getCodeRetryCount()).isEqualTo(0);
            verify(testWriterAgent, times(2)).run(any());
            verify(coderAgent, times(2)).run(any());
            verify(reviewerAgent, times(2)).run(any());
        }

        @Test
        void shouldIncrementTestRetryCountNotCodeRetryCountForTestIssue() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nThe assertion is wrong."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "The assertion is wrong.", "test_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getTestRetryCount()).isGreaterThan(0);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class ReviewerUnclearRouting {

        @Test
        void shouldRouteUnclearFeedbackToNeedsArbitration() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES"), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.successWithWarnings(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "(no feedback provided)", "unclear"),
                    List.of("Reviewer feedback body is empty"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getTestRetryCount()).isEqualTo(0);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(0);
        }

        @Test
        void shouldNotIncrementAnyRetryCountForUnclearRouting() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES"), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.successWithWarnings(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "(no feedback provided)", "unclear"),
                    List.of("Reviewer feedback body is empty"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getTestRetryCount()).isEqualTo(0);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(0);
        }
    }

    @Nested
    class ReviewerEventDataPersistence {

        @Test
        void shouldRecordReviewClassifiedEventWithCategoryAndRouteDataForTestIssue() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nThe test is invalid."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "The test is invalid.", "test_issue"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            java.util.List<TaskEventEntity> capturedEvents = new java.util.ArrayList<>();
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> {
                TaskEventEntity event = invocation.getArgument(0);
                capturedEvents.add(event);
                return event;
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);

            java.util.Optional<TaskEventEntity> classifiedEvent = capturedEvents.stream()
                    .filter(e -> "REVIEW_CLASSIFIED".equals(e.getEventType()))
                    .findFirst();
            assertThat(classifiedEvent).isPresent();
            assertThat(classifiedEvent.get().getMessage()).contains("test_issue");
            assertThat(classifiedEvent.get().getDataJson()).isNotNull();
            assertThat(classifiedEvent.get().getDataJson()).contains("\"category\":\"test_issue\"");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"route\":\"TEST_WRITER\"");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"reviewerId\":\"reviewer-1\"");
        }

        @Test
        void shouldRecordReviewClassifiedEventWithCategoryAndRouteDataForImplementationIssue() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES\nImplementation has bugs."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Implementation has bugs.", "implementation_issue"), "");

            AgentRun coderRetryRun = createAgentRun(ndjsonWithText("Second attempt"), 0);
            coderRetryRun = new AgentRun("run-c2", "task-1", "coder", "test-model",
                    null, null, "prompt", "Second attempt", 0, 100L, "coder-session-1", 0, Instant.now());
            ExtractionResult<CoderResult> coderRetryExtract = ExtractionResult.success(
                    new CoderResult("Second attempt", "mvn test", "pass", "ghi789", List.of()), "");

            AgentRun reviewerApproveRun = createAgentRun(ndjsonWithText("APPROVE\nAll good."), 0);
            ExtractionResult<ReviewerResult> reviewerApproveExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "All good.", null), "");

            when(reviewerAgent.run(any()))
                    .thenReturn(new AgentResult<>(reviewerRun, reviewerExtract))
                    .thenReturn(new AgentResult<>(reviewerApproveRun, reviewerApproveExtract));
            when(coderAgent.run(any()))
                    .thenReturn(new AgentResult<>(coderRun, coderExtract))
                    .thenReturn(new AgentResult<>(coderRetryRun, coderRetryExtract));

            when(agentRunRepository.findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc("task-1", "coder"))
                    .thenReturn(Optional.of(AgentRunEntity.fromDomain(coderRun)));

            java.util.List<TaskEventEntity> capturedEvents = new java.util.ArrayList<>();
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> {
                TaskEventEntity event = invocation.getArgument(0);
                capturedEvents.add(event);
                return event;
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);

            java.util.Optional<TaskEventEntity> classifiedEvent = capturedEvents.stream()
                    .filter(e -> "REVIEW_CLASSIFIED".equals(e.getEventType()))
                    .findFirst();
            assertThat(classifiedEvent).isPresent();
            assertThat(classifiedEvent.get().getMessage()).contains("implementation_issue");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"category\":\"implementation_issue\"");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"route\":\"CODER\"");
        }

        @Test
        void shouldRecordReviewClassifiedEventWithCategoryAndRouteDataForUnclear() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("REQUEST_CHANGES"), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.successWithWarnings(
                    new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "(no feedback provided)", "unclear"),
                    List.of("Reviewer feedback body is empty"), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            java.util.List<TaskEventEntity> capturedEvents = new java.util.ArrayList<>();
            when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> {
                TaskEventEntity event = invocation.getArgument(0);
                capturedEvents.add(event);
                return event;
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);

            java.util.Optional<TaskEventEntity> classifiedEvent = capturedEvents.stream()
                    .filter(e -> "REVIEW_CLASSIFIED".equals(e.getEventType()))
                    .findFirst();
            assertThat(classifiedEvent).isPresent();
            assertThat(classifiedEvent.get().getMessage()).contains("unclear");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"category\":\"unclear\"");
            assertThat(classifiedEvent.get().getDataJson()).contains("\"route\":\"NEEDS_ARBITRATION\"");
        }
    }

    @Nested
    class CoderFailureRetryLimits {

        @Test
        void shouldEnterNeedsArbitrationWhenCoderExceptionExceedsRetries() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            when(coderAgent.run(any())).thenThrow(new RuntimeException("opencode crashed"));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(orchestratorConfig.getMaxCodeRetries() + 1);
        }

        @Test
        void shouldEnterNeedsArbitrationWhenCoderNonZeroExitExceedsRetries() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderFailRun = createAgentRun(ndjsonWithText("Build failed"), 1);
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderFailRun, ExtractionResult.criticalError("exit code 1", "")));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(orchestratorConfig.getMaxCodeRetries() + 1);
        }

        @Test
        void shouldEnterNeedsArbitrationWhenCoderExtractionFailureExceedsRetries() {
            Task task = createDefaultTask();
            setupTaskSaveWithReload(task);

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderBadRun = createAgentRun(ndjsonWithText("No structured output"), 0);
            ExtractionResult<CoderResult> coderBadExtract = ExtractionResult.criticalError("Could not extract CoderResult", "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderBadRun, coderBadExtract));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            TaskExecutionService.ExecutionOutcome.NeedsArbitration needsArb = (TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome;
            assertThat(needsArb.task().getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
            assertThat(needsArb.task().getCodeRetryCount()).isEqualTo(orchestratorConfig.getMaxCodeRetries() + 1);
        }
    }

    @Nested
    class CancelledTaskStops {

        @Test
        void shouldStopWhenTaskIsCancelledBeforePlanning() {
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
        void shouldStopAfterPlanningWhenTaskCancelledDuringPlanning() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));

            AtomicInteger findByIdCalls = new AtomicInteger(0);
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
            verify(coderAgent, never()).run(any());
            verify(reviewerAgent, never()).run(any());
        }

        @Test
        void shouldNotOverwriteCancelledWithSessionIdSaveInPlanning() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRunWithSession = new AgentRun("run-p", "task-1", "planner", "test-model",
                    null, null, "prompt", ndjsonWithText(plannerJson), 0, 100L, "planner-sess-123", 0, Instant.now());
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRunWithSession);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRunWithSession, plannerExtract));

            AtomicInteger findByIdCalls = new AtomicInteger(0);
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
            TaskExecutionService.ExecutionOutcome.Cancelled cancelled = (TaskExecutionService.ExecutionOutcome.Cancelled) outcome;
            assertThat(cancelled.task().getStatus()).isEqualTo(TaskStatus.CANCELLED);
            verify(plannerAgent).run(any());
            verify(testWriterAgent, never()).run(any());
            verify(coderAgent, never()).run(any());
            verify(reviewerAgent, never()).run(any());
        }

        @Test
        void shouldPreserveWorktreeAndAgentRunsWhenCancelled() {
            Task task = createDefaultTask();
            task.setStatus(TaskStatus.CANCELLED);
            task.setWorktreePath("/worktrees/task-1");
            task.setBranchName("task-1/test-task");
            AgentRun existingRun = createAgentRun("some output", 0);
            when(taskRepository.findById("task-1")).thenReturn(Optional.of(TaskEntity.fromDomain(task)));
            when(agentRunRepository.findByTaskIdOrderByCreatedAtDesc("task-1"))
                    .thenReturn(List.of(AgentRunEntity.fromDomain(existingRun)));

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            TaskExecutionService.ExecutionOutcome.Cancelled cancelled = (TaskExecutionService.ExecutionOutcome.Cancelled) outcome;
            assertThat(cancelled.task().getWorktreePath()).isEqualTo("/worktrees/task-1");
            assertThat(cancelled.task().getBranchName()).isEqualTo("task-1/test-task");

            verify(worktreeManager, never()).removeWorktree(any());
            verify(taskRepository, never()).delete(any());
            verify(agentRunRepository, never()).delete(any());
        }

        @Test
        void shouldStopAfterTestWriterWhenCancelledDuringTestWriting() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of("Test.java")), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AtomicBoolean seenTestWriting = new AtomicBoolean(false);
            when(taskRepository.findById("task-1")).thenAnswer(invocation -> {
                TaskEntity entity = latestEntity.get();
                if (entity.getStatus() == TaskStatus.TEST_WRITING) {
                    if (seenTestWriting.get()) {
                        entity.setStatus(TaskStatus.CANCELLED);
                    } else {
                        seenTestWriting.set(true);
                    }
                }
                return Optional.of(entity);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent).run(any());
            verify(testWriterAgent).run(any());
            verify(testReviewerAgent, never()).run(any());
            verify(coderAgent, never()).run(any());
            verify(reviewerAgent, never()).run(any());
        }

        @Test
        void shouldStopAfterCoderWhenCancelledDuringCoding() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AtomicBoolean seenCoding = new AtomicBoolean(false);
            when(taskRepository.findById("task-1")).thenAnswer(invocation -> {
                TaskEntity entity = latestEntity.get();
                if (entity.getStatus() == TaskStatus.CODING) {
                    if (seenCoding.get()) {
                        entity.setStatus(TaskStatus.CANCELLED);
                    } else {
                        seenCoding.set(true);
                    }
                }
                return Optional.of(entity);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent).run(any());
            verify(testWriterAgent).run(any());
            verify(testReviewerAgent).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent, never()).run(any());
        }

        @Test
        void shouldStopAfterReviewerWhenCancelledDuringReviewing() {
            Task task = createDefaultTask();
            AtomicReference<TaskEntity> latestEntity = new AtomicReference<>(TaskEntity.fromDomain(task));
            lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
                TaskEntity entity = invocation.getArgument(0);
                latestEntity.set(entity);
                return entity;
            });
            lenient().when(agentRunRepository.save(any(AgentRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(taskEventRepository.save(any(TaskEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String plannerJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple\",\"plan\":\"Do the thing\"}";
            AgentRun plannerRun = createAgentRun(ndjsonWithText(plannerJson), 0);
            ExtractionResult<PlannerResult> plannerExtract = realExtractor.extractPlannerResult(plannerRun);
            when(plannerAgent.run(any())).thenReturn(new AgentResult<>(plannerRun, plannerExtract));
            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task-1/test-task");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/worktrees/task-1"));

            AgentRun twRun = createAgentRun(ndjsonWithText("TestWriter EXPECTED_RED"), 0);
            ExtractionResult<TestWriterResult> twExtract = ExtractionResult.success(
                    new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc123", List.of()), "");
            when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(twRun, twExtract));

            AgentRun trRun = createAgentRun(ndjsonWithText("APPROVE\nGood tests."), 0);
            ExtractionResult<TestReviewerResult> trExtract = ExtractionResult.success(
                    new TestReviewerResult(ReviewVerdict.APPROVE, "Good tests."), "");
            when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(trRun, trExtract));

            AgentRun coderRun = createAgentRun(ndjsonWithText("Implementation done"), 0);
            ExtractionResult<CoderResult> coderExtract = ExtractionResult.success(
                    new CoderResult("Fixed bug", "mvn test", "pass", "def456", List.of()), "");
            when(coderAgent.run(any())).thenReturn(new AgentResult<>(coderRun, coderExtract));

            AgentRun reviewerRun = createAgentRun(ndjsonWithText("APPROVE\nLooks good."), 0);
            ExtractionResult<ReviewerResult> reviewerExtract = ExtractionResult.success(
                    new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Looks good.", null), "");
            when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(reviewerRun, reviewerExtract));

            AtomicBoolean seenReviewing = new AtomicBoolean(false);
            when(taskRepository.findById("task-1")).thenAnswer(invocation -> {
                TaskEntity entity = latestEntity.get();
                if (entity.getStatus() == TaskStatus.REVIEWING) {
                    if (seenReviewing.get()) {
                        entity.setStatus(TaskStatus.CANCELLED);
                    } else {
                        seenReviewing.set(true);
                    }
                }
                return Optional.of(entity);
            });

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
            verify(plannerAgent).run(any());
            verify(testWriterAgent).run(any());
            verify(testReviewerAgent).run(any());
            verify(coderAgent).run(any());
            verify(reviewerAgent).run(any());
        }
    }
}