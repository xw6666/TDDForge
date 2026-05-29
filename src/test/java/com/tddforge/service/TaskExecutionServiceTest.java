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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private AgentOutputExtractor outputExtractor;

    private AgentOutputExtractor realExtractor;
    private PlannerService plannerService;
    private OpencodeConfig opencodeConfig;
    private OrchestratorConfig orchestratorConfig;
    private RepoConfig repoConfig;
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

        service = new TaskExecutionService(
                taskRepository, agentRunRepository, taskEventRepository,
                plannerAgent, testWriterAgent, testReviewerAgent, coderAgent, reviewerAgent,
                outputExtractor, plannerService, worktreeManager,
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
        void shouldEnterTestWriteFailedWhenTestWriterReturnsInvalid() {
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
    }

    @Nested
    class TestReviewerRejection {

        @Test
        void shouldEnterTestReviewFailedWhenTestReviewerRejects() {
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
        }
    }

    @Nested
    class ReviewerRejection {

        @Test
        void shouldEnterReviewFailedWhenReviewerRejects() {
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

            TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-1");

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
            verify(reviewerAgent, atLeast(1)).run(any());
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
    }
}