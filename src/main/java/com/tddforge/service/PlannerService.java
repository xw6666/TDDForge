package com.tddforge.service;

import com.tddforge.agent.AgentOutputExtractor;
import com.tddforge.agent.ExtractionResult;
import com.tddforge.domain.AgentRun;
import com.tddforge.domain.PlannerResult;
import com.tddforge.domain.Task;
import com.tddforge.domain.TaskStatus;
import java.util.List;

public class PlannerService {

    private final AgentOutputExtractor outputExtractor;
    private final PlannerResultValidator validator;
    private final PlannerResultProcessor processor;

    public PlannerService(AgentOutputExtractor outputExtractor,
                          PlannerResultValidator validator,
                          PlannerResultProcessor processor) {
        this.outputExtractor = outputExtractor;
        this.validator = validator;
        this.processor = processor;
    }

    public sealed interface PlannerOutcome permits PlannerOutcome.Success, PlannerOutcome.NeedsRetry, PlannerOutcome.Failed {
        record Success(Task updatedTask, List<Task> childTasks) implements PlannerOutcome {}
        record NeedsRetry(String error) implements PlannerOutcome {}
        record Failed(Task task, String error) implements PlannerOutcome {}
    }

    public PlannerOutcome process(Task task, AgentRun agentRun, int attemptNumber) {
        ExtractionResult<PlannerResult> extraction = outputExtractor.extractPlannerResult(agentRun);

        String error = extractError(extraction);

        if (error == null) {
            PlannerResultValidator.ValidationResult validation = validator.validate(extraction.result());
            if (!validation.isValid()) {
                error = validation.errorMessage();
            }
        }

        if (error != null) {
            if (attemptNumber >= 1) {
                task.setError(error);
                task.setStatus(TaskStatus.FAILED);
                return new PlannerOutcome.Failed(task, error);
            }
            return new PlannerOutcome.NeedsRetry(error);
        }

        PlannerResult result = extraction.result();
        PlannerResultProcessor.ProcessResult processResult = processor.process(task, result);

        return switch (processResult) {
            case PlannerResultProcessor.ProcessResult.SinglePlan(Task updatedTask) ->
                    new PlannerOutcome.Success(updatedTask, List.of());
            case PlannerResultProcessor.ProcessResult.SplitPlan(Task parentTask, List<Task> childTasks) ->
                    new PlannerOutcome.Success(parentTask, childTasks);
        };
    }

    private String extractError(ExtractionResult<PlannerResult> extraction) {
        if (extraction.hasCriticalError()) {
            return extraction.criticalError();
        }
        if (extraction.result() == null) {
            return "Planner result is null";
        }
        return null;
    }
}