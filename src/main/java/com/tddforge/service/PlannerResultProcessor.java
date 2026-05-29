package com.tddforge.service;

import com.tddforge.domain.PlannerResult;
import com.tddforge.domain.Task;
import com.tddforge.domain.TaskPriority;
import java.util.*;

public class PlannerResultProcessor {

    private final IdGenerator idGenerator;

    public PlannerResultProcessor(IdGenerator idGenerator) {
        Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.idGenerator = idGenerator;
    }

    public interface IdGenerator {
        String generateId();
    }

    public static class DefaultIdGenerator implements IdGenerator {
        @Override
        public String generateId() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
    }

    public sealed interface ProcessResult permits ProcessResult.SinglePlan, ProcessResult.SplitPlan {
        record SinglePlan(Task updatedTask) implements ProcessResult {}
        record SplitPlan(Task parentTask, List<Task> childTasks) implements ProcessResult {}
    }

    public ProcessResult process(Task parentTask, PlannerResult result) {
        Objects.requireNonNull(parentTask, "parentTask must not be null");
        Objects.requireNonNull(result, "result must not be null");

        if (!result.split()) {
            return processSinglePlan(parentTask, result);
        } else {
            return processSplitPlan(parentTask, result);
        }
    }

    private ProcessResult.SinglePlan processSinglePlan(Task parentTask, PlannerResult result) {
        parentTask.setComplexity(result.complexity());
        parentTask.setPlanOutput(result.plan());
        return new ProcessResult.SinglePlan(parentTask);
    }

    private ProcessResult.SplitPlan processSplitPlan(Task parentTask, PlannerResult result) {
        List<Task> childTasks = new ArrayList<>();
        Map<Integer, String> indexToId = new LinkedHashMap<>();

        for (int i = 0; i < result.subTasks().size(); i++) {
            PlannerResult.SubTask subTask = result.subTasks().get(i);
            String childId = idGenerator.generateId();
            indexToId.put(i, childId);

            TaskPriority priority = mapPriority(subTask.priority());
            String description = buildChildDescription(parentTask, subTask, result);

            Task childTask = new Task(childId, subTask.title(), description, parentTask.getRepoPath());
            childTask.setPriority(priority);
            childTask.setParentId(parentTask.getId());
            childTask.setSource(parentTask.getSource());
            childTask.setTaskMode(parentTask.getTaskMode());
            childTask.setComplexity(mapComplexity(result.complexity()));
            childTask.setForceNoSplit(true);
            childTask.setMaxTestRetries(parentTask.getMaxTestRetries());
            childTask.setMaxCodeRetries(parentTask.getMaxCodeRetries());

            childTasks.add(childTask);
        }

        for (int i = 0; i < result.subTasks().size(); i++) {
            PlannerResult.SubTask subTask = result.subTasks().get(i);
            Task childTask = childTasks.get(i);

            List<String> dependsOnIds = new ArrayList<>();
            if (subTask.dependsOn() != null && !subTask.dependsOn().isEmpty()) {
                for (int idx : subTask.dependsOn()) {
                    dependsOnIds.add(indexToId.get(idx));
                }
            }
            childTask.setDependsOn(dependsOnIds);

            if (!dependsOnIds.isEmpty()) {
                String existingDesc = childTask.getDescription();
                StringBuilder sb = new StringBuilder(existingDesc);
                sb.append("\n\n## Dependency task IDs\n");
                sb.append("This sub-task depends on tasks: ").append(String.join(", ", dependsOnIds));
                childTask.setDescription(sb.toString());
            }
        }

        parentTask.setComplexity(result.complexity());
        parentTask.setPlanOutput(buildParentPlanOutput(result));

        return new ProcessResult.SplitPlan(parentTask, Collections.unmodifiableList(childTasks));
    }

    private String buildChildDescription(Task parentTask, PlannerResult.SubTask subTask, PlannerResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Parent task: ").append(parentTask.getTitle()).append("]\n\n");
        sb.append("## Parent objective\n");
        sb.append(result.reason()).append("\n\n");
        sb.append("## Sub-task boundary\n");
        sb.append(subTask.description()).append("\n\n");

        if (subTask.dependsOn() != null && !subTask.dependsOn().isEmpty()) {
            sb.append("## Dependencies\n");
            sb.append("This sub-task depends on: ");
            List<String> depTitles = subTask.dependsOn().stream()
                    .map(idx -> result.subTasks().get(idx).title())
                    .toList();
            sb.append(String.join(", ", depTitles));
            sb.append("\n\n");
        }

        sb.append("## Acceptance requirements\n");
        sb.append("This sub-task must satisfy its specific scope while contributing to the overall parent objective.");

        return sb.toString();
    }

    private String buildParentPlanOutput(PlannerResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task split into sub-tasks. Reason: ").append(result.reason()).append("\n\n");
        sb.append("Sub-tasks:\n");
        for (int i = 0; i < result.subTasks().size(); i++) {
            PlannerResult.SubTask st = result.subTasks().get(i);
            sb.append(i).append(". ").append(st.title()).append(" (").append(st.priority()).append(")");
            if (st.dependsOn() != null && !st.dependsOn().isEmpty()) {
                sb.append(" — depends on: ").append(st.dependsOn());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private TaskPriority mapPriority(String priority) {
        if (priority == null || priority.isBlank()) return TaskPriority.MEDIUM;
        try {
            return TaskPriority.fromString(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskPriority.MEDIUM;
        }
    }

    private String mapComplexity(String complexity) {
        return complexity;
    }
}