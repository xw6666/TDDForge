package com.tddforge.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.Map;

class PromptTemplateRegistryTest {

    private final PromptTemplateRegistry registry = new PromptTemplateRegistry();

    @Test
    void shouldRenderSystemCommon() {
        String result = registry.getRaw(PromptTemplateRegistry.Template.SYSTEM_COMMON);
        assertTrue(result.contains("isolated git worktree"));
        assertTrue(result.contains("Global rules"));
        assertTrue(result.contains("AGENTS.md"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderPlannerAnalyzeSplit() {
        Map<String, String> vars = Map.of(
                "title", "Fix bug",
                "description", "Fix the NPE",
                "repo_path", "/repo"
        );
        String result = registry.render(PromptTemplateRegistry.Template.PLANNER_ANALYZE_SPLIT, vars);
        assertTrue(result.contains("planning agent"));
        assertTrue(result.contains("Fix bug"));
        assertTrue(result.contains("Fix the NPE"));
        assertTrue(result.contains("/repo"));
        assertTrue(result.contains("complexity"));
        assertTrue(result.contains("split"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderPlannerNoSplit() {
        Map<String, String> vars = Map.of(
                "title", "Simple fix",
                "description", "Fix typo",
                "repo_path", "/repo"
        );
        String result = registry.render(PromptTemplateRegistry.Template.PLANNER_NO_SPLIT, vars);
        assertTrue(result.contains("Splitting is forbidden"));
        assertTrue(result.contains("Simple fix"));
        assertTrue(result.contains("Fix typo"));
        assertTrue(result.contains("/repo"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderTestWriter() {
        Map<String, String> vars = Map.of(
                "title", "Add login",
                "description", "Add user login",
                "repo_path", "/repo",
                "plan_output", "Write login tests"
        );
        String result = registry.render(PromptTemplateRegistry.Template.TEST_WRITER, vars);
        assertTrue(result.contains("test-writing agent"));
        assertTrue(result.contains("PASS"));
        assertTrue(result.contains("EXPECTED_RED"));
        assertTrue(result.contains("INVALID"));
        assertTrue(result.contains("Add login"));
        assertTrue(result.contains("Write login tests"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderTestWriterRetry() {
        Map<String, String> vars = Map.of(
                "attempt", "2",
                "test_phase_feedback", "Tests were invalid"
        );
        String result = registry.render(PromptTemplateRegistry.Template.TEST_WRITER_RETRY, vars);
        assertTrue(result.contains("Test Phase Feedback"));
        assertTrue(result.contains("attempt 2"));
        assertTrue(result.contains("Tests were invalid"));
        assertTrue(result.contains("PASS / EXPECTED_RED / INVALID"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderTestReviewer() {
        Map<String, String> vars = Map.of(
                "title", "Add login",
                "description", "Add user login",
                "plan_output", "Write login tests",
                "test_writer_response", "Tests written"
        );
        String result = registry.render(PromptTemplateRegistry.Template.TEST_REVIEWER, vars);
        assertTrue(result.contains("test review agent"));
        assertTrue(result.contains("APPROVE"));
        assertTrue(result.contains("REQUEST_CHANGES"));
        assertTrue(result.contains("Add login"));
        assertTrue(result.contains("Tests written"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderCoderImplement() {
        Map<String, String> vars = Map.of(
                "title", "Implement login",
                "description", "Implement user login",
                "test_output", "Test output",
                "test_review_output", "Approved",
                "dependency_context", "",
                "file_path", "src/Login.java",
                "line_number", "42",
                "plan_output", "Implement login"
        );
        String result = registry.render(PromptTemplateRegistry.Template.CODER_IMPLEMENT, vars);
        assertTrue(result.contains("coding agent"));
        assertTrue(result.contains("Implement login"));
        assertTrue(result.contains("Test output"));
        assertTrue(result.contains("Approved"));
        assertTrue(result.contains("src/Login.java"));
        assertTrue(result.contains("42"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderCoderRetry() {
        Map<String, String> vars = Map.of(
                "attempt", "1",
                "review_feedback", "Fix the implementation"
        );
        String result = registry.render(PromptTemplateRegistry.Template.CODER_RETRY, vars);
        assertTrue(result.contains("Review Feedback"));
        assertTrue(result.contains("attempt 1"));
        assertTrue(result.contains("Fix the implementation"));
        assertTrue(result.contains("AGENTS.md"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderCoderTestFailureRetry() {
        Map<String, String> vars = Map.of(
                "attempt", "1",
                "test_command", "mvn test",
                "test_output", "Tests failed: 1"
        );
        String result = registry.render(PromptTemplateRegistry.Template.CODER_TEST_FAILURE_RETRY, vars);
        assertTrue(result.contains("Test Failure Feedback"));
        assertTrue(result.contains("attempt 1"));
        assertTrue(result.contains("mvn test"));
        assertTrue(result.contains("Tests failed: 1"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderReviewerReview() {
        Map<String, String> vars = Map.of(
                "title", "Implement login",
                "description", "Implement user login",
                "test_output", "Test output",
                "test_review_output", "Approved",
                "coder_response", "Implemented",
                "prior_rejections", ""
        );
        String result = registry.render(PromptTemplateRegistry.Template.REVIEWER_REVIEW, vars);
        assertTrue(result.contains("code review agent"));
        assertTrue(result.contains("APPROVE"));
        assertTrue(result.contains("REQUEST_CHANGES"));
        assertTrue(result.contains("Implemented"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderReviewerPatch() {
        Map<String, String> vars = Map.of(
                "title", "Review PR #42",
                "revision_context", "Check the diff",
                "prior_rejections", "",
                "review_input", "Patch content"
        );
        String result = registry.render(PromptTemplateRegistry.Template.REVIEWER_PATCH, vars);
        assertTrue(result.contains("code review agent"));
        assertTrue(result.contains("Review PR #42"));
        assertTrue(result.contains("Check the diff"));
        assertTrue(result.contains("Patch content"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderBranchSlug() {
        Map<String, String> vars = Map.of("title", "Fix Null Pointer Exception");
        String result = registry.render(PromptTemplateRegistry.Template.BRANCH_SLUG, vars);
        assertTrue(result.contains("branch name slug"));
        assertTrue(result.contains("Fix Null Pointer Exception"));
        assertTrue(result.contains("Reply with ONLY the slug"));
        assertFalse(result.contains("{{"));
    }

    @Test
    void shouldRenderContinueWithoutVariables() {
        String result = registry.getRaw(PromptTemplateRegistry.Template.CONTINUE);
        assertEquals("Continue", result);
    }

    @Test
    void shouldRenderContinueWithVariablesMap() {
        String result = registry.render(PromptTemplateRegistry.Template.CONTINUE, Map.of());
        assertEquals("Continue", result);
    }

    @Test
    void shouldRenderContinueWithNullVariables() {
        String result = registry.render(PromptTemplateRegistry.Template.CONTINUE, null);
        assertEquals("Continue", result);
    }

    @Test
    void shouldThrowOnMissingVariable() {
        Map<String, String> vars = Map.of("title", "Fix bug");
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.PLANNER_ANALYZE_SPLIT, vars));
        assertTrue(ex.getMessage().contains("PLANNER_ANALYZE_SPLIT"));
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void shouldThrowOnMissingTitleInBranchSlug() {
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.BRANCH_SLUG, Map.of()));
        assertTrue(ex.getMessage().contains("BRANCH_SLUG"));
        assertTrue(ex.getMessage().contains("title"));
    }

    @Test
    void shouldThrowOnMissingTestWriterVariable() {
        Map<String, String> vars = Map.of(
                "title", "Test",
                "repo_path", "/repo",
                "plan_output", "plan"
        );
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.TEST_WRITER, vars));
        assertTrue(ex.getMessage().contains("TEST_WRITER"));
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void shouldThrowOnMissingCoderImplementVariable() {
        Map<String, String> vars = Map.of(
                "title", "Impl",
                "description", "Desc",
                "test_output", "out",
                "test_review_output", "review",
                "dependency_context", "",
                "plan_output", "plan"
        );
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.CODER_IMPLEMENT, vars));
        assertTrue(ex.getMessage().contains("CODER_IMPLEMENT"));
        assertTrue(ex.getMessage().contains("file_path"));
    }

    @Test
    void shouldThrowOnMissingTestWriterRetryVariable() {
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.TEST_WRITER_RETRY, Map.of("attempt", "1")));
        assertTrue(ex.getMessage().contains("TEST_WRITER_RETRY"));
        assertTrue(ex.getMessage().contains("test_phase_feedback"));
    }

    @Test
    void shouldThrowOnMissingReviewerReviewVariable() {
        Map<String, String> vars = Map.of(
                "title", "T",
                "description", "D",
                "test_output", "TO",
                "test_review_output", "TRO"
        );
        var ex = assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.REVIEWER_REVIEW, vars));
        assertTrue(ex.getMessage().contains("REVIEWER_REVIEW"));
        assertTrue(ex.getMessage().contains("coder_response"));
    }

    @Test
    void shouldPreserveNewlinesInRenderedOutput() {
        Map<String, String> vars = Map.of(
                "title", "Fix NPE",
                "description", "Fix null pointer",
                "repo_path", "/repo"
        );
        String result = registry.render(PromptTemplateRegistry.Template.PLANNER_ANALYZE_SPLIT, vars);
        assertTrue(result.contains("\n"));
        assertTrue(result.startsWith("You are a planning agent"));
    }

    @Test
    void shouldMakeAllTemplatesAccessible() {
        for (var template : PromptTemplateRegistry.Template.values()) {
            String raw = registry.getRaw(template);
            assertNotNull(raw);
            assertFalse(raw.isBlank(), "Template " + template + " should not be blank");
        }
    }

    @Test
    void shouldNotContainUnresolvedPlaceholdersInContinue() {
        String result = registry.render(PromptTemplateRegistry.Template.CONTINUE, null);
        assertEquals("Continue", result);
    }

    @Test
    void shouldThrowOnNullVariablesForTemplateWithPlaceholders() {
        assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.BRANCH_SLUG, null));
    }

    @Test
    void shouldThrowOnNullVariablesForTestWriterRetry() {
        assertThrows(PromptTemplateRegistry.MissingVariableException.class,
                () -> registry.render(PromptTemplateRegistry.Template.TEST_WRITER_RETRY, null));
    }
}
