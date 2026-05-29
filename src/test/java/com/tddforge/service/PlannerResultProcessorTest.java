package com.tddforge.service;

import com.tddforge.domain.*;
import com.tddforge.domain.PlannerResult.SubTask;
import com.tddforge.service.PlannerResultProcessor.IdGenerator;
import com.tddforge.service.PlannerResultProcessor.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerResultProcessorTest {

    private PlannerResultProcessor processor;
    private AtomicInteger idCounter;

    @BeforeEach
    void setUp() {
        idCounter = new AtomicInteger(0);
        IdGenerator idGenerator = () -> String.format("task-%032d", idCounter.incrementAndGet());
        processor = new PlannerResultProcessor(idGenerator);
    }

    private Task createParentTask() {
        Task task = new Task("parent-1", "Implement feature X", "Full feature description", "/repo/project");
        task.setSource(TaskSource.MANUAL);
        task.setTaskMode("develop");
        task.setMaxTestRetries(2);
        task.setMaxCodeRetries(4);
        return task;
    }

    @Nested
    class SinglePlanProcessing {

        @Test
        void shouldSavePlanToTaskPlanOutput() {
            Task parentTask = createParentTask();
            PlannerResult result = new PlannerResult("medium", false, "Simple change", "Overall objective: Fix the bug\n1. Locate the null pointer\n2. Add null check", null);

            ProcessResult processResult = processor.process(parentTask, result);

            assertThat(processResult).isInstanceOf(ProcessResult.SinglePlan.class);
            ProcessResult.SinglePlan singlePlan = (ProcessResult.SinglePlan) processResult;
            assertThat(singlePlan.updatedTask().getPlanOutput()).isEqualTo("Overall objective: Fix the bug\n1. Locate the null pointer\n2. Add null check");
        }

        @Test
        void shouldSetComplexityOnTask() {
            Task parentTask = createParentTask();
            PlannerResult result = new PlannerResult("complex", false, "Needs careful design", "The plan", null);

            ProcessResult processResult = processor.process(parentTask, result);

            assertThat(processResult).isInstanceOf(ProcessResult.SinglePlan.class);
            ProcessResult.SinglePlan singlePlan = (ProcessResult.SinglePlan) processResult;
            assertThat(singlePlan.updatedTask().getComplexity()).isEqualTo("complex");
        }

        @Test
        void shouldPreserveTaskIdentity() {
            Task parentTask = createParentTask();
            PlannerResult result = new PlannerResult("simple", false, "Trivial fix", "Fix typo", null);

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SinglePlan singlePlan = (ProcessResult.SinglePlan) processResult;
            assertThat(singlePlan.updatedTask().getId()).isEqualTo("parent-1");
            assertThat(singlePlan.updatedTask().getTitle()).isEqualTo("Implement feature X");
            assertThat(singlePlan.updatedTask().getRepoPath()).isEqualTo("/repo/project");
        }
    }

    @Nested
    class SplitPlanProcessing {

        @Test
        void shouldCreateChildTasks() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Define interface", "Create the new interface", "high", List.of());
            SubTask sub2 = new SubTask("Implement logic", "Implement the core logic", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Multiple concerns", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            assertThat(processResult).isInstanceOf(ProcessResult.SplitPlan.class);
            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks()).hasSize(2);
        }

        @Test
        void shouldSetParentIdOnChildTasks() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            for (Task child : splitPlan.childTasks()) {
                assertThat(child.getParentId()).isEqualTo("parent-1");
            }
        }

        @Test
        void shouldConvertDependsOnFromIndexToTaskId() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            Task childA = splitPlan.childTasks().get(0);
            Task childB = splitPlan.childTasks().get(1);

            assertThat(childA.getDependsOn()).isEmpty();
            assertThat(childB.getDependsOn()).hasSize(1);
            assertThat(childB.getDependsOn().get(0)).isEqualTo(childA.getId());
        }

        @Test
        void shouldSetChildTaskTitles() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Define interface", "Create the new interface", "high", List.of());
            SubTask sub2 = new SubTask("Implement logic", "Implement the core logic", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks().get(0).getTitle()).isEqualTo("Define interface");
            assertThat(splitPlan.childTasks().get(1).getTitle()).isEqualTo("Implement logic");
        }

        @Test
        void shouldIncludeParentObjectiveInChildDescription() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A specifically", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Multiple concerns in the system", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            String description = splitPlan.childTasks().get(0).getDescription();
            assertThat(description).contains("Parent objective");
            assertThat(description).contains("Multiple concerns in the system");
        }

        @Test
        void shouldIncludeChildBoundaryInDescription() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Handle the data layer changes", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            String description = splitPlan.childTasks().get(0).getDescription();
            assertThat(description).contains("Sub-task boundary");
            assertThat(description).contains("Handle the data layer changes");
        }

        @Test
        void shouldIncludeDependencyInfoInDescription() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            String description = splitPlan.childTasks().get(1).getDescription();
            assertThat(description).contains("Dependencies");
            assertThat(description).contains("Task A");
        }

        @Test
        void shouldIncludeAcceptanceRequirementsInDescription() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            String description = splitPlan.childTasks().get(0).getDescription();
            assertThat(description).contains("Acceptance requirements");
        }

        @Test
        void shouldIncludeRealTaskIdInDependencyDescription() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            Task childA = splitPlan.childTasks().get(0);
            Task childB = splitPlan.childTasks().get(1);
            assertThat(childB.getDescription()).contains(childA.getId());
        }

        @Test
        void shouldSetForceNoSplitOnChildTasks() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks().get(0).isForceNoSplit()).isTrue();
        }

        @Test
        void shouldMapPriorityCorrectly() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks().get(0).getPriority()).isEqualTo(TaskPriority.HIGH);
        }

        @Test
        void shouldMapLowercasePriorityCorrectly() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "critical", List.of());
            PlannerResult result = new PlannerResult("very_complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks().get(0).getPriority()).isEqualTo(TaskPriority.CRITICAL);
        }

        @Test
        void shouldSetParentTaskComplexityAndPlanOutput() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Multiple concerns", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.parentTask().getComplexity()).isEqualTo("complex");
            assertThat(splitPlan.parentTask().getPlanOutput()).contains("Task split into sub-tasks");
            assertThat(splitPlan.parentTask().getPlanOutput()).contains("Multiple concerns");
        }

        @Test
        void shouldSetChildTaskProperties() {
            Task parentTask = createParentTask();
            SubTask sub = new SubTask("Task A", "Do A", "high", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Reason", null, List.of(sub));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            Task child = splitPlan.childTasks().get(0);

            assertThat(child.getSource()).isEqualTo(TaskSource.MANUAL);
            assertThat(child.getTaskMode()).isEqualTo("develop");
            assertThat(child.getRepoPath()).isEqualTo("/repo/project");
            assertThat(child.getMaxTestRetries()).isEqualTo(2);
            assertThat(child.getMaxCodeRetries()).isEqualTo(4);
            assertThat(child.getStatus()).isEqualTo(TaskStatus.PENDING);
        }

        @Test
        void shouldHandleMultipleDependencies() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of());
            SubTask sub3 = new SubTask("Task C", "Do C", "low", List.of(0, 1));
            PlannerResult result = new PlannerResult("very_complex", true, "Reason", null, List.of(sub1, sub2, sub3));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            Task childA = splitPlan.childTasks().get(0);
            Task childB = splitPlan.childTasks().get(1);
            Task childC = splitPlan.childTasks().get(2);

            assertThat(childC.getDependsOn()).hasSize(2);
            assertThat(childC.getDependsOn()).containsExactly(childA.getId(), childB.getId());
        }

        @Test
        void shouldHandleIndependentTasksWithNoDependencies() {
            Task parentTask = createParentTask();
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of());
            PlannerResult result = new PlannerResult("complex", true, "Parallel tasks", null, List.of(sub1, sub2));

            ProcessResult processResult = processor.process(parentTask, result);

            ProcessResult.SplitPlan splitPlan = (ProcessResult.SplitPlan) processResult;
            assertThat(splitPlan.childTasks().get(0).getDependsOn()).isEmpty();
            assertThat(splitPlan.childTasks().get(1).getDependsOn()).isEmpty();
        }
    }
}