package com.tddforge.agent;

import com.tddforge.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTest {

    private TestOpenCodeClient testClient;
    private PromptTemplateRegistry registry;
    private AgentOutputExtractor extractor;

    @BeforeEach
    void setUp() {
        testClient = new TestOpenCodeClient();
        registry = new PromptTemplateRegistry();
        extractor = new AgentOutputExtractor();
    }

    private AgentContext createContext() {
        return new AgentContext(
                "task-1",
                Path.of("/tmp/worktree"),
                "session-123",
                new ModelSpec("test-model", "v1", "agent-x"),
                "Fix login bug",
                "The login endpoint returns 500 on invalid input",
                "/repo",
                3600L,
                false,
                "Implement fix for login validation",
                "Test output from TestWriter",
                "Test review approved",
                "TestWriter wrote 3 tests covering validation",
                "Implemented null check in Validator",
                "",
                "src/main/Validator.java",
                "42",
                "",
                "reviewer-1",
                null,
                null,
                null
        );
    }

    private AgentContext createContext(Map<String, Object> overrides) {
        AgentContext base = createContext();
        return new AgentContext(
                overrides.containsKey("taskId") ? (String) overrides.get("taskId") : base.taskId(),
                overrides.containsKey("worktreePath") ? (Path) overrides.get("worktreePath") : base.worktreePath(),
                overrides.containsKey("sessionId") ? (String) overrides.get("sessionId") : base.sessionId(),
                overrides.containsKey("modelSpec") ? (ModelSpec) overrides.get("modelSpec") : base.modelSpec(),
                overrides.containsKey("title") ? (String) overrides.get("title") : base.title(),
                overrides.containsKey("description") ? (String) overrides.get("description") : base.description(),
                overrides.containsKey("repoPath") ? (String) overrides.get("repoPath") : base.repoPath(),
                overrides.containsKey("timeoutSeconds") ? (Long) overrides.get("timeoutSeconds") : base.timeoutSeconds(),
                overrides.containsKey("forceNoSplit") ? (Boolean) overrides.get("forceNoSplit") : base.forceNoSplit(),
                overrides.containsKey("planOutput") ? (String) overrides.get("planOutput") : base.planOutput(),
                overrides.containsKey("testOutput") ? (String) overrides.get("testOutput") : base.testOutput(),
                overrides.containsKey("testReviewOutput") ? (String) overrides.get("testReviewOutput") : base.testReviewOutput(),
                overrides.containsKey("testWriterResponse") ? (String) overrides.get("testWriterResponse") : base.testWriterResponse(),
                overrides.containsKey("coderResponse") ? (String) overrides.get("coderResponse") : base.coderResponse(),
                overrides.containsKey("priorRejections") ? (String) overrides.get("priorRejections") : base.priorRejections(),
                overrides.containsKey("filePath") ? (String) overrides.get("filePath") : base.filePath(),
                overrides.containsKey("lineNumber") ? (String) overrides.get("lineNumber") : base.lineNumber(),
                overrides.containsKey("dependencyContext") ? (String) overrides.get("dependencyContext") : base.dependencyContext(),
                overrides.containsKey("reviewerId") ? (String) overrides.get("reviewerId") : base.reviewerId(),
                overrides.containsKey("testPhaseFeedback") ? (String) overrides.get("testPhaseFeedback") : base.testPhaseFeedback(),
                overrides.containsKey("attempt") ? (Integer) overrides.get("attempt") : base.attempt(),
                overrides.containsKey("humanRevisionFeedback") ? (String) overrides.get("humanRevisionFeedback") : base.humanRevisionFeedback()
        );
    }

    private Map<String, Object> withNulls(Object... keysAndValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private String wrapInNdjson(String text) {
        return """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"%s"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.formatted(text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
    }

    @Nested
    class PlannerAgentTests {

        @Test
        void shouldRenderPromptVariablesIntoRequest() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Fix login bug");
            assertThat(call.request().prompt()).contains("The login endpoint returns 500 on invalid input");
            assertThat(call.request().prompt()).contains("/repo");
            assertThat(call.request().prompt()).doesNotContain("{{");
        }

        @Test
        void shouldForwardSessionId() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().sessionId()).isEqualTo("session-123");
        }

        @Test
        void shouldForwardModelVariantAndAgent() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().model()).isEqualTo("test-model");
            assertThat(call.request().variant()).isEqualTo("v1");
            assertThat(call.request().agent()).isEqualTo("agent-x");
        }

        @Test
        void shouldReturnAgentRunWithExtractionResult() {
            String plannerOutput = wrapInNdjson(
                    "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Fix the bug\"}");
            testClient.setNextResult(new AgentRun(
                    "run-1", "task-1", "planner", "test-model",
                    null, null, "prompt", plannerOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            AgentResult<PlannerResult> result = agent.run(createContext());

            assertThat(result.agentRun()).isNotNull();
            assertThat(result.extractionResult()).isNotNull();
            assertThat(result.extractionResult().hasCriticalError()).isFalse();
            assertThat(result.extractionResult().result()).isNotNull();
            assertThat(result.extractionResult().result().complexity()).isEqualTo("medium");
            assertThat(result.extractionResult().result().split()).isFalse();
        }

        @Test
        void shouldUseAnalyzeSplitTemplateByDefault() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Step 2 — Decide whether to split");
        }

        @Test
        void shouldUseNoSplitTemplateWhenForceNoSplit() {
            AgentContext ctx = createContext(withNulls("forceNoSplit", true));
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(ctx);

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Splitting is forbidden");
        }

        @Test
        void shouldSetCorrectAgentType() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.agentType()).isEqualTo("planner");
        }

        @Test
        void shouldSetCorrectTaskId() {
            PlannerAgent agent = new PlannerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.taskId()).isEqualTo("task-1");
        }
    }

    @Nested
    class TestWriterAgentTests {

        @Test
        void shouldRenderPromptVariablesIntoRequest() {
            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Fix login bug");
            assertThat(call.request().prompt()).contains("The login endpoint returns 500 on invalid input");
            assertThat(call.request().prompt()).contains("/repo");
            assertThat(call.request().prompt()).contains("Implement fix for login validation");
            assertThat(call.request().prompt()).doesNotContain("{{");
        }

        @Test
        void shouldForwardSessionId() {
            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().sessionId()).isEqualTo("session-123");
        }

        @Test
        void shouldForwardModelVariantAndAgent() {
            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().model()).isEqualTo("test-model");
            assertThat(call.request().variant()).isEqualTo("v1");
            assertThat(call.request().agent()).isEqualTo("agent-x");
        }

        @Test
        void shouldReturnAgentRunWithExtractionResult() {
            String testWriterOutput = wrapInNdjson(
                    "Test files changed:\n- src/test/FooTest.java\n\nTest command: `mvn test`\n\nResult classification: PASS\n\nBehavior covered: Tests verify validation\n\nCommit hash: abc1234");
            testClient.setNextResult(new AgentRun(
                    "run-2", "task-1", "test_writer", "test-model",
                    null, null, "prompt", testWriterOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            AgentResult<TestWriterResult> result = agent.run(createContext());

            assertThat(result.agentRun()).isNotNull();
            assertThat(result.extractionResult()).isNotNull();
            assertThat(result.extractionResult().hasCriticalError()).isFalse();
            assertThat(result.extractionResult().result()).isNotNull();
            assertThat(result.extractionResult().result().resultClassification()).isEqualTo("PASS");
        }

        @Test
        void shouldSetCorrectAgentType() {
            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.agentType()).isEqualTo("test_writer");
        }

        @Test
        void shouldHandleNullPlanOutput() {
            AgentContext ctx = createContext(withNulls("planOutput", null));
            TestWriterAgent agent = new TestWriterAgent(testClient, registry, extractor);
            agent.run(ctx);

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).doesNotContain("{{");
        }
    }

    @Nested
    class TestReviewerAgentTests {

        @Test
        void shouldRenderPromptVariablesIntoRequest() {
            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Fix login bug");
            assertThat(call.request().prompt()).contains("The login endpoint returns 500 on invalid input");
            assertThat(call.request().prompt()).contains("Implement fix for login validation");
            assertThat(call.request().prompt()).contains("TestWriter wrote 3 tests covering validation");
            assertThat(call.request().prompt()).doesNotContain("{{");
        }

        @Test
        void shouldForwardSessionId() {
            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().sessionId()).isEqualTo("session-123");
        }

        @Test
        void shouldForwardModelVariantAndAgent() {
            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().model()).isEqualTo("test-model");
            assertThat(call.request().variant()).isEqualTo("v1");
            assertThat(call.request().agent()).isEqualTo("agent-x");
        }

        @Test
        void shouldReturnAgentRunWithExtractionResult() {
            String reviewOutput = wrapInNdjson("APPROVE\n\nThe tests are well-written and cover expected behavior.");
            testClient.setNextResult(new AgentRun(
                    "run-3", "task-1", "test_reviewer", "test-model",
                    null, null, "prompt", reviewOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            AgentResult<TestReviewerResult> result = agent.run(createContext());

            assertThat(result.agentRun()).isNotNull();
            assertThat(result.extractionResult()).isNotNull();
            assertThat(result.extractionResult().hasCriticalError()).isFalse();
            assertThat(result.extractionResult().result()).isNotNull();
            assertThat(result.extractionResult().result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
        }

        @Test
        void shouldSetCorrectAgentType() {
            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.agentType()).isEqualTo("test_reviewer");
        }

        @Test
        void shouldHandleNullTestWriterResponse() {
            AgentContext ctx = createContext(withNulls("testWriterResponse", null));
            TestReviewerAgent agent = new TestReviewerAgent(testClient, registry, extractor);
            agent.run(ctx);

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).doesNotContain("{{");
        }
    }

    @Nested
    class CoderAgentTests {

        @Test
        void shouldRenderPromptVariablesIntoRequest() {
            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Fix login bug");
            assertThat(call.request().prompt()).contains("The login endpoint returns 500 on invalid input");
            assertThat(call.request().prompt()).contains("Test output from TestWriter");
            assertThat(call.request().prompt()).contains("Test review approved");
            assertThat(call.request().prompt()).contains("Implement fix for login validation");
            assertThat(call.request().prompt()).contains("src/main/Validator.java");
            assertThat(call.request().prompt()).contains("42");
            assertThat(call.request().prompt()).doesNotContain("{{");
        }

        @Test
        void shouldForwardSessionId() {
            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().sessionId()).isEqualTo("session-123");
        }

        @Test
        void shouldForwardModelVariantAndAgent() {
            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().model()).isEqualTo("test-model");
            assertThat(call.request().variant()).isEqualTo("v1");
            assertThat(call.request().agent()).isEqualTo("agent-x");
        }

        @Test
        void shouldReturnAgentRunWithExtractionResult() {
            String coderOutput = wrapInNdjson(
                    "Implementation summary: Updated validator\n\nFiles changed:\n- src/main/Validator.java\n\nTest command: `mvn test`\nTest result: All tests passed\n\nCommit hash: def5678");
            testClient.setNextResult(new AgentRun(
                    "run-4", "task-1", "coder", "test-model",
                    null, null, "prompt", coderOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            AgentResult<CoderResult> result = agent.run(createContext());

            assertThat(result.agentRun()).isNotNull();
            assertThat(result.extractionResult()).isNotNull();
            assertThat(result.extractionResult().hasCriticalError()).isFalse();
            assertThat(result.extractionResult().result()).isNotNull();
            assertThat(result.extractionResult().result().summary()).contains("Updated validator");
        }

        @Test
        void shouldSetCorrectAgentType() {
            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.agentType()).isEqualTo("coder");
        }

        @Test
        void shouldHandleNullOptionalFields() {
            AgentContext ctx = createContext(withNulls(
                    "testOutput", null,
                    "testReviewOutput", null,
                    "filePath", null,
                    "lineNumber", null,
                    "dependencyContext", null
            ));
            CoderAgent agent = new CoderAgent(testClient, registry, extractor);
            agent.run(ctx);

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).doesNotContain("{{");
        }
    }

    @Nested
    class ReviewerAgentTests {

        @Test
        void shouldRenderPromptVariablesIntoRequest() {
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).contains("Fix login bug");
            assertThat(call.request().prompt()).contains("The login endpoint returns 500 on invalid input");
            assertThat(call.request().prompt()).contains("Test output from TestWriter");
            assertThat(call.request().prompt()).contains("Test review approved");
            assertThat(call.request().prompt()).contains("Implemented null check in Validator");
            assertThat(call.request().prompt()).doesNotContain("{{");
        }

        @Test
        void shouldForwardSessionId() {
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().sessionId()).isEqualTo("session-123");
        }

        @Test
        void shouldForwardModelVariantAndAgent() {
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().model()).isEqualTo("test-model");
            assertThat(call.request().variant()).isEqualTo("v1");
            assertThat(call.request().agent()).isEqualTo("agent-x");
        }

        @Test
        void shouldReturnAgentRunWithExtractionResult() {
            String reviewOutput = wrapInNdjson("APPROVE\n\nThe implementation looks good. All tests pass.");
            testClient.setNextResult(new AgentRun(
                    "run-5", "task-1", "reviewer", "test-model",
                    null, null, "prompt", reviewOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            AgentResult<ReviewerResult> result = agent.run(createContext());

            assertThat(result.agentRun()).isNotNull();
            assertThat(result.extractionResult()).isNotNull();
            assertThat(result.extractionResult().hasCriticalError()).isFalse();
            assertThat(result.extractionResult().result()).isNotNull();
            assertThat(result.extractionResult().result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
            assertThat(result.extractionResult().result().reviewerId()).isEqualTo("reviewer-1");
        }

        @Test
        void shouldUseReviewerIdFromContext() {
            String reviewOutput = wrapInNdjson("APPROVE\n\nLooks good.");
            testClient.setNextResult(new AgentRun(
                    "run-6", "task-1", "reviewer", "test-model",
                    null, null, "prompt", reviewOutput,
                    0, 100L, null, 0, Instant.now()
            ));

            AgentContext ctx = createContext(withNulls("reviewerId", "custom-reviewer-42"));
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            AgentResult<ReviewerResult> result = agent.run(ctx);

            assertThat(result.extractionResult().result().reviewerId()).isEqualTo("custom-reviewer-42");
        }

        @Test
        void shouldSetCorrectAgentType() {
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            agent.run(createContext());

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.agentType()).isEqualTo("reviewer");
        }

        @Test
        void shouldHandleNullOptionalFields() {
            AgentContext ctx = createContext(withNulls(
                    "coderResponse", null,
                    "priorRejections", null
            ));
            ReviewerAgent agent = new ReviewerAgent(testClient, registry, extractor);
            agent.run(ctx);

            TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
            assertThat(call.request().prompt()).doesNotContain("{{");
        }
    }

    @Nested
    class CommonTests {

        @Test
        void allAgentsShouldForwardWorktreePath() {
            AgentContext ctx = createContext();

            for (var agent : new Agent<?>[]{
                    new PlannerAgent(testClient, registry, extractor),
                    new TestWriterAgent(testClient, registry, extractor),
                    new TestReviewerAgent(testClient, registry, extractor),
                    new CoderAgent(testClient, registry, extractor),
                    new ReviewerAgent(testClient, registry, extractor)
            }) {
                testClient.getCapturedCalls().clear();
                agent.run(ctx);
                TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
                assertThat(call.request().worktreeDir()).isEqualTo(Path.of("/tmp/worktree"));
            }
        }

        @Test
        void allAgentsShouldForwardTimeout() {
            AgentContext ctx = createContext();

            for (var agent : new Agent<?>[]{
                    new PlannerAgent(testClient, registry, extractor),
                    new TestWriterAgent(testClient, registry, extractor),
                    new TestReviewerAgent(testClient, registry, extractor),
                    new CoderAgent(testClient, registry, extractor),
                    new ReviewerAgent(testClient, registry, extractor)
            }) {
                testClient.getCapturedCalls().clear();
                agent.run(ctx);
                TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
                assertThat(call.request().timeoutSeconds()).isEqualTo(3600L);
            }
        }

        @Test
        void allAgentsShouldRenderPromptsWithoutUnresolvedPlaceholders() {
            AgentContext ctx = createContext();

            for (var agent : new Agent<?>[]{
                    new PlannerAgent(testClient, registry, extractor),
                    new TestWriterAgent(testClient, registry, extractor),
                    new TestReviewerAgent(testClient, registry, extractor),
                    new CoderAgent(testClient, registry, extractor),
                    new ReviewerAgent(testClient, registry, extractor)
            }) {
                testClient.getCapturedCalls().clear();
                agent.run(ctx);
                TestOpenCodeClient.CapturedCall call = testClient.getLastCall();
                assertThat(call.request().prompt()).doesNotContain("{{");
            }
        }

        @Test
        void allAgentsShouldReturnNonNullExtractionResults() {
            AgentContext ctx = createContext();

            for (var agent : new Agent<?>[]{
                    new PlannerAgent(testClient, registry, extractor),
                    new TestWriterAgent(testClient, registry, extractor),
                    new TestReviewerAgent(testClient, registry, extractor),
                    new CoderAgent(testClient, registry, extractor),
                    new ReviewerAgent(testClient, registry, extractor)
            }) {
                testClient.getCapturedCalls().clear();
                AgentResult<?> result = agent.run(ctx);
                assertThat(result.extractionResult()).isNotNull();
                assertThat(result.agentRun()).isNotNull();
            }
        }
    }
}
