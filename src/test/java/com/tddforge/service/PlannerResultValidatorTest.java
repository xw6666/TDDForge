package com.tddforge.service;

import com.tddforge.domain.PlannerResult;
import com.tddforge.domain.PlannerResult.SubTask;
import com.tddforge.service.PlannerResultValidator.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerResultValidatorTest {

    private PlannerResultValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PlannerResultValidator();
    }

    @Nested
    class ComplexityValidation {

        @Test
        void shouldAcceptValidComplexityValues() {
            for (String complexity : List.of("very_complex", "complex", "medium", "simple")) {
                PlannerResult result = new PlannerResult(complexity, false, "reason", "plan", null);
                ValidationResult validation = validator.validate(result);
                assertThat(validation.isValid())
                        .as("complexity '%s' should be valid", complexity)
                        .isTrue();
            }
        }

        @Test
        void shouldRejectInvalidComplexity() {
            PlannerResult result = new PlannerResult("extreme", false, "reason", "plan", null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Invalid complexity 'extreme'");
        }

        @Test
        void shouldRejectEmptyComplexity() {
            PlannerResult result = new PlannerResult("medium", false, "reason", "plan", null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }
    }

    @Nested
    class SinglePlanValidation {

        @Test
        void shouldAcceptValidSinglePlan() {
            PlannerResult result = new PlannerResult("medium", false, "Simple scope", "Fix the bug", null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }

        @Test
        void shouldRejectSinglePlanWithEmptyPlan() {
            PlannerResult result = new PlannerResult("medium", false, "Simple scope", "", null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Plan must not be empty when split is false");
        }

        @Test
        void shouldRejectSinglePlanWithNullPlan() {
            PlannerResult result = new PlannerResult("medium", false, "Simple scope", null, null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Plan must not be empty when split is false");
        }
    }

    @Nested
    class SplitPlanValidation {

        @Test
        void shouldAcceptValidSplitPlan() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, List.of(sub1, sub2));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }

        @Test
        void shouldRejectSplitPlanWithNullSubTasks() {
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, null);
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("sub_tasks must not be empty when split is true");
        }

        @Test
        void shouldRejectSplitPlanWithEmptySubTasks() {
            PlannerResult result = new PlannerResult("complex", true, "Needs split", null, List.of());
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("sub_tasks must not be empty when split is true");
        }

        @Test
        void shouldAcceptValidPriorityValues() {
            for (String priority : List.of("low", "medium", "high", "critical")) {
                SubTask sub = new SubTask("Task A", "Do A", priority, List.of());
                PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub));
                ValidationResult validation = validator.validate(result);
                assertThat(validation.isValid())
                        .as("priority '%s' should be valid", priority)
                        .isTrue();
            }
        }

        @Test
        void shouldRejectInvalidPriority() {
            SubTask sub = new SubTask("Task A", "Do A", "urgent", List.of());
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Invalid priority 'urgent'");
        }

        @Test
        void shouldRejectInvalidDependsOnIndex() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(5));
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub1, sub2));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Invalid depends_on index 5");
        }

        @Test
        void shouldRejectNegativeDependsOnIndex() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(-1));
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub1, sub2));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Invalid depends_on index -1");
        }

        @Test
        void shouldAcceptValidDependsOnIndex() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(0));
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub1, sub2));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }

        @Test
        void shouldAcceptDependsOnEqualToLastIndex() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of());
            SubTask sub3 = new SubTask("Task C", "Do C", "low", List.of(0, 1));
            PlannerResult result = new PlannerResult("very_complex", true, "reason", null, List.of(sub1, sub2, sub3));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }

        @Test
        void shouldRejectDependsOnEqualToSize() {
            SubTask sub1 = new SubTask("Task A", "Do A", "high", List.of());
            SubTask sub2 = new SubTask("Task B", "Do B", "medium", List.of(2));
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub1, sub2));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errorMessage()).contains("Invalid depends_on index 2");
        }

        @Test
        void shouldCollectMultipleErrors() {
            SubTask sub = new SubTask("Task A", "Do A", "urgent", List.of(99));
            PlannerResult result = new PlannerResult("extreme", true, "reason", null, List.of(sub));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isFalse();
            assertThat(validation.errors()).hasSize(3);
            assertThat(validation.errorMessage()).contains("Invalid complexity");
            assertThat(validation.errorMessage()).contains("Invalid priority");
            assertThat(validation.errorMessage()).contains("Invalid depends_on index 99");
        }

        @Test
        void shouldAcceptCaseInsensitivePriority() {
            SubTask sub = new SubTask("Task A", "Do A", "HIGH", List.of());
            PlannerResult result = new PlannerResult("complex", true, "reason", null, List.of(sub));
            ValidationResult validation = validator.validate(result);
            assertThat(validation.isValid()).isTrue();
        }
    }
}