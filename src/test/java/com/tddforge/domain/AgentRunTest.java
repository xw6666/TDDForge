package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;

class AgentRunTest {

    @Test
    void shouldCreateWithAllFields() {
        var now = Instant.now();
        var run = new AgentRun("run-1", "task-1", "planner", "gpt-4",
                "v1", "agent1", "prompt text", "output text", 0,
                1000L, "sess-1", 0, now);

        assertEquals("run-1", run.id());
        assertEquals("task-1", run.taskId());
        assertEquals("planner", run.agentType());
        assertEquals("gpt-4", run.model());
        assertEquals("v1", run.variant());
        assertEquals("agent1", run.agent());
        assertEquals("prompt text", run.prompt());
        assertEquals("output text", run.output());
        assertEquals(0, run.exitCode());
        assertEquals(1000L, run.durationMs());
        assertEquals("sess-1", run.sessionId());
        assertEquals(0, run.continueCount());
        assertEquals(now, run.createdAt());
    }

    @Test
    void shouldRejectNullId() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun(null, "task-1", "planner", "gpt-4",
                        null, null, "prompt", "output", 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullTaskId() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", null, "planner", "gpt-4",
                        null, null, "prompt", "output", 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullAgentType() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", null, "gpt-4",
                        null, null, "prompt", "output", 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullModel() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", "planner", null,
                        null, null, "prompt", "output", 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullPrompt() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", "planner", "gpt-4",
                        null, null, null, "output", 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullOutput() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", "planner", "gpt-4",
                        null, null, "prompt", null, 0, 0, null, 0, now));
    }

    @Test
    void shouldRejectNullCreatedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", "planner", "gpt-4",
                        null, null, "prompt", "output", 0, 0, null, 0, null));
    }

    @Test
    void shouldRejectNegativeContinueCount() {
        var now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRun("run-1", "task-1", "planner", "gpt-4",
                        null, null, "prompt", "output", 0, 0, null, -1, now));
    }

    @Test
    void shouldAcceptEmptyVariantAndAgent() {
        var now = Instant.now();
        var run = new AgentRun("run-1", "task-1", "planner", "gpt-4",
                "", "", "prompt", "output", 0, 0, null, 0, now);
        assertEquals("", run.variant());
        assertEquals("", run.agent());
    }

    @Test
    void shouldAcceptNullSessionId() {
        var now = Instant.now();
        var run = new AgentRun("run-1", "task-1", "planner", "gpt-4",
                null, null, "prompt", "output", 0, 0, null, 0, now);
        assertNull(run.sessionId());
    }

    @Test
    void shouldAcceptNonZeroExitCode() {
        var now = Instant.now();
        var run = new AgentRun("run-1", "task-1", "planner", "gpt-4",
                null, null, "prompt", "output", 1, 500L, null, 2, now);
        assertEquals(1, run.exitCode());
        assertEquals(500L, run.durationMs());
        assertEquals(2, run.continueCount());
    }
}
