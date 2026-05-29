package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReviewerAgent extends BaseAgent {

    public ReviewerAgent(OpenCodeClient openCodeClient,
                         PromptTemplateRegistry promptRegistry,
                         AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    public AgentRun run(AgentContext context) {
        AgentRun agentRun = super.run(context);
        outputExtractor.extractReviewerResult(agentRun, "reviewer-1");
        return agentRun;
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        return PromptTemplateRegistry.Template.REVIEWER_REVIEW;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("title", context.title());
        vars.put("description", context.description());
        vars.put("test_output", context.testOutput() != null ? context.testOutput() : "");
        vars.put("test_review_output", context.testReviewOutput() != null ? context.testReviewOutput() : "");
        vars.put("coder_response", context.coderResponse() != null ? context.coderResponse() : "");
        vars.put("prior_rejections", context.priorRejections() != null ? context.priorRejections() : "");
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "reviewer";
    }
}
