package com.tddforge.persistence;

import com.tddforge.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AgentRunRepository using H2 in MySQL compatibility mode.
 *
 * NOTE: Docker is not available in this CI environment, so H2 with MODE=MySQL
 * is used as the test substitute for Testcontainers MySQL. The H2 migration
 * (src/test/resources/db/h2-migration/V1__init_schema.sql) mirrors the MySQL
 * migration. JSON columns are supported via H2's JSON type. LONGTEXT is
 * mapped to CLOB for H2 compatibility.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("persistence-test")
class AgentRunRepositoryTest {

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private TaskEntity createTask(String id) {
        Instant now = Instant.now();
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setTitle("Test Task");
        task.setDescription("Test Desc");
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setSource(TaskSource.MANUAL);
        task.setRepoPath("/repo");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return taskRepository.save(task);
    }

    @Test
    void shouldSaveAndFindAgentRunById() {
        createTask("task-ar1");
        Instant now = Instant.now();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-001");
        entity.setTaskId("task-ar1");
        entity.setAgentType("planner");
        entity.setModel("gpt-4");
        entity.setPrompt("Analyze this task");
        entity.setOutput("Analysis complete");
        entity.setExitCode(0);
        entity.setDurationMs(5000L);
        entity.setCreatedAt(now);

        agentRunRepository.save(entity);
        agentRunRepository.flush();

        var found = agentRunRepository.findById("run-001");
        assertTrue(found.isPresent());
        assertEquals("planner", found.get().getAgentType());
        assertEquals("gpt-4", found.get().getModel());
        assertEquals(0, found.get().getExitCode());
    }

    @Test
    void shouldFindByTaskId() {
        createTask("task-ar2");
        Instant now = Instant.now();

        AgentRunEntity run1 = new AgentRunEntity();
        run1.setId("run-t1");
        run1.setTaskId("task-ar2");
        run1.setAgentType("planner");
        run1.setModel("gpt-4");
        run1.setPrompt("prompt 1");
        run1.setOutput("output 1");
        run1.setExitCode(0);
        run1.setDurationMs(1000L);
        run1.setCreatedAt(now);
        agentRunRepository.save(run1);

        AgentRunEntity run2 = new AgentRunEntity();
        run2.setId("run-t2");
        run2.setTaskId("task-ar2");
        run2.setAgentType("coder");
        run2.setModel("gpt-4");
        run2.setPrompt("prompt 2");
        run2.setOutput("output 2");
        run2.setExitCode(0);
        run2.setDurationMs(2000L);
        run2.setCreatedAt(now.plusSeconds(10));
        agentRunRepository.save(run2);

        agentRunRepository.flush();

        var runs = agentRunRepository.findByTaskId("task-ar2");
        assertEquals(2, runs.size());
        assertTrue(runs.stream().anyMatch(r -> "planner".equals(r.getAgentType())));
        assertTrue(runs.stream().anyMatch(r -> "coder".equals(r.getAgentType())));
    }

    @Test
    void shouldFindByAgentType() {
        createTask("task-ar3");
        Instant now = Instant.now();

        AgentRunEntity run = new AgentRunEntity();
        run.setId("run-at1");
        run.setTaskId("task-ar3");
        run.setAgentType("test_writer");
        run.setModel("gpt-4");
        run.setPrompt("write tests");
        run.setOutput("tests written");
        run.setExitCode(0);
        run.setDurationMs(3000L);
        run.setCreatedAt(now);
        agentRunRepository.save(run);

        agentRunRepository.flush();

        var runs = agentRunRepository.findByAgentType("test_writer");
        assertEquals(1, runs.size());
        assertEquals("test_writer", runs.get(0).getAgentType());
    }

    @Test
    void shouldUpdateAgentRun() {
        createTask("task-ar4");
        Instant now = Instant.now();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-upd");
        entity.setTaskId("task-ar4");
        entity.setAgentType("coder");
        entity.setModel("gpt-4");
        entity.setPrompt("initial prompt");
        entity.setOutput("initial output");
        entity.setExitCode(0);
        entity.setDurationMs(1000L);
        entity.setCreatedAt(now);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        entity.setOutput("updated output with continue");
        entity.setContinueCount(1);
        entity.setDurationMs(5000L);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        var found = agentRunRepository.findById("run-upd").orElseThrow();
        assertEquals("updated output with continue", found.getOutput());
        assertEquals(1, found.getContinueCount());
        assertEquals(5000L, found.getDurationMs());
    }

    @Test
    void shouldDeleteAgentRun() {
        createTask("task-ar5");
        Instant now = Instant.now();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-del");
        entity.setTaskId("task-ar5");
        entity.setAgentType("reviewer");
        entity.setModel("gpt-4");
        entity.setPrompt("review");
        entity.setOutput("approved");
        entity.setExitCode(0);
        entity.setDurationMs(2000L);
        entity.setCreatedAt(now);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        assertTrue(agentRunRepository.findById("run-del").isPresent());

        agentRunRepository.deleteById("run-del");
        agentRunRepository.flush();

        assertFalse(agentRunRepository.findById("run-del").isPresent());
    }

    @Test
    void shouldSaveLongPromptAndOutput() {
        createTask("task-ar6");
        Instant now = Instant.now();
        String longPrompt = "P".repeat(100_000);
        String longOutput = "O".repeat(200_000);

        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-long");
        entity.setTaskId("task-ar6");
        entity.setAgentType("coder");
        entity.setModel("gpt-4");
        entity.setPrompt(longPrompt);
        entity.setOutput(longOutput);
        entity.setExitCode(0);
        entity.setDurationMs(60000L);
        entity.setCreatedAt(now);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        entityManager.clear();
        var found = agentRunRepository.findById("run-long").orElseThrow();

        assertEquals(longPrompt.length(), found.getPrompt().length());
        assertEquals(longOutput.length(), found.getOutput().length());
        assertEquals(longPrompt, found.getPrompt());
        assertEquals(longOutput, found.getOutput());
    }

    @Test
    void shouldSaveAgentRunWithSessionId() {
        createTask("task-ar7");
        Instant now = Instant.now();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-sess");
        entity.setTaskId("task-ar7");
        entity.setAgentType("coder");
        entity.setModel("gpt-4");
        entity.setVariant("v1");
        entity.setAgent("coder-agent");
        entity.setPrompt("implement feature");
        entity.setOutput("done");
        entity.setExitCode(0);
        entity.setDurationMs(5000L);
        entity.setSessionId("sess-abc-123");
        entity.setContinueCount(2);
        entity.setCreatedAt(now);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        var found = agentRunRepository.findById("run-sess").orElseThrow();
        assertEquals("sess-abc-123", found.getSessionId());
        assertEquals(2, found.getContinueCount());
        assertEquals("v1", found.getVariant());
        assertEquals("coder-agent", found.getAgent());
    }

    @Test
    void shouldCascadeDeleteAgentRunsWhenTaskDeleted() {
        createTask("task-cascade");
        Instant now = Instant.now();
        AgentRunEntity entity = new AgentRunEntity();
        entity.setId("run-casc");
        entity.setTaskId("task-cascade");
        entity.setAgentType("planner");
        entity.setModel("gpt-4");
        entity.setPrompt("prompt");
        entity.setOutput("output");
        entity.setExitCode(0);
        entity.setDurationMs(1000L);
        entity.setCreatedAt(now);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        taskRepository.deleteById("task-cascade");
        entityManager.flush();
        entityManager.clear();

        assertFalse(agentRunRepository.findById("run-casc").isPresent());
    }

    @Test
    void shouldRoundTripAgentRunDomainConversion() {
        createTask("task-rt-ar");
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        var domainRun = new AgentRun(
                "run-rt", "task-rt-ar", "test_writer", "gpt-4",
                "v1", "writer-agent", "write tests for X", "tests written",
                0, 12000L, "sess-456", 1, now
        );

        AgentRunEntity entity = AgentRunEntity.fromDomain(domainRun);
        agentRunRepository.save(entity);
        agentRunRepository.flush();

        entityManager.clear();
        var found = agentRunRepository.findById("run-rt").orElseThrow();
        var restored = found.toDomain();

        assertEquals(domainRun.id(), restored.id());
        assertEquals(domainRun.taskId(), restored.taskId());
        assertEquals(domainRun.agentType(), restored.agentType());
        assertEquals(domainRun.model(), restored.model());
        assertEquals(domainRun.variant(), restored.variant());
        assertEquals(domainRun.agent(), restored.agent());
        assertEquals(domainRun.prompt(), restored.prompt());
        assertEquals(domainRun.output(), restored.output());
        assertEquals(domainRun.exitCode(), restored.exitCode());
        assertEquals(domainRun.durationMs(), restored.durationMs());
        assertEquals(domainRun.sessionId(), restored.sessionId());
        assertEquals(domainRun.continueCount(), restored.continueCount());
        assertEquals(domainRun.createdAt(), restored.createdAt());
    }
}
