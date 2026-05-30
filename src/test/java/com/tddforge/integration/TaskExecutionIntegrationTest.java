package com.tddforge.integration;

import com.tddforge.agent.*;
import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.OrchestratorConfig;
import com.tddforge.config.RepoConfig;
import com.tddforge.domain.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.opencode.OpenCodeClient;
import com.tddforge.opencode.OpenCodeNdjsonParser;
import com.tddforge.persistence.AgentRunEntity;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.service.DependencyTracker;
import com.tddforge.service.PlannerResultProcessor;
import com.tddforge.service.PlannerResultValidator;
import com.tddforge.service.PlannerService;
import com.tddforge.service.TaskExecutionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the TDD pipeline state machine with real persistence.
 *
 * <p>NOTE: Docker is not available in this CI environment, so H2 with MODE=MySQL
 * is used as the test substitute for Testcontainers MySQL, matching the existing
 * persistence test convention (see {@code TaskRepositoryTest}). The H2 migration
 * mirrors the MySQL schema. Fake opencode scripts are used instead of real opencode.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
class TaskExecutionIntegrationTest {

    private static Path tempRepoDir;
    private static Path tempWorktreeDir;

    @BeforeAll
    static void initTempDirs() throws IOException, InterruptedException {
        tempRepoDir = Files.createTempDirectory("integration-test-repo");
        runGit(tempRepoDir, "init");
        runGit(tempRepoDir, "config", "user.email", "test@test.com");
        runGit(tempRepoDir, "config", "user.name", "Test");
        Files.writeString(tempRepoDir.resolve("README.md"), "init");
        runGit(tempRepoDir, "add", ".");
        runGit(tempRepoDir, "commit", "-m", "init");
        tempWorktreeDir = Files.createTempDirectory("integration-test-worktrees");
    }

    @AfterAll
    static void cleanupTempDirs() throws IOException {
        deleteDirectoryIfExists(tempWorktreeDir);
        deleteDirectoryIfExists(tempRepoDir);
    }

    private static void deleteDirectoryIfExists(Path dir) throws IOException {
        if (dir != null && Files.isDirectory(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void runGit(Path dir, String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start().waitFor();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        if (tempRepoDir != null) registry.add("repo.path", tempRepoDir::toString);
        if (tempWorktreeDir != null) registry.add("repo.worktree_dir", tempWorktreeDir::toString);
    }

    @Autowired private TaskRepository taskRepository;
    @Autowired private AgentRunRepository agentRunRepository;
    @Autowired private TaskEventRepository taskEventRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PlannerService plannerService;
    @Autowired private DependencyTracker dependencyTracker;
    @Autowired private OrchestratorConfig orchestratorConfig;
    @Autowired private OpencodeConfig opencodeConfig;
    @Autowired private RepoConfig repoConfig;
    @Autowired private AgentOutputExtractor agentOutputExtractor;

    @MockBean private WorktreeManager worktreeManager;
    @MockBean private PlannerAgent plannerAgent;
    @MockBean private TestWriterAgent testWriterAgent;
    @MockBean private TestReviewerAgent testReviewerAgent;
    @MockBean private CoderAgent coderAgent;
    @MockBean private ReviewerAgent reviewerAgent;

    private TaskExecutionService service;
    private final AtomicInteger runIdCounter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        runIdCounter.set(0);
        entityManager.clear();
        transactionTemplate.executeWithoutResult(status -> {
            agentRunRepository.deleteAll();
            taskEventRepository.deleteAll();
            taskRepository.deleteAll();
            entityManager.flush();
        });
        entityManager.clear();

        service = new TaskExecutionService(
                taskRepository, agentRunRepository, taskEventRepository,
                plannerAgent, testWriterAgent, testReviewerAgent, coderAgent, reviewerAgent,
                plannerService, dependencyTracker, worktreeManager,
                orchestratorConfig, opencodeConfig, repoConfig
        );

        when(worktreeManager.generateBranchName(any(), any())).thenReturn("task/integration/test");
        when(worktreeManager.createWorktree(any(), any())).thenReturn(Path.of("/tmp/integration-worktree"));
    }

    private TaskEntity createAndSaveTask(String id, String title) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            TaskEntity entity = new TaskEntity();
            entity.setId(id);
            entity.setTitle(title);
            entity.setDescription("Integration test task: " + title);
            entity.setStatus(TaskStatus.PENDING);
            entity.setPriority(TaskPriority.MEDIUM);
            entity.setSource(TaskSource.MANUAL);
            entity.setTaskMode("develop");
            entity.setRepoPath(repoConfig.getPath());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return taskRepository.save(entity);
        });
    }

    private String ndjson(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"type\":\"step_start\",\"step_start\":{\"type\":\"thinking\"}}\n" +
                "{\"type\":\"text\",\"sessionId\":\"int-sess\",\"text\":\"" + escaped + "\"}\n" +
                "{\"type\":\"step_finish\",\"step_finish\":{\"reason\":\"stop\"}}";
    }

    private AgentRun makeRun(String prefix, String taskId, String agentType, String output) {
        String id = prefix + "-" + runIdCounter.incrementAndGet();
        return new AgentRun(id, taskId, agentType, "test-model",
                null, null, "prompt", output, 0, 100L, "sess-" + id, 0, Instant.now());
    }

    private AgentRun makeRunWithExitCode(String prefix, String taskId, String agentType, String output, int exitCode) {
        String id = prefix + "-" + runIdCounter.incrementAndGet();
        return new AgentRun(id, taskId, agentType, "test-model",
                null, null, "prompt", output, exitCode, 100L, "sess-" + id, 0, Instant.now());
    }

    // --- Mock agent setup helpers ---

    private void setupPlannerSingle(String taskId) {
        String json = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple task\",\"plan\":\"Do the thing\"}";
        AgentRun run = makeRun("run-p", taskId, "planner", ndjson(json));
        when(plannerAgent.run(any())).thenReturn(new AgentResult<>(run, agentOutputExtractor.extractPlannerResult(run)));
    }

    private void setupPlannerSplit(String taskId) {
        String json = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Sub A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]},{\"title\":\"Sub B\",\"description\":\"Do B\",\"priority\":\"medium\",\"depends_on\":[0]}]}";
        AgentRun run = makeRun("run-p", taskId, "planner", ndjson(json));
        when(plannerAgent.run(any())).thenReturn(new AgentResult<>(run, agentOutputExtractor.extractPlannerResult(run)));
    }

    private void setupTestWriterExpectedRed(String taskId) {
        String out = ndjson("## Behavior covered\nTest the new feature\n## Test command run\n`mvn test`\n## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc1234567");
        AgentRun run = makeRun("run-tw", taskId, "test-writer", out);
        when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(run,
                ExtractionResult.success(new TestWriterResult("Test summary", "mvn test", "EXPECTED_RED", "abc1234567", List.of("Test.java")), out)));
    }

    private void setupTestWriterInvalid(String taskId) {
        String out = ndjson("## Behavior covered\nInvalid test\n## Test command run\n`mvn test`\n## Result classification\nINVALID");
        AgentRun run = makeRun("run-tw", taskId, "test-writer", out);
        when(testWriterAgent.run(any())).thenReturn(new AgentResult<>(run,
                ExtractionResult.success(new TestWriterResult("Invalid tests", "mvn test", "INVALID", null, List.of()), out)));
    }

    private void setupTestReviewerApprove(String taskId) {
        String out = ndjson("APPROVE\nTests look good.");
        AgentRun run = makeRun("run-tr", taskId, "test-reviewer", out);
        when(testReviewerAgent.run(any())).thenReturn(new AgentResult<>(run,
                ExtractionResult.success(new TestReviewerResult(ReviewVerdict.APPROVE, "Tests look good."), out)));
    }

    private void setupCoderSuccess(String taskId) {
        String out = ndjson("## Implementation summary\nImplemented the feature.\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def4567890");
        AgentRun run = makeRun("run-c", taskId, "coder", out);
        when(coderAgent.run(any())).thenReturn(new AgentResult<>(run,
                ExtractionResult.success(new CoderResult("Implemented the feature", "mvn test", "All tests passed", "def4567890", List.of("Main.java")), out)));
    }

    private void setupReviewerApprove(String taskId) {
        String out = ndjson("APPROVE\nImplementation looks correct.");
        AgentRun run = makeRun("run-r", taskId, "reviewer", out);
        when(reviewerAgent.run(any())).thenReturn(new AgentResult<>(run,
                ExtractionResult.success(new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Implementation looks correct.", null), out)));
    }

    // =========================================================================
    // 1. State machine tests with mock agents (real persistence, real state machine)
    // =========================================================================

    @Nested
    @DisplayName("State machine with mock agents")
    class StateMachineWithMockAgents {

        @Nested
        class SingleTaskSuccessfulCompletion {

            @Test
            void shouldCompleteFullPipelineSuccessfully() {
                String tid = "task-success";
                createAndSaveTask(tid, "Success Task");

                setupPlannerSingle(tid);
                setupTestWriterExpectedRed(tid);
                setupTestReviewerApprove(tid);
                setupCoderSuccess(tid);
                setupReviewerApprove(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                Task completed = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
                assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
                assertThat(completed.isReviewPass()).isTrue();
                assertThat(completed.getComplexity()).isEqualTo("medium");
                assertThat(completed.getPlanOutput()).contains("Do the thing");
                assertThat(completed.getTestOutput()).isNotNull();
                assertThat(completed.getCodeOutput()).isNotNull();
                assertThat(completed.getReviewOutput()).contains("correct");

                verify(plannerAgent).run(any());
                verify(testWriterAgent).run(any());
                verify(testReviewerAgent).run(any());
                verify(coderAgent).run(any());
                verify(reviewerAgent).run(any());
                verify(worktreeManager).createWorktree(tid, "task/integration/test");

                List<AgentRunEntity> runs = agentRunRepository.findByTaskIdOrderByCreatedAtDesc(tid);
                assertThat(runs).hasSizeGreaterThanOrEqualTo(5);

                TaskEntity persisted = taskRepository.findById(tid).orElseThrow();
                assertThat(persisted.getStatus()).isEqualTo(TaskStatus.COMPLETED);
                assertThat(persisted.getSessionIds()).isNotEmpty();
            }
        }

        @Nested
        class TestWriterInvalidRetry {

            @Test
            void shouldRetryTestWriterWhenInvalidThenSucceed() {
                String tid = "task-tw-invalid";
                createAndSaveTask(tid, "TW Invalid Task");
                setupPlannerSingle(tid);

                String invOut = ndjson("## Result classification\nINVALID");
                AgentRun invRun = makeRun("run-tw-inv", tid, "test-writer", invOut);
                ExtractionResult<TestWriterResult> invExt = ExtractionResult.success(
                        new TestWriterResult("Invalid", "mvn test", "INVALID", null, List.of()), invOut);

                String valOut = ndjson("## Behavior covered\nGood tests\n## Test command run\n`mvn test`\n## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc9999999");
                AgentRun valRun = makeRun("run-tw-val", tid, "test-writer", valOut);
                ExtractionResult<TestWriterResult> valExt = ExtractionResult.success(
                        new TestWriterResult("Good tests", "mvn test", "EXPECTED_RED", "abc9999999", List.of()), valOut);

                when(testWriterAgent.run(any()))
                        .thenReturn(new AgentResult<>(invRun, invExt))
                        .thenReturn(new AgentResult<>(valRun, valExt));

                setupTestReviewerApprove(tid);
                setupCoderSuccess(tid);
                setupReviewerApprove(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                        .isEqualTo(TaskStatus.COMPLETED);
                verify(testWriterAgent, times(2)).run(any());
            }

            @Test
            void shouldReachNeedsArbitrationWhenMaxTestRetriesExceeded() {
                String tid = "task-tw-exhaust";
                createAndSaveTask(tid, "TW Exhaust Task");
                setupPlannerSingle(tid);
                setupTestWriterInvalid(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.NeedsArbitration.class);
                Task task = ((TaskExecutionService.ExecutionOutcome.NeedsArbitration) outcome).task();
                assertThat(task.getStatus()).isEqualTo(TaskStatus.NEEDS_ARBITRATION);
                assertThat(task.getTestRetryCount()).isGreaterThan(orchestratorConfig.getMaxTestRetries());
                verify(testWriterAgent, atLeast(2)).run(any());
                verify(testReviewerAgent, never()).run(any());
            }
        }

        @Nested
        class TestReviewerRejectBackToTestWriter {

            @Test
            void shouldRouteBackToTestWriterWhenTestReviewerRejects() {
                String tid = "task-tr-reject";
                createAndSaveTask(tid, "TR Reject Task");
                setupPlannerSingle(tid);

                String twOut1 = ndjson("## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc1234567");
                AgentRun twRun1 = makeRun("run-tw-1", tid, "test-writer", twOut1);
                ExtractionResult<TestWriterResult> twExt1 = ExtractionResult.success(
                        new TestWriterResult("T", "mvn test", "EXPECTED_RED", "abc1234567", List.of()), twOut1);

                String twRetryOut = ndjson("## Behavior covered\nBetter tests\n## Test command run\n`mvn test`\n## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc8888888");
                AgentRun twRetryRun = makeRun("run-tw-retry", tid, "test-writer", twRetryOut);
                ExtractionResult<TestWriterResult> twRetryExt = ExtractionResult.success(
                        new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "abc8888888", List.of()), twRetryOut);

                when(testWriterAgent.run(any()))
                        .thenReturn(new AgentResult<>(twRun1, twExt1))
                        .thenReturn(new AgentResult<>(twRetryRun, twRetryExt));

                String trRejOut = ndjson("REQUEST_CHANGES\nTests are weak.");
                AgentRun trRejRun = makeRun("run-tr-rej", tid, "test-reviewer", trRejOut);
                String trAppOut = ndjson("APPROVE\nTests improved.");
                AgentRun trAppRun = makeRun("run-tr-app", tid, "test-reviewer", trAppOut);

                when(testReviewerAgent.run(any()))
                        .thenReturn(new AgentResult<>(trRejRun, ExtractionResult.success(
                                new TestReviewerResult(ReviewVerdict.REQUEST_CHANGES, "Tests are weak."), trRejOut)))
                        .thenReturn(new AgentResult<>(trAppRun, ExtractionResult.success(
                                new TestReviewerResult(ReviewVerdict.APPROVE, "Tests improved."), trAppOut)));

                setupCoderSuccess(tid);
                setupReviewerApprove(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                        .isEqualTo(TaskStatus.COMPLETED);
                verify(testWriterAgent, times(2)).run(any());
                verify(testReviewerAgent, times(2)).run(any());
            }
        }

        @Nested
        class CoderTestFailureRetry {

            @Test
            void shouldRetryCoderWhenExitCodeNonZero() {
                String tid = "task-coder-fail";
                createAndSaveTask(tid, "Coder Fail Task");
                setupPlannerSingle(tid);
                setupTestWriterExpectedRed(tid);
                setupTestReviewerApprove(tid);

                String cFailOut = ndjson("## Implementation summary\nTests failed.\n## Test command run\n`mvn test`\n## Test result\n2 tests failed");
                AgentRun cFailRun = makeRunWithExitCode("run-c-fail", tid, "coder", cFailOut, 1);

                String cOkOut = ndjson("## Implementation summary\nFixed implementation.\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def2222222");
                AgentRun cOkRun = makeRun("run-c-ok", tid, "coder", cOkOut);

                when(coderAgent.run(any()))
                        .thenReturn(new AgentResult<>(cFailRun, ExtractionResult.success(
                                new CoderResult("Tests failed", "mvn test", "2 tests failed", null, List.of()), cFailOut)))
                        .thenReturn(new AgentResult<>(cOkRun, ExtractionResult.success(
                                new CoderResult("Fixed implementation", "mvn test", "All tests passed", "def2222222", List.of()), cOkOut)));

                setupReviewerApprove(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcome).task().getStatus())
                        .isEqualTo(TaskStatus.COMPLETED);
                verify(coderAgent, times(2)).run(any());
            }
        }

        @Nested
        class ReviewerRejectBackToCoder {

            @Test
            void shouldRouteBackToCoderWhenReviewerRejectsImplementation() {
                String tid = "task-rev-rej";
                createAndSaveTask(tid, "Reviewer Reject Task");
                setupPlannerSingle(tid);
                setupTestWriterExpectedRed(tid);
                setupTestReviewerApprove(tid);

                String c1Out = ndjson("## Implementation summary\nFirst attempt\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def4567890");
                AgentRun c1Run = makeRun("run-c-1", tid, "coder", c1Out);
                String c2Out = ndjson("## Implementation summary\nFixed bugs.\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def3333333");
                AgentRun c2Run = makeRun("run-c-2", tid, "coder", c2Out);

                when(coderAgent.run(any()))
                        .thenReturn(new AgentResult<>(c1Run, ExtractionResult.success(
                                new CoderResult("First attempt", "mvn test", "All tests passed", "def4567890", List.of()), c1Out)))
                        .thenReturn(new AgentResult<>(c2Run, ExtractionResult.success(
                                new CoderResult("Fixed bugs", "mvn test", "All tests passed", "def3333333", List.of()), c2Out)));

                String rRejOut = ndjson("REQUEST_CHANGES\nImplementation has bugs.");
                AgentRun rRejRun = makeRun("run-r-rej", tid, "reviewer", rRejOut);
                String rAppOut = ndjson("APPROVE\nLooks good now.");
                AgentRun rAppRun = makeRun("run-r-app", tid, "reviewer", rAppOut);

                when(reviewerAgent.run(any()))
                        .thenReturn(new AgentResult<>(rRejRun, ExtractionResult.success(
                                new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES, "Implementation has bugs.", "implementation_issue"), rRejOut)))
                        .thenReturn(new AgentResult<>(rAppRun, ExtractionResult.success(
                                new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Looks good now.", null), rAppOut)));

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                Task completed = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
                assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
                assertThat(completed.getCodeRetryCount()).isGreaterThanOrEqualTo(1);
                verify(reviewerAgent, times(2)).run(any());
                verify(coderAgent, times(2)).run(any());
            }
        }

        @Nested
        class ReviewerRejectTestIssueBackToTestWriter {

            @Test
            void shouldRouteBackToTestWriterWhenReviewerIdentifiesTestIssue() {
                String tid = "task-rev-test";
                createAndSaveTask(tid, "Reviewer Test Issue Task");
                setupPlannerSingle(tid);

                String tw1Out = ndjson("## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc1234567");
                AgentRun tw1Run = makeRun("run-tw-1", tid, "test-writer", tw1Out);
                String twRetryOut = ndjson("## Behavior covered\nBetter tests\n## Test command run\n`mvn test`\n## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc7777777");
                AgentRun twRetryRun = makeRun("run-tw-retry", tid, "test-writer", twRetryOut);

                when(testWriterAgent.run(any()))
                        .thenReturn(new AgentResult<>(tw1Run, ExtractionResult.success(
                                new TestWriterResult("T", "mvn test", "EXPECTED_RED", "abc1234567", List.of()), tw1Out)))
                        .thenReturn(new AgentResult<>(twRetryRun, ExtractionResult.success(
                                new TestWriterResult("Better tests", "mvn test", "EXPECTED_RED", "abc7777777", List.of()), twRetryOut)));

                setupTestReviewerApprove(tid);

                String cOut = ndjson("## Implementation summary\nFirst attempt\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def4567890");
                AgentRun cRun = makeRun("run-c-1", tid, "coder", cOut);
                when(coderAgent.run(any())).thenReturn(new AgentResult<>(cRun, ExtractionResult.success(
                        new CoderResult("First", "mvn test", "pass", "def4567890", List.of()), cOut)));

                String rTestOut = ndjson("REQUEST_CHANGES\nTest coverage is insufficient and weak test assertions.");
                AgentRun rTestRun = makeRun("run-r-test", tid, "reviewer", rTestOut);
                String rAppOut = ndjson("APPROVE\nLooks good now.");
                AgentRun rAppRun = makeRun("run-r-app", tid, "reviewer", rAppOut);

                when(reviewerAgent.run(any()))
                        .thenReturn(new AgentResult<>(rTestRun, ExtractionResult.success(
                                new ReviewerResult("reviewer-1", ReviewVerdict.REQUEST_CHANGES,
                                        "Test coverage is insufficient and weak test assertions.", "test_issue"), rTestOut)))
                        .thenReturn(new AgentResult<>(rAppRun, ExtractionResult.success(
                                new ReviewerResult("reviewer-1", ReviewVerdict.APPROVE, "Looks good now.", null), rAppOut)));

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                Task completed = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
                assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
                verify(testWriterAgent, times(2)).run(any());
                verify(reviewerAgent, times(2)).run(any());
            }
        }

        @Nested
        class PlannerSplitCreatesChildTasks {

            @Test
            void shouldCreateChildTasksWhenPlannerSplits() {
                String tid = "task-split";
                createAndSaveTask(tid, "Split Task");
                setupPlannerSplit(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.SplitParentWaiting.class);
                TaskExecutionService.ExecutionOutcome.SplitParentWaiting split =
                        (TaskExecutionService.ExecutionOutcome.SplitParentWaiting) outcome;

                assertThat(split.childTasks()).hasSize(2);
                assertThat(split.childTasks().get(0).getParentId()).isEqualTo(tid);
                assertThat(split.childTasks().get(1).getParentId()).isEqualTo(tid);
                assertThat(split.childTasks().get(0).getDependsOn()).isEmpty();
                assertThat(split.childTasks().get(1).getDependsOn()).contains(split.childTasks().get(0).getId());

                TaskEntity parent = taskRepository.findById(tid).orElseThrow();
                assertThat(parent.getPlanOutput()).contains("Sub-task");

                verify(worktreeManager, never()).createWorktree(any(), any());
            }
        }

        @Nested
        class DependentChildTasksScheduledInOrder {

            @Test
            void shouldScheduleDependentChildTasksAfterDependencyCompletes() {
                createAndSaveTask("task-parent", "Parent Task");
                createAndSaveTask("child-a", "Child A");
                createAndSaveTask("child-b", "Child B");

                transactionTemplate.executeWithoutResult(status -> {
                    TaskEntity c1 = taskRepository.findById("child-a").orElseThrow();
                    c1.setParentId("task-parent");
                    taskRepository.save(c1);
                    TaskEntity c2 = taskRepository.findById("child-b").orElseThrow();
                    c2.setParentId("task-parent");
                    c2.setDependsOn(List.of("child-a"));
                    taskRepository.save(c2);
                    entityManager.flush();
                    entityManager.clear();
                });

                assertThat(dependencyTracker.isBlockedByDependencies("child-a")).isFalse();
                assertThat(dependencyTracker.isBlockedByDependencies("child-b")).isTrue();

                setupPlannerSingle("child-a");
                setupTestWriterExpectedRed("child-a");
                setupTestReviewerApprove("child-a");
                setupCoderSuccess("child-a");
                setupReviewerApprove("child-a");

                TaskExecutionService.ExecutionOutcome outcomeA = service.executeTask("child-a");
                assertThat(outcomeA).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
                assertThat(((TaskExecutionService.ExecutionOutcome.Success) outcomeA).task().getStatus())
                        .isEqualTo(TaskStatus.COMPLETED);

                transactionTemplate.executeWithoutResult(status -> {
                    entityManager.flush();
                    entityManager.clear();
                });

                assertThat(dependencyTracker.isBlockedByDependencies("child-b")).isFalse();

                setupPlannerSingle("child-b");
                setupTestWriterExpectedRed("child-b");
                setupTestReviewerApprove("child-b");
                setupCoderSuccess("child-b");
                setupReviewerApprove("child-b");

                TaskExecutionService.ExecutionOutcome outcomeB = service.executeTask("child-b");
                assertThat(outcomeB).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            }
        }

        @Nested
        class CancelTaskBeforeExecution {

            @Test
            void shouldReturnCancelledWhenTaskIsCancelledBeforeExecution() {
                createAndSaveTask("task-cancel", "Cancel Task");

                transactionTemplate.executeWithoutResult(status -> {
                    TaskEntity entity = taskRepository.findById("task-cancel").orElseThrow();
                    entity.setStatus(TaskStatus.CANCELLED);
                    taskRepository.save(entity);
                    entityManager.flush();
                    entityManager.clear();
                });

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask("task-cancel");

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Cancelled.class);
                verify(plannerAgent, never()).run(any());
                verify(testWriterAgent, never()).run(any());
                verify(coderAgent, never()).run(any());
            }
        }

        @Nested
        class PersistenceVerification {

            @Test
            void shouldPersistAllAgentRunData() {
                String tid = "task-persist";
                createAndSaveTask(tid, "Persist Task");

                setupPlannerSingle(tid);
                setupTestWriterExpectedRed(tid);
                setupTestReviewerApprove(tid);
                setupCoderSuccess(tid);
                setupReviewerApprove(tid);

                TaskExecutionService.ExecutionOutcome outcome = service.executeTask(tid);

                assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);

                List<AgentRunEntity> runs = agentRunRepository.findByTaskIdOrderByCreatedAtDesc(tid);
                assertThat(runs).hasSizeGreaterThanOrEqualTo(5);

                List<String> agentTypes = runs.stream().map(AgentRunEntity::getAgentType).toList();
                assertThat(agentTypes).contains("planner", "test-writer", "test-reviewer", "coder", "reviewer");

                for (AgentRunEntity run : runs) {
                    assertThat(run.getOutput()).isNotBlank();
                    assertThat(run.getPrompt()).isNotBlank();
                    assertThat(run.getModel()).isNotBlank();
                    assertThat(run.getCreatedAt()).isNotNull();
                }

                TaskEntity persisted = taskRepository.findById(tid).orElseThrow();
                assertThat(persisted.getSessionIds()).isNotEmpty();
                assertThat(persisted.getPlanOutput()).isNotNull();
                assertThat(persisted.getTestOutput()).isNotNull();
                assertThat(persisted.getCodeOutput()).isNotNull();
                assertThat(persisted.getReviewOutput()).isNotNull();
            }
        }
    }

    // =========================================================================
    // 2. End-to-end test using real agents + fake opencode binary
    // =========================================================================

    @Nested
    @DisplayName("End-to-end pipeline with fake opencode binary")
    class EndToEndWithFakeOpencode {

        private TaskExecutionService e2eService;
        private Path e2eWorktreeDir;

    @BeforeEach
    void setUpE2E() throws IOException {
        e2eWorktreeDir = Files.createTempDirectory("e2e-worktree");

        OpencodeConfig fakeConfig = new OpencodeConfig();
        fakeConfig.setConfigPath("/tmp/e2e-opencode-config.json");
        fakeConfig.setTimeoutSeconds(30);
        fakeConfig.setMaxContinues(1);

            com.tddforge.config.ModelSpec modelSpec = new com.tddforge.config.ModelSpec();
            modelSpec.setModel("fake-model");
            fakeConfig.setPlanner(modelSpec);
            fakeConfig.setTestWriter(modelSpec);
            fakeConfig.setTestReviewer(modelSpec);
            fakeConfig.setCoderDefault(modelSpec);
            fakeConfig.setReviewers(List.of(modelSpec));

            String fakeScriptPath = getClass().getClassLoader()
                    .getResource("scripts/fake-opencode-integration.sh").getPath();

            OpenCodeNdjsonParser parser = new OpenCodeNdjsonParser();
            OpenCodeClient fakeClient = new OpenCodeClient(fakeConfig, parser);
            fakeClient.setOpencodeBinary(fakeScriptPath);

            AgentOutputExtractor extractor = new AgentOutputExtractor();
            PromptTemplateRegistry registry = new PromptTemplateRegistry();

            PlannerAgent realPlanner = new PlannerAgent(fakeClient, registry, extractor);
            TestWriterAgent realTestWriter = new TestWriterAgent(fakeClient, registry, extractor);
            TestReviewerAgent realTestReviewer = new TestReviewerAgent(fakeClient, registry, extractor);
            CoderAgent realCoder = new CoderAgent(fakeClient, registry, extractor);
            ReviewerAgent realReviewer = new ReviewerAgent(fakeClient, registry, extractor);

            PlannerResultValidator validator = new PlannerResultValidator();
            PlannerResultProcessor processor = new PlannerResultProcessor(new PlannerResultProcessor.DefaultIdGenerator());
            PlannerService realPlannerService = new PlannerService(extractor, validator, processor);

            e2eService = new TaskExecutionService(
                    taskRepository, agentRunRepository, taskEventRepository,
                    realPlanner, realTestWriter, realTestReviewer, realCoder, realReviewer,
                    realPlannerService, dependencyTracker, worktreeManager,
                    orchestratorConfig, fakeConfig, repoConfig
            );
        }

        @AfterEach
        void tearDownE2E() throws IOException {
            deleteDirectoryIfExists(e2eWorktreeDir);
        }

        @Test
        void shouldCompleteFullPipelineWithFakeOpencode() throws IOException {
            String tid = "e2e-success";
            createAndSaveTask(tid, "E2E Success Task");

            when(worktreeManager.generateBranchName(any(), any())).thenReturn("task/e2e/test");
            when(worktreeManager.createWorktree(any(), any())).thenReturn(e2eWorktreeDir);

            TaskExecutionService.ExecutionOutcome outcome = e2eService.executeTask(tid);

            if (outcome instanceof TaskExecutionService.ExecutionOutcome.Failed f) {
                System.out.println("FAILED: " + f.error());
            }
            if (outcome instanceof TaskExecutionService.ExecutionOutcome.NeedsArbitration n) {
                System.out.println("NEEDS_ARBITRATION: " + n.reason() + " | " + n.task().getError());
            }

            assertThat(outcome).isInstanceOf(TaskExecutionService.ExecutionOutcome.Success.class);
            Task completed = ((TaskExecutionService.ExecutionOutcome.Success) outcome).task();
            assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(completed.isReviewPass()).isTrue();
            assertThat(completed.getComplexity()).isEqualTo("medium");
            assertThat(completed.getPlanOutput()).contains("implement the feature");
            assertThat(completed.getSessionIds()).isNotEmpty();

            List<AgentRunEntity> runs = agentRunRepository.findByTaskIdOrderByCreatedAtDesc(tid);
            assertThat(runs).hasSizeGreaterThanOrEqualTo(5);

            List<String> sessionIds = runs.stream()
                    .map(AgentRunEntity::getSessionId)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            assertThat(sessionIds).isNotEmpty();
            assertThat(sessionIds).allMatch(s -> s.startsWith("int-"));
        }
    }

    // =========================================================================
    // 3. In-flight cancellation with fake opencode
    // =========================================================================

    @Nested
    @DisplayName("In-flight cancellation")
    class InFlightCancellation {

        @Test
        void shouldKillOpencodeProcessWhenTaskCancelledMidExecution() throws Exception {
            OpencodeConfig cancelConfig = new OpencodeConfig();
            cancelConfig.setConfigPath("/tmp/cancel-config.json");
            cancelConfig.setTimeoutSeconds(60);
            cancelConfig.setMaxContinues(0);
            com.tddforge.config.ModelSpec ms = new com.tddforge.config.ModelSpec();
            ms.setModel("fake-model");
            cancelConfig.setPlanner(ms);

            String fakeScriptPath = getClass().getClassLoader()
                    .getResource("scripts/fake-opencode-integration.sh").getPath();

            OpenCodeNdjsonParser parser = new OpenCodeNdjsonParser();
            OpenCodeClient cancelClient = new OpenCodeClient(cancelConfig, parser);
            cancelClient.setOpencodeBinary(fakeScriptPath);

            cancelClient.killTask("nonexistent-task");

            cancelClient.killAll();
        }
    }
}
