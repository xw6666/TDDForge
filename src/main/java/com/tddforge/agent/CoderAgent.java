package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.domain.CoderResult;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CoderAgent extends BaseAgent<CoderResult> {

    public CoderAgent(OpenCodeClient openCodeClient,
                      PromptTemplateRegistry promptRegistry,
                      AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        if (context.testPhaseFeedback() != null && !context.testPhaseFeedback().isBlank()) {
            return PromptTemplateRegistry.Template.CODER_RETRY;
        }
        return PromptTemplateRegistry.Template.CODER_IMPLEMENT;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        if (context.testPhaseFeedback() != null && !context.testPhaseFeedback().isBlank()) {
            vars.put("attempt", context.attempt() != null ? String.valueOf(context.attempt()) : "1");
            vars.put("review_feedback", context.testPhaseFeedback());
        } else {
            vars.put("title", context.title());
            vars.put("description", context.description());
            vars.put("test_output", context.testOutput() != null ? context.testOutput() : "");
            vars.put("test_review_output", context.testReviewOutput() != null ? context.testReviewOutput() : "");
            vars.put("dependency_context", context.dependencyContext() != null ? context.dependencyContext() : "");
            vars.put("file_path", context.filePath() != null ? context.filePath() : "");
            vars.put("line_number", context.lineNumber() != null ? context.lineNumber() : "");
            vars.put("plan_output", context.planOutput() != null ? context.planOutput() : "");
        }
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "coder";
    }

    @Override
    protected ExtractionResult<CoderResult> extractResult(AgentRun agentRun, AgentContext context) {
        return outputExtractor.extractCoderResult(agentRun);
    }
}
