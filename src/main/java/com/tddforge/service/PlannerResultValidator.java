package com.tddforge.service;

import com.tddforge.domain.PlannerResult;
import com.tddforge.domain.TaskPriority;
import java.util.*;

public class PlannerResultValidator {

    private static final Set<String> VALID_COMPLEXITIES = Set.of(
            "very_complex", "complex", "medium", "simple"
    );

    private static final Set<String> VALID_PRIORITIES = Set.of(
            "low", "medium", "high", "critical"
    );

    public ValidationResult validate(PlannerResult result) {
        Objects.requireNonNull(result, "PlannerResult must not be null");
        List<String> errors = new ArrayList<>();

        validateComplexity(result, errors);
        validateSplitConsistency(result, errors);

        return new ValidationResult(errors);
    }

    private void validateComplexity(PlannerResult result, List<String> errors) {
        if (!VALID_COMPLEXITIES.contains(result.complexity())) {
            errors.add("Invalid complexity '" + result.complexity()
                    + "'. Allowed values: very_complex, complex, medium, simple");
        }
    }

    private void validateSplitConsistency(PlannerResult result, List<String> errors) {
        if (!result.split()) {
            if (result.plan() == null || result.plan().isBlank()) {
                errors.add("Plan must not be empty when split is false");
            }
        } else {
            if (result.subTasks() == null || result.subTasks().isEmpty()) {
                errors.add("sub_tasks must not be empty when split is true");
            } else {
                validateSubTasks(result.subTasks(), errors);
            }
        }
    }

    private void validateSubTasks(List<PlannerResult.SubTask> subTasks, List<String> errors) {
        for (int i = 0; i < subTasks.size(); i++) {
            PlannerResult.SubTask subTask = subTasks.get(i);
            validateSubTaskPriority(subTask, i, errors);
            validateSubTaskDependsOn(subTask, i, subTasks.size(), errors);
        }
    }

    private void validateSubTaskPriority(PlannerResult.SubTask subTask, int index, List<String> errors) {
        if (!VALID_PRIORITIES.contains(subTask.priority().toLowerCase())) {
            errors.add("Invalid priority '" + subTask.priority() + "' in sub_task at index " + index
                    + ". Allowed values: low, medium, high, critical");
        }
    }

    private void validateSubTaskDependsOn(PlannerResult.SubTask subTask, int index, int totalSubTasks, List<String> errors) {
        if (subTask.dependsOn() != null) {
            for (int depIdx : subTask.dependsOn()) {
                if (depIdx < 0 || depIdx >= totalSubTasks) {
                    errors.add("Invalid depends_on index " + depIdx + " in sub_task at index " + index
                            + ": must be between 0 and " + (totalSubTasks - 1));
                }
            }
        }
    }

    public record ValidationResult(List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public String errorMessage() {
            return String.join("; ", errors);
        }
    }
}