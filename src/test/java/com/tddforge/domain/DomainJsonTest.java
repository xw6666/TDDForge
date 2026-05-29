package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class DomainJsonTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // --- TaskStatus JSON ---

    @Test
    void shouldSerializeTaskStatus() throws Exception {
        assertEquals("\"PENDING\"", mapper.writeValueAsString(TaskStatus.PENDING));
    }

    @Test
    void shouldDeserializeTaskStatus() throws Exception {
        assertEquals(TaskStatus.PENDING, mapper.readValue("\"PENDING\"", TaskStatus.class));
        assertEquals(TaskStatus.NEEDS_ARBITRATION,
                mapper.readValue("\"NEEDS_ARBITRATION\"", TaskStatus.class));
    }

    @Test
    void shouldFailOnInvalidTaskStatusJson() {
        var ex = assertThrows(ValueInstantiationException.class,
                () -> mapper.readValue("\"INVALID\"", TaskStatus.class));
        assertTrue(ex.getMessage().contains("Invalid TaskStatus"));
    }

    // --- TaskPriority JSON ---

    @Test
    void shouldSerializeTaskPriority() throws Exception {
        assertEquals("\"MEDIUM\"", mapper.writeValueAsString(TaskPriority.MEDIUM));
    }

    @Test
    void shouldDeserializeTaskPriority() throws Exception {
        assertEquals(TaskPriority.HIGH, mapper.readValue("\"HIGH\"", TaskPriority.class));
    }

    @Test
    void shouldFailOnInvalidTaskPriorityJson() {
        assertThrows(ValueInstantiationException.class,
                () -> mapper.readValue("\"BAD\"", TaskPriority.class));
    }

    // --- TaskSource JSON ---

    @Test
    void shouldSerializeTaskSource() throws Exception {
        assertEquals("\"MANUAL\"", mapper.writeValueAsString(TaskSource.MANUAL));
    }

    @Test
    void shouldDeserializeTaskSource() throws Exception {
        assertEquals(TaskSource.CLI, mapper.readValue("\"CLI\"", TaskSource.class));
    }

    // --- ReviewVerdict JSON ---

    @Test
    void shouldSerializeReviewVerdict() throws Exception {
        assertEquals("\"APPROVE\"", mapper.writeValueAsString(ReviewVerdict.APPROVE));
    }

    @Test
    void shouldDeserializeReviewVerdict() throws Exception {
        assertEquals(ReviewVerdict.REQUEST_CHANGES,
                mapper.readValue("\"REQUEST_CHANGES\"", ReviewVerdict.class));
    }

    // --- ModelSpec JSON ---

    @Test
    void shouldSerializeModelSpec() throws Exception {
        var spec = new ModelSpec("gpt-4", null, null);
        var json = mapper.writeValueAsString(spec);
        assertTrue(json.contains("gpt-4"));
        assertFalse(json.contains("variant"));
    }

    @Test
    void shouldDeserializeModelSpec() throws Exception {
        var json = "{\"model\":\"gpt-4\"}";
        var spec = mapper.readValue(json, ModelSpec.class);
        assertEquals("gpt-4", spec.model());
        assertNull(spec.variant());
    }

    @Test
    void shouldDeserializeModelSpecWithAllFields() throws Exception {
        var json = "{\"model\":\"gpt-4\",\"variant\":\"v1\",\"agent\":\"coder\"}";
        var spec = mapper.readValue(json, ModelSpec.class);
        assertEquals("gpt-4", spec.model());
        assertEquals("v1", spec.variant());
        assertEquals("coder", spec.agent());
    }

    // --- ReviewerResult JSON ---

    @Test
    void shouldSerializeReviewerResult() throws Exception {
        var result = new ReviewerResult("r1", ReviewVerdict.APPROVE, "good", null);
        var json = mapper.writeValueAsString(result);
        assertTrue(json.contains("r1"));
        assertTrue(json.contains("APPROVE"));
        assertTrue(json.contains("good"));
    }

    @Test
    void shouldDeserializeReviewerResult() throws Exception {
        var json = "{\"reviewerId\":\"r1\",\"verdict\":\"APPROVE\",\"feedback\":\"good\"}";
        var result = mapper.readValue(json, ReviewerResult.class);
        assertEquals("r1", result.reviewerId());
        assertEquals(ReviewVerdict.APPROVE, result.verdict());
        assertEquals("good", result.feedback());
    }

    // --- AgentRun JSON ---

    @Test
    void shouldSerializeAndDeserializeAgentRun() throws Exception {
        var now = Instant.parse("2025-01-01T00:00:00Z");
        var run = new AgentRun("run-1", "task-1", "planner", "gpt-4",
                "v1", null, "prompt", "output", 0, 100L, "sess-1", 0, now);

        var json = mapper.writeValueAsString(run);
        var deserialized = mapper.readValue(json, AgentRun.class);

        assertEquals(run.id(), deserialized.id());
        assertEquals(run.taskId(), deserialized.taskId());
        assertEquals(run.agentType(), deserialized.agentType());
        assertEquals(run.model(), deserialized.model());
        assertEquals(run.prompt(), deserialized.prompt());
        assertEquals(run.output(), deserialized.output());
        assertEquals(run.exitCode(), deserialized.exitCode());
        assertEquals(run.durationMs(), deserialized.durationMs());
        assertEquals(run.sessionId(), deserialized.sessionId());
        assertEquals(run.continueCount(), deserialized.continueCount());
        assertEquals(run.createdAt(), deserialized.createdAt());
    }

    // --- Task JSON (sessionIds, reviewerResults, dependsOn) ---

    @Test
    void shouldSerializeTaskWithCollectionFields() throws Exception {
        var now = Instant.parse("2025-01-01T00:00:00Z");
        var result = new ReviewerResult("r1", ReviewVerdict.APPROVE, "good", null);
        var task = new Task("t1", "title", "desc", TaskStatus.PENDING,
                TaskPriority.MEDIUM, TaskSource.MANUAL, "develop", null,
                List.of("dep-1"), false, "/repo", "branch", "/worktree",
                "", null, null, null, null, null,
                false, List.of(result), List.of("sess-1"),
                0, 0, 0, 2, 4, null, null,
                now, now, null, null, null);

        var json = mapper.writeValueAsString(task);
        assertTrue(json.contains("t1"));
        assertTrue(json.contains("\"dep-1\""));
        assertTrue(json.contains("\"sess-1\""));
        assertTrue(json.contains("\"r1\""));
        assertTrue(json.contains("\"APPROVE\""));
        assertTrue(json.contains("\"" + now + "\""));
    }

    @Test
    void shouldDeserializeTaskWithCollectionFields() throws Exception {
        var json = """
                {
                    "id": "t1",
                    "title": "Test Task",
                    "description": "A test",
                    "status": "PLANNING",
                    "priority": "HIGH",
                    "source": "MANUAL",
                    "taskMode": "develop",
                    "repoPath": "/repo",
                    "dependsOn": ["dep-1", "dep-2"],
                    "sessionIds": ["sess-1"],
                    "reviewerResults": [{"reviewerId": "r1", "verdict": "REQUEST_CHANGES", "feedback": "fix it"}],
                    "testRetryCount": 1,
                    "codeRetryCount": 2,
                    "createdAt": "2025-01-01T00:00:00Z",
                    "updatedAt": "2025-01-01T00:00:00Z"
                }
                """;

        var task = mapper.readValue(json, Task.class);
        assertEquals("t1", task.getId());
        assertEquals("Test Task", task.getTitle());
        assertEquals(TaskStatus.PLANNING, task.getStatus());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(2, task.getDependsOn().size());
        assertTrue(task.getDependsOn().contains("dep-1"));
        assertEquals(1, task.getSessionIds().size());
        assertEquals("sess-1", task.getSessionIds().get(0));
        assertEquals(1, task.getReviewerResults().size());
        assertEquals("r1", task.getReviewerResults().get(0).reviewerId());
        assertEquals(ReviewVerdict.REQUEST_CHANGES, task.getReviewerResults().get(0).verdict());
        assertEquals(1, task.getTestRetryCount());
        assertEquals(2, task.getCodeRetryCount());
    }

    @Test
    void shouldRoundTripTaskWithAllFields() throws Exception {
        var now = Instant.parse("2025-01-01T00:00:00Z");
        var result = new ReviewerResult("r1", ReviewVerdict.APPROVE, "good", "implementation_issue");
        var task = new Task("t1", "title", "desc", TaskStatus.COMPLETED,
                TaskPriority.CRITICAL, TaskSource.CLI, "develop", "parent-1",
                List.of("dep-1", "dep-2"), true, "/repo", "feature/branch", "/wt",
                "complex", "plan out", "test out", "treview out", "code out", "review out",
                true, List.of(result), List.of("sess-1", "sess-2"),
                3, 2, 4, 5, 10, "feedback", "error",
                now, now, now, now, now);

        var json = mapper.writeValueAsString(task);
        var restored = mapper.readValue(json, Task.class);

        assertEquals(task.getId(), restored.getId());
        assertEquals(task.getTitle(), restored.getTitle());
        assertEquals(task.getDescription(), restored.getDescription());
        assertEquals(task.getStatus(), restored.getStatus());
        assertEquals(task.getPriority(), restored.getPriority());
        assertEquals(task.getSource(), restored.getSource());
        assertEquals(task.getTaskMode(), restored.getTaskMode());
        assertEquals(task.getParentId(), restored.getParentId());
        assertEquals(task.getDependsOn(), restored.getDependsOn());
        assertEquals(task.isForceNoSplit(), restored.isForceNoSplit());
        assertEquals(task.getRepoPath(), restored.getRepoPath());
        assertEquals(task.getBranchName(), restored.getBranchName());
        assertEquals(task.getWorktreePath(), restored.getWorktreePath());
        assertEquals(task.getComplexity(), restored.getComplexity());
        assertEquals(task.getPlanOutput(), restored.getPlanOutput());
        assertEquals(task.getTestOutput(), restored.getTestOutput());
        assertEquals(task.getTestReviewOutput(), restored.getTestReviewOutput());
        assertEquals(task.getCodeOutput(), restored.getCodeOutput());
        assertEquals(task.getReviewOutput(), restored.getReviewOutput());
        assertEquals(task.isReviewPass(), restored.isReviewPass());
        assertEquals(task.getSessionIds(), restored.getSessionIds());
        assertEquals(task.getRetryCount(), restored.getRetryCount());
        assertEquals(task.getTestRetryCount(), restored.getTestRetryCount());
        assertEquals(task.getCodeRetryCount(), restored.getCodeRetryCount());
        assertEquals(task.getMaxTestRetries(), restored.getMaxTestRetries());
        assertEquals(task.getMaxCodeRetries(), restored.getMaxCodeRetries());
        assertEquals(task.getUserFeedback(), restored.getUserFeedback());
        assertEquals(task.getError(), restored.getError());

        assertEquals(1, restored.getReviewerResults().size());
        assertEquals("r1", restored.getReviewerResults().get(0).reviewerId());
        assertEquals(ReviewVerdict.APPROVE, restored.getReviewerResults().get(0).verdict());
    }

    // --- PlannerResult JSON ---

    @Test
    void shouldSerializeAndDeserializePlannerResult() throws Exception {
        var subTask = new PlannerResult.SubTask("Sub 1", "Desc", "high", List.of(0));
        var result = new PlannerResult("medium", false, "simple task", "plan details", null);
        var json = mapper.writeValueAsString(result);
        var restored = mapper.readValue(json, PlannerResult.class);
        assertEquals("medium", restored.complexity());
        assertFalse(restored.split());
        assertEquals("simple task", restored.reason());
        assertEquals("plan details", restored.plan());
        assertNull(restored.subTasks());
    }

    @Test
    void shouldSerializePlannerResultWithSubTasks() throws Exception {
        var subTask = new PlannerResult.SubTask("Sub 1", "Desc", "high", List.of(0));
        var result = new PlannerResult("complex", true, "needs split", null, List.of(subTask));
        var json = mapper.writeValueAsString(result);
        assertTrue(json.contains("Sub 1"));
        assertTrue(json.contains("complex"));
        assertTrue(json.contains("true"));
    }

    // --- TestWriterResult JSON ---

    @Test
    void shouldSerializeAndDeserializeTestWriterResult() throws Exception {
        var result = new TestWriterResult("wrote tests", "mvn test",
                "EXPECTED_RED", "abc123", List.of("Test1.java", "Test2.java"));
        var json = mapper.writeValueAsString(result);
        var restored = mapper.readValue(json, TestWriterResult.class);
        assertEquals("wrote tests", restored.summary());
        assertEquals("mvn test", restored.testCommand());
        assertEquals("EXPECTED_RED", restored.resultClassification());
        assertEquals("abc123", restored.commitHash());
        assertEquals(2, restored.filesChanged().size());
    }

    // --- CoderResult JSON ---

    @Test
    void shouldSerializeAndDeserializeCoderResult() throws Exception {
        var result = new CoderResult("implemented", "mvn test", "PASS", "def456", null);
        var json = mapper.writeValueAsString(result);
        var restored = mapper.readValue(json, CoderResult.class);
        assertEquals("implemented", restored.summary());
        assertEquals("mvn test", restored.testCommand());
        assertEquals("PASS", restored.testResult());
        assertEquals("def456", restored.commitHash());
        assertNull(restored.filesChanged());
    }

    // --- OpenCodeRequest JSON ---

    @Test
    void shouldSerializeAndDeserializeOpenCodeRequest() throws Exception {
        var request = new OpenCodeRequest("gpt-4",
                java.nio.file.Paths.get("/worktree"), "write tests",
                "sess-1", null, null, null, 3600L);
        var json = mapper.writeValueAsString(request);
        var restored = mapper.readValue(json, OpenCodeRequest.class);
        assertEquals("gpt-4", restored.model());
        assertEquals("/worktree", restored.worktreeDir().toString());
        assertEquals("write tests", restored.prompt());
        assertEquals("sess-1", restored.sessionId());
        assertEquals(3600L, restored.timeoutSeconds());
    }

    // --- OpenCodeEvent JSON ---

    @Test
    void shouldDeserializeOpenCodeEvent() throws Exception {
        var json = """
                {
                    "type": "step_finish",
                    "sessionId": "sess-1",
                    "step_finish": {"reason": "stop"}
                }
                """;
        var event = mapper.readValue(json, OpenCodeEvent.class);
        assertEquals("step_finish", event.type());
        assertEquals("sess-1", event.sessionId());
        assertNotNull(event.stepFinish());
        assertEquals("stop", event.stepFinish().reason());
    }

    @Test
    void shouldDeserializeOpenCodeTextEvent() throws Exception {
        var json = """
                {
                    "type": "text",
                    "text": "Hello",
                    "role": "assistant"
                }
                """;
        var event = mapper.readValue(json, OpenCodeEvent.class);
        assertEquals("text", event.type());
        assertEquals("Hello", event.text());
        assertEquals("assistant", event.role());
    }

    @Test
    void shouldDeserializeOpenCodeToolEvent() throws Exception {
        var json = """
                {
                    "type": "tool_use",
                    "tool_name": "read",
                    "tool_input": {"filePath": "src/Main.java"}
                }
                """;
        var event = mapper.readValue(json, OpenCodeEvent.class);
        assertEquals("tool_use", event.type());
        assertEquals("read", event.toolName());
        assertNotNull(event.toolInput());
        assertEquals("src/Main.java", event.toolInput().get("filePath"));
    }

    @Test
    void shouldHandleUnknownFieldsInOpenCodeEvent() throws Exception {
        var json = """
                {
                    "type": "step_start",
                    "unknownField": "should be ignored"
                }
                """;
        var event = mapper.readValue(json, OpenCodeEvent.class);
        assertEquals("step_start", event.type());
    }
}
