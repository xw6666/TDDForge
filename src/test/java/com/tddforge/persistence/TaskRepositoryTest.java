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
 * Integration tests for TaskRepository using H2 in MySQL compatibility mode.
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
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndFindTaskById() {
        Instant now = Instant.now();
        TaskEntity entity = new TaskEntity();
        entity.setId("task-001");
        entity.setTitle("Fix cache bug");
        entity.setDescription("When user permissions change, cached access decisions should be invalidated.");
        entity.setStatus(TaskStatus.PENDING);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setTaskMode("develop");
        entity.setRepoPath("/data/repos/target-project");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        taskRepository.save(entity);
        taskRepository.flush();

        var found = taskRepository.findById("task-001");
        assertTrue(found.isPresent());
        assertEquals("Fix cache bug", found.get().getTitle());
        assertEquals(TaskStatus.PENDING, found.get().getStatus());
    }

    @Test
    void shouldSaveAndFindByStatus() {
        Instant now = Instant.now();
        TaskEntity e1 = new TaskEntity();
        e1.setId("task-s1");
        e1.setTitle("Task S1");
        e1.setDescription("Desc S1");
        e1.setStatus(TaskStatus.PLANNING);
        e1.setPriority(TaskPriority.HIGH);
        e1.setSource(TaskSource.MANUAL);
        e1.setRepoPath("/repo");
        e1.setCreatedAt(now);
        e1.setUpdatedAt(now);
        taskRepository.save(e1);

        TaskEntity e2 = new TaskEntity();
        e2.setId("task-s2");
        e2.setTitle("Task S2");
        e2.setDescription("Desc S2");
        e2.setStatus(TaskStatus.PLANNING);
        e2.setPriority(TaskPriority.LOW);
        e2.setSource(TaskSource.CLI);
        e2.setRepoPath("/repo");
        e2.setCreatedAt(now);
        e2.setUpdatedAt(now);
        taskRepository.save(e2);

        TaskEntity e3 = new TaskEntity();
        e3.setId("task-s3");
        e3.setTitle("Task S3");
        e3.setDescription("Desc S3");
        e3.setStatus(TaskStatus.COMPLETED);
        e3.setPriority(TaskPriority.MEDIUM);
        e3.setSource(TaskSource.MANUAL);
        e3.setRepoPath("/repo");
        e3.setCreatedAt(now);
        e3.setUpdatedAt(now);
        taskRepository.save(e3);

        taskRepository.flush();

        var planning = taskRepository.findByStatus(TaskStatus.PLANNING);
        assertEquals(2, planning.size());
        assertTrue(planning.stream().allMatch(e -> e.getStatus() == TaskStatus.PLANNING));

        var completed = taskRepository.findByStatus(TaskStatus.COMPLETED);
        assertEquals(1, completed.size());
        assertEquals("task-s3", completed.get(0).getId());
    }

    @Test
    void shouldUpdateTask() {
        Instant now = Instant.now();
        TaskEntity entity = new TaskEntity();
        entity.setId("task-upd");
        entity.setTitle("Original Title");
        entity.setDescription("Original Desc");
        entity.setStatus(TaskStatus.PENDING);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setRepoPath("/repo");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskRepository.save(entity);
        taskRepository.flush();

        entity.setTitle("Updated Title");
        entity.setStatus(TaskStatus.PLANNING);
        entity.setUpdatedAt(Instant.now());
        taskRepository.save(entity);
        taskRepository.flush();

        var found = taskRepository.findById("task-upd").orElseThrow();
        assertEquals("Updated Title", found.getTitle());
        assertEquals(TaskStatus.PLANNING, found.getStatus());
    }

    @Test
    void shouldDeleteTask() {
        Instant now = Instant.now();
        TaskEntity entity = new TaskEntity();
        entity.setId("task-del");
        entity.setTitle("To Delete");
        entity.setDescription("Delete me");
        entity.setStatus(TaskStatus.PENDING);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setRepoPath("/repo");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskRepository.save(entity);
        taskRepository.flush();

        assertTrue(taskRepository.findById("task-del").isPresent());

        taskRepository.deleteById("task-del");
        taskRepository.flush();

        assertFalse(taskRepository.findById("task-del").isPresent());
    }

    @Test
    void shouldPersistJsonFields() {
        Instant now = Instant.now();
        TaskEntity entity = new TaskEntity();
        entity.setId("task-json");
        entity.setTitle("JSON Test");
        entity.setDescription("Test JSON fields");
        entity.setStatus(TaskStatus.PENDING);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setRepoPath("/repo");
        entity.setDependsOn(List.of("dep-1", "dep-2"));
        entity.setSessionIds(List.of("sess-1", "sess-2", "sess-3"));
        var reviewerResult = new ReviewerResult("rev-1", ReviewVerdict.APPROVE, "Looks good", "implementation_issue");
        entity.setReviewerResults(List.of(reviewerResult));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        taskRepository.save(entity);
        taskRepository.flush();

        entityManager.clear();
        var found = taskRepository.findById("task-json").orElseThrow();

        assertEquals(2, found.getDependsOn().size());
        assertTrue(found.getDependsOn().contains("dep-1"));
        assertTrue(found.getDependsOn().contains("dep-2"));

        assertEquals(3, found.getSessionIds().size());
        assertEquals("sess-1", found.getSessionIds().get(0));
        assertEquals("sess-3", found.getSessionIds().get(2));

        assertEquals(1, found.getReviewerResults().size());
        assertEquals("rev-1", found.getReviewerResults().get(0).reviewerId());
        assertEquals(ReviewVerdict.APPROVE, found.getReviewerResults().get(0).verdict());
        assertEquals("Looks good", found.getReviewerResults().get(0).feedback());
        assertEquals("implementation_issue", found.getReviewerResults().get(0).category());
    }

    @Test
    void shouldPersistLongTextFields() {
        Instant now = Instant.now();
        String longPrompt = "A".repeat(100_000);
        String longOutput = "B".repeat(200_000);

        TaskEntity entity = new TaskEntity();
        entity.setId("task-long");
        entity.setTitle("Long Text Test");
        entity.setDescription("Test long text");
        entity.setStatus(TaskStatus.CODING);
        entity.setPriority(TaskPriority.MEDIUM);
        entity.setSource(TaskSource.MANUAL);
        entity.setRepoPath("/repo");
        entity.setPlanOutput(longPrompt);
        entity.setTestOutput(longOutput);
        entity.setCodeOutput(longPrompt);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        taskRepository.save(entity);
        taskRepository.flush();

        entityManager.clear();
        var found = taskRepository.findById("task-long").orElseThrow();

        assertEquals(longPrompt.length(), found.getPlanOutput().length());
        assertEquals(longOutput.length(), found.getTestOutput().length());
        assertEquals(longPrompt.length(), found.getCodeOutput().length());
        assertEquals(longPrompt, found.getPlanOutput());
    }

    @Test
    void shouldFindTasksByParentId() {
        Instant now = Instant.now();
        TaskEntity parent = new TaskEntity();
        parent.setId("parent-1");
        parent.setTitle("Parent");
        parent.setDescription("Parent task");
        parent.setStatus(TaskStatus.PENDING);
        parent.setPriority(TaskPriority.MEDIUM);
        parent.setSource(TaskSource.MANUAL);
        parent.setRepoPath("/repo");
        parent.setCreatedAt(now);
        parent.setUpdatedAt(now);
        taskRepository.save(parent);

        TaskEntity child1 = new TaskEntity();
        child1.setId("child-1");
        child1.setTitle("Child 1");
        child1.setDescription("Child task 1");
        child1.setStatus(TaskStatus.PENDING);
        child1.setPriority(TaskPriority.MEDIUM);
        child1.setSource(TaskSource.MANUAL);
        child1.setRepoPath("/repo");
        child1.setParentId("parent-1");
        child1.setCreatedAt(now);
        child1.setUpdatedAt(now);
        taskRepository.save(child1);

        TaskEntity child2 = new TaskEntity();
        child2.setId("child-2");
        child2.setTitle("Child 2");
        child2.setDescription("Child task 2");
        child2.setStatus(TaskStatus.PENDING);
        child2.setPriority(TaskPriority.MEDIUM);
        child2.setSource(TaskSource.MANUAL);
        child2.setRepoPath("/repo");
        child2.setParentId("parent-1");
        child2.setCreatedAt(now);
        child2.setUpdatedAt(now);
        taskRepository.save(child2);

        taskRepository.flush();

        var children = taskRepository.findByParentId("parent-1");
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(e -> "parent-1".equals(e.getParentId())));
    }

    @Test
    void shouldRoundTripDomainConversion() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        var reviewerResult = new ReviewerResult("rev-1", ReviewVerdict.REQUEST_CHANGES, "Fix tests", "test_issue");
        var domainTask = new Task(
                "task-rt", "Round Trip", "Full round trip test", TaskStatus.REVIEWING,
                TaskPriority.HIGH, TaskSource.CLI, "develop", "parent-1",
                List.of("dep-1"), true, "/repo", "feature/test", "/wt",
                "complex", "plan output", "test output", "treview output", "code output", "review output",
                true, List.of(reviewerResult), List.of("sess-1", "sess-2"),
                3, 2, 4, 5, 10, "feedback", "error",
                now, now, now, now, now
        );

        TaskEntity entity = TaskEntity.fromDomain(domainTask);
        taskRepository.save(entity);
        taskRepository.flush();

        entityManager.clear();
        var found = taskRepository.findById("task-rt").orElseThrow();
        var restored = found.toDomain();

        assertEquals(domainTask.getId(), restored.getId());
        assertEquals(domainTask.getTitle(), restored.getTitle());
        assertEquals(domainTask.getDescription(), restored.getDescription());
        assertEquals(domainTask.getStatus(), restored.getStatus());
        assertEquals(domainTask.getPriority(), restored.getPriority());
        assertEquals(domainTask.getSource(), restored.getSource());
        assertEquals(domainTask.getTaskMode(), restored.getTaskMode());
        assertEquals(domainTask.getParentId(), restored.getParentId());
        assertEquals(domainTask.getDependsOn(), restored.getDependsOn());
        assertEquals(domainTask.isForceNoSplit(), restored.isForceNoSplit());
        assertEquals(domainTask.getRepoPath(), restored.getRepoPath());
        assertEquals(domainTask.getBranchName(), restored.getBranchName());
        assertEquals(domainTask.getWorktreePath(), restored.getWorktreePath());
        assertEquals(domainTask.getComplexity(), restored.getComplexity());
        assertEquals(domainTask.getPlanOutput(), restored.getPlanOutput());
        assertEquals(domainTask.getTestOutput(), restored.getTestOutput());
        assertEquals(domainTask.getTestReviewOutput(), restored.getTestReviewOutput());
        assertEquals(domainTask.getCodeOutput(), restored.getCodeOutput());
        assertEquals(domainTask.getReviewOutput(), restored.getReviewOutput());
        assertEquals(domainTask.isReviewPass(), restored.isReviewPass());
        assertEquals(domainTask.getSessionIds(), restored.getSessionIds());
        assertEquals(domainTask.getRetryCount(), restored.getRetryCount());
        assertEquals(domainTask.getTestRetryCount(), restored.getTestRetryCount());
        assertEquals(domainTask.getCodeRetryCount(), restored.getCodeRetryCount());
        assertEquals(domainTask.getMaxTestRetries(), restored.getMaxTestRetries());
        assertEquals(domainTask.getMaxCodeRetries(), restored.getMaxCodeRetries());
        assertEquals(domainTask.getUserFeedback(), restored.getUserFeedback());
        assertEquals(domainTask.getError(), restored.getError());
        assertEquals(1, restored.getReviewerResults().size());
        assertEquals("rev-1", restored.getReviewerResults().get(0).reviewerId());
        assertEquals(ReviewVerdict.REQUEST_CHANGES, restored.getReviewerResults().get(0).verdict());
    }
}
