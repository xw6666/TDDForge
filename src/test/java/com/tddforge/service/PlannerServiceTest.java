package com.tddforge.service;

import com.tddforge.agent.AgentOutputExtractor;
import com.tddforge.agent.ExtractionResult;
import com.tddforge.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerServiceTest {

    private PlannerService plannerService;
    private AgentOutputExtractor outputExtractor;
    private PlannerResultValidator validator;
    private PlannerResultProcessor processor;

    @BeforeEach
    void setUp() {
        outputExtractor = new AgentOutputExtractor();
        validator = new PlannerResultValidator();
        AtomicInteger idCounter = new AtomicInteger(0);
        PlannerResultProcessor.IdGenerator idGenerator = () -> String.format("task-%032d", idCounter.incrementAndGet());
        processor = new PlannerResultProcessor(idGenerator);
        plannerService = new PlannerService(outputExtractor, validator, processor);
    }

    private AgentRun createRun(String output) {
        return new AgentRun("run-1", "task-1", "planner", "test-model",
                null, null, "test prompt", output, 0, 1000L, null, 0, Instant.now());
    }

    private String ndjsonWithText(String text) {
        return "{\"type\":\"step_start\",\"step_start\":{\"type\":\"thinking\"}}\n" +
                "{\"type\":\"text\",\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n" +
                "{\"type\":\"step_finish\",\"step_finish\":{\"reason\":\"stop\"}}";
    }

    private Task createTask() {
        return new Task("task-1", "Implement feature X", "Full description", "/repo");
    }

    @Nested
    class SinglePlanProcessing {

        @Test
        void shouldProcessValidSinglePlan() {
            String json = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Overall objective: Fix the bug\\n1. Find the null pointer\\n2. Add null check\"}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Success.class);
            PlannerService.PlannerOutcome.Success success = (PlannerService.PlannerOutcome.Success) outcome;
            assertThat(success.updatedTask().getPlanOutput()).isEqualTo("Overall objective: Fix the bug\n1. Find the null pointer\n2. Add null check");
            assertThat(success.updatedTask().getComplexity()).isEqualTo("medium");
            assertThat(success.childTasks()).isEmpty();
        }
    }

    @Nested
    class SplitPlanProcessing {

        @Test
        void shouldProcessValidSplitPlanWithChildTasks() {
            String json = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Task A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]},{\"title\":\"Task B\",\"description\":\"Do B\",\"priority\":\"medium\",\"depends_on\":[0]}]}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Success.class);
            PlannerService.PlannerOutcome.Success success = (PlannerService.PlannerOutcome.Success) outcome;
            assertThat(success.childTasks()).hasSize(2);
            assertThat(success.updatedTask().getComplexity()).isEqualTo("complex");
        }
    }

    @Nested
    class RetryAndFailureSemantics {

        @Test
        void shouldReturnNeedsRetryOnFirstInvalidJsonAttempt() {
            String output = ndjsonWithText("This is not valid JSON at all.");
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            PlannerService.PlannerOutcome.NeedsRetry needsRetry = (PlannerService.PlannerOutcome.NeedsRetry) outcome;
            assertThat(needsRetry.error()).contains("No valid JSON found");
        }

        @Test
        void shouldReturnFailedOnSecondInvalidJsonAttempt() {
            String output = ndjsonWithText("This is not valid JSON at all.");
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 1);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Failed.class);
            PlannerService.PlannerOutcome.Failed failed = (PlannerService.PlannerOutcome.Failed) outcome;
            assertThat(failed.task().getError()).contains("No valid JSON found");
            assertThat(failed.task().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldReturnFailedOnSubsequentInvalidJsonAttempt() {
            String output = ndjsonWithText("This is not valid JSON at all.");
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 2);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Failed.class);
            assertThat(((PlannerService.PlannerOutcome.Failed) outcome).task().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldReturnNeedsRetryOnFirstValidationFailure() {
            String json = "{\"complexity\":\"extreme\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Fix the bug\"}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            PlannerService.PlannerOutcome.NeedsRetry needsRetry = (PlannerService.PlannerOutcome.NeedsRetry) outcome;
            assertThat(needsRetry.error()).contains("Invalid complexity");
        }

        @Test
        void shouldReturnFailedOnSecondValidationFailure() {
            String json = "{\"complexity\":\"extreme\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Fix the bug\"}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 1);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Failed.class);
            PlannerService.PlannerOutcome.Failed failed = (PlannerService.PlannerOutcome.Failed) outcome;
            assertThat(failed.task().getError()).contains("Invalid complexity");
            assertThat(failed.task().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldReturnNeedsRetryOnEmptySubTasks() {
            String json = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Reason\",\"sub_tasks\":[]}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            assertThat(((PlannerService.PlannerOutcome.NeedsRetry) outcome).error()).contains("sub_tasks must not be empty");
        }

        @Test
        void shouldReturnFailedOnSecondEmptySubTasksAttempt() {
            String json = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Reason\",\"sub_tasks\":[]}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 1);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.Failed.class);
            assertThat(((PlannerService.PlannerOutcome.Failed) outcome).task().getStatus()).isEqualTo(TaskStatus.FAILED);
        }

        @Test
        void shouldReturnNeedsRetryOnIllegalDependsOn() {
            String json = "{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Reason\",\"sub_tasks\":[{\"title\":\"A\",\"description\":\"Do A\",\"priority\":\"high\",\"depends_on\":[]},{\"title\":\"B\",\"description\":\"Do B\",\"priority\":\"medium\",\"depends_on\":[5]}]}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            assertThat(((PlannerService.PlannerOutcome.NeedsRetry) outcome).error()).contains("Invalid depends_on index 5");
        }

        @Test
        void shouldSucceedOnSecondAttemptAfterFirstFailure() {
            String invalidOutput = ndjsonWithText("This is not valid JSON at all.");
            String validJson = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Simple scope\",\"plan\":\"Fix the bug\"}";
            String validOutput = ndjsonWithText(validJson);

            AgentRun firstFailedRun = createRun(invalidOutput);
            AgentRun secondSuccessRun = createRun(validOutput);
            Task task = createTask();

            PlannerService.PlannerOutcome firstOutcome = plannerService.process(task, firstFailedRun, 0);
            assertThat(firstOutcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);

            PlannerService.PlannerOutcome secondOutcome = plannerService.process(task, secondSuccessRun, 1);
            assertThat(secondOutcome).isInstanceOf(PlannerService.PlannerOutcome.Success.class);
            PlannerService.PlannerOutcome.Success success = (PlannerService.PlannerOutcome.Success) secondOutcome;
            assertThat(success.updatedTask().getPlanOutput()).isEqualTo("Fix the bug");
            assertThat(success.updatedTask().getComplexity()).isEqualTo("medium");
        }
    }

    @Nested
    class MalformedJsonHandling {

        @Test
        void shouldHandleMalformedJsonAsCriticalError() {
            String output = ndjsonWithText("{invalid json here}");
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
        }

        @Test
        void shouldHandleMissingFieldsAsValidationError() {
            String json = "{\"complexity\":\"medium\",\"split\":true,\"reason\":\"Reason\"}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            assertThat(((PlannerService.PlannerOutcome.NeedsRetry) outcome).error()).contains("sub_tasks must not be empty");
        }

        @Test
        void shouldHandleMissingPlanForSingleTaskAsValidationError() {
            String json = "{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Reason\"}";
            String output = ndjsonWithText(json);
            AgentRun agentRun = createRun(output);
            Task task = createTask();

            PlannerService.PlannerOutcome outcome = plannerService.process(task, agentRun, 0);

            assertThat(outcome).isInstanceOf(PlannerService.PlannerOutcome.NeedsRetry.class);
            assertThat(((PlannerService.PlannerOutcome.NeedsRetry) outcome).error()).contains("Plan must not be empty");
        }
    }
}