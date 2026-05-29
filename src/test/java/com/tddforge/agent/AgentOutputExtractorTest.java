package com.tddforge.agent;

import com.tddforge.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOutputExtractorTest {

    private AgentOutputExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AgentOutputExtractor();
    }

    private AgentRun createRun(String output) {
        return new AgentRun(
                "run-1", "task-1", "planner", "test-model",
                null, null, "test prompt", output,
                0, 1000L, null, 0, Instant.now()
        );
    }

    private AgentRun createRun(String agentType, String output) {
        return new AgentRun(
                "run-1", "task-1", agentType, "test-model",
                null, null, "test prompt", output,
                0, 1000L, null, 0, Instant.now()
        );
    }

    private String wrapInNdjson(String text) {
        return """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"%s"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.formatted(text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
    }

    private String ndjsonWithText(String text) {
        return "{\"type\":\"step_start\",\"step_start\":{\"type\":\"thinking\"}}\n" +
                "{\"type\":\"text\",\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n" +
                "{\"type\":\"step_finish\",\"step_finish\":{\"reason\":\"stop\"}}";
    }

    @Nested
    class PlannerExtraction {

        @Test
        void shouldExtractStandardPlannerOutput() {
            String output = ndjsonWithText(
                    "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Fix the bug\"}");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result()).isNotNull();
            assertThat(result.result().complexity()).isEqualTo("medium");
            assertThat(result.result().split()).isFalse();
            assertThat(result.result().reason()).isEqualTo("Simple scope");
            assertThat(result.result().plan()).isEqualTo("Fix the bug");
        }

        @Test
        void shouldExtractPlannerWithSubTasks() {
            String output = ndjsonWithText(
                    "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Task A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]},{\"title\":\"Task B\",\"description\":\"Do B\",\"priority\":\"medium\",\"depends_on\":[0]}]}");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().split()).isTrue();
            assertThat(result.result().subTasks()).hasSize(2);
            assertThat(result.result().subTasks().get(0).title()).isEqualTo("Task A");
            assertThat(result.result().subTasks().get(0).dependsOn()).isEmpty();
            assertThat(result.result().subTasks().get(1).dependsOn()).containsExactly(0);
        }

        @Test
        void shouldMapSnakeCaseFields() {
            String output = ndjsonWithText(
                    "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Task A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]}]}");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().subTasks()).isNotNull();
            assertThat(result.result().subTasks().get(0).dependsOn()).isEmpty();
        }

        @Test
        void shouldHandlePlannerJsonEmbeddedInMarkdown() {
            String json = """
                    Here is my analysis:

                    ```json
                    {"complexity":"simple","split":false,"reason":"One-liner","plan":"Fix the typo"}
                    ```

                    This should be straightforward.
                    """;
            String output = wrapInNdjson(json);

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().complexity()).isEqualTo("simple");
        }

        @Test
        void shouldReturnCriticalErrorForInvalidJson() {
            String output = wrapInNdjson("This is not valid JSON at all. No braces here.");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isTrue();
            assertThat(result.criticalError()).contains("No valid JSON found");
        }

        @Test
        void shouldReturnCriticalErrorForMalformedJson() {
            String output = wrapInNdjson("{invalid json here}");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isTrue();
        }

        @Test
        void shouldPreserveRawOutput() {
            String raw = wrapInNdjson("test output");
            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(raw));

            assertThat(result.rawOutput()).isEqualTo(raw);
        }

        @Test
        void shouldExtractJsonWithBracesInsideStrings() {
            String output = ndjsonWithText(
                    "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Has {braces} in reason\",\"plan\":\"Fix {the} bug\"}");

            ExtractionResult<PlannerResult> result = extractor.extractPlannerResult(createRun(output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().reason()).contains("{braces}");
            assertThat(result.result().plan()).contains("{the}");
        }
    }

    @Nested
    class TestWriterExtraction {

        @Test
        void shouldExtractPassClassification() {
            String text = """
                    Test files changed:
                    - src/test/FooTest.java

                    Test command: `mvn test -pl module`

                    Result classification: PASS

                    Commit hash: abc1234

                    Behavior covered: Tests verify the new validation logic
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().resultClassification()).isEqualTo("PASS");
            assertThat(result.result().testCommand()).contains("mvn test");
            assertThat(result.result().commitHash()).isEqualTo("abc1234");
            assertThat(result.result().filesChanged()).contains("src/test/FooTest.java");
        }

        @Test
        void shouldExtractExpectedRedClassification() {
            String text = """
                    Test command: `mvn test`

                    Result classification: EXPECTED_RED

                    The tests compile and run but fail because the behavior is not implemented yet.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().resultClassification()).isEqualTo("EXPECTED_RED");
        }

        @Test
        void shouldExtractInvalidClassification() {
            String text = """
                    Test command: `mvn test`

                    Result classification: INVALID

                    The tests cannot compile due to missing dependencies.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().resultClassification()).isEqualTo("INVALID");
        }

        @Test
        void shouldReturnCriticalErrorWhenClassificationMissing() {
            String text = """
                    Test command: `mvn test`

                    The tests were written successfully.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isTrue();
            assertThat(result.criticalError()).contains("classification");
        }

        @Test
        void shouldReturnWarningsForMissingNonCriticalFields() {
            String text = """
                    Result classification: EXPECTED_RED
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).hasSize(2);
            assertThat(result.result()).isNotNull();
        }

        @Test
        void shouldExtractMultipleTestFiles() {
            String text = """
                    Test files changed:
                    - src/test/FooTest.java
                    - src/test/BarTest.java
                    - src/test/BazTest.java

                    Test command: `mvn test`
                    Result classification: PASS
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.result().filesChanged()).hasSize(3);
        }

        @Test
        void shouldHandleExtraMarkdownFormatting() {
            String text = """
                    ## Test Results

                    Here's what I did:

                    **Test command:** `mvn test -pl module`

                    **Classification:** PASS

                    ---

                    > All tests pass.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().resultClassification()).isEqualTo("PASS");
        }

        @Test
        void shouldExtractTestCommandWithoutBackticks() {
            String text = """
                    I ran: mvn test -pl module -Dtest=FooTest

                    Result classification: PASS
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestWriterResult> result = extractor.extractTestWriterResult(createRun("test_writer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().testCommand()).contains("mvn test");
        }
    }

    @Nested
    class TestReviewerExtraction {

        @Test
        void shouldExtractApproveVerdictAndFeedback() {
            String text = """
                    APPROVE

                    The tests are well-written and cover the expected behavior.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
            assertThat(result.result().feedback()).contains("well-written");
        }

        @Test
        void shouldExtractRequestChangesVerdictAndFeedback() {
            String text = """
                    REQUEST_CHANGES

                    The tests are too weak and don't cover edge cases.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
            assertThat(result.result().feedback()).contains("too weak");
        }

        @Test
        void shouldReturnCriticalErrorWhenVerdictMissing() {
            String text = """
                    The tests look okay but I have some concerns.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isTrue();
            assertThat(result.criticalError()).contains("verdict");
        }

        @Test
        void shouldUseFirstLineVerdictOnly() {
            String text = """
                    APPROVE

                    Actually, I think there are issues. REQUEST_CHANGES would be more appropriate.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
        }

        @Test
        void shouldHandleVerdictWithLeadingWhitespace() {
            String text = """


                    REQUEST_CHANGES

                    Need more coverage.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
        }

        @Test
        void shouldHandleBlankFeedbackWithWarning() {
            String text = "APPROVE";
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
            assertThat(result.result().feedback()).isEqualTo("(no feedback provided)");
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("feedback"));
        }

        @Test
        void shouldRejectVerdictLikeApproveChanges() {
            String text = """
                    APPROVE_CHANGES

                    This looks mostly fine.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<TestReviewerResult> result = extractor.extractTestReviewerResult(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isTrue();
        }
    }

    @Nested
    class ReviewVerdictOnlyExtraction {

        @Test
        void shouldExtractApproveVerdict() {
            String text = """
                    APPROVE

                    The tests are well-written and cover the expected behavior.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result()).isEqualTo(ReviewVerdict.APPROVE);
        }

        @Test
        void shouldExtractRequestChangesVerdict() {
            String text = """
                    REQUEST_CHANGES

                    The tests are too weak and don't cover edge cases.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
        }

        @Test
        void shouldReturnCriticalErrorWhenVerdictMissing() {
            String text = """
                    The tests look okay but I have some concerns.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isTrue();
            assertThat(result.criticalError()).contains("verdict");
        }

        @Test
        void shouldUseFirstLineVerdictOnly() {
            String text = """
                    APPROVE

                    Actually, REQUEST_CHANGES would be more appropriate.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.result()).isEqualTo(ReviewVerdict.APPROVE);
        }

        @Test
        void shouldHandleVerdictWithLeadingWhitespace() {
            String text = """


                    REQUEST_CHANGES

                    Need more coverage.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.result()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
        }

        @Test
        void shouldRejectApproveChangesAsInvalidVerdict() {
            String text = """
                    APPROVE_CHANGES

                    Looks fine.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isTrue();
        }

        @Test
        void shouldRejectRequestChangesWithExtraText() {
            String text = """
                    REQUEST_CHANGES please

                    Fix the issues.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewVerdict> result = extractor.extractReviewVerdict(createRun("test_reviewer", output));

            assertThat(result.hasCriticalError()).isTrue();
        }
    }

    @Nested
    class CoderExtraction {

        @Test
        void shouldExtractStandardCoderOutput() {
            String text = """
                    Implementation summary: Updated the handler to validate input

                    Files changed:
                    - src/main/Foo.java
                    - src/main/Bar.java

                    Test command: `mvn test`
                    Test result: All tests passed

                    Commit hash: def5678
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<CoderResult> result = extractor.extractCoderResult(createRun("coder", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().summary()).contains("Updated the handler");
            assertThat(result.result().filesChanged()).hasSize(2);
            assertThat(result.result().testCommand()).contains("mvn test");
            assertThat(result.result().commitHash()).isEqualTo("def5678");
        }

        @Test
        void shouldReturnWarningsForMissingNonCriticalFields() {
            String text = """
                    The implementation is done.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<CoderResult> result = extractor.extractCoderResult(createRun("coder", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.result()).isNotNull();
        }

        @Test
        void shouldExtractFilesFromInlineBackticks() {
            String text = """
                    Implementation summary: Fixed the bug in `src/main/service/Handler.java` and `src/test/HandlerTest.java`.

                    Test command: `mvn test`
                    Test result: All tests passed
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<CoderResult> result = extractor.extractCoderResult(createRun("coder", output));

            assertThat(result.result().filesChanged()).isNotNull();
            assertThat(result.result().filesChanged()).hasSize(2);
        }

        @Test
        void shouldHandleExtraMarkdown() {
            String text = """
                    ## Implementation Summary

                    Fixed the validation issue.

                    ### Files Changed
                    - `src/main/Validator.java`

                    ### Test Results
                    Test command: `mvn test`
                    Pass/fail result: All tests passed
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<CoderResult> result = extractor.extractCoderResult(createRun("coder", output));

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().filesChanged()).contains("src/main/Validator.java");
        }
    }

    @Nested
    class ReviewerExtraction {

        @Test
        void shouldExtractApproveReviewerResult() {
            String text = """
                    APPROVE

                    The implementation looks good. All tests pass.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.APPROVE);
            assertThat(result.result().category()).isNull();
            assertThat(result.result().feedback()).contains("All tests pass");
        }

        @Test
        void shouldClassifyTestIssue() {
            String text = """
                    REQUEST_CHANGES

                    The tests are too weak and don't cover edge cases. Test coverage is insufficient.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
            assertThat(result.result().category()).isEqualTo("test_issue");
        }

        @Test
        void shouldClassifyImplementationIssue() {
            String text = """
                    REQUEST_CHANGES

                    The implementation has a bug in the error handling logic.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
            assertThat(result.result().category()).isEqualTo("implementation_issue");
        }

        @Test
        void shouldClassifyUnclearWhenNoKeywords() {
            String text = """
                    REQUEST_CHANGES

                    Needs improvement.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.result().category()).isEqualTo("implementation_issue");
        }

        @Test
        void shouldReturnCriticalErrorWhenVerdictMissing() {
            String text = """
                    The code looks fine.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.hasCriticalError()).isTrue();
        }

        @Test
        void shouldUseFirstLineVerdictOnly() {
            String text = """
                    REQUEST_CHANGES

                    The implementation is broken. But wait, maybe APPROVE is better since tests pass.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
        }

        @Test
        void shouldClassifyAssertionIssueAsTestIssue() {
            String text = """
                    REQUEST_CHANGES

                    The assertions in the test are wrong. The fixture needs to be fixed.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.result().category()).isEqualTo("test_issue");
        }

        @Test
        void shouldPreserveRawOutputForReviewer() {
            String raw = wrapInNdjson("APPROVE\nLooks good.");
            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", raw), "reviewer-1");

            assertThat(result.rawOutput()).isEqualTo(raw);
        }

        @Test
        void shouldHandleBlankFeedbackWithWarning() {
            String text = "REQUEST_CHANGES";
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.hasCriticalError()).isFalse();
            assertThat(result.result().verdict()).isEqualTo(ReviewVerdict.REQUEST_CHANGES);
            assertThat(result.result().feedback()).isEqualTo("(no feedback provided)");
            assertThat(result.result().category()).isEqualTo("unclear");
            assertThat(result.hasWarnings()).isTrue();
        }

        @Test
        void shouldRejectApproveChangesAsInvalidVerdict() {
            String text = """
                    APPROVE_CHANGES

                    Looks fine.
                    """;
            String output = wrapInNdjson(text);

            ExtractionResult<ReviewerResult> result = extractor.extractReviewerResult(
                    createRun("reviewer", output), "reviewer-1");

            assertThat(result.hasCriticalError()).isTrue();
        }
    }
}
