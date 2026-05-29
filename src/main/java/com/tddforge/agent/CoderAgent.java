package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CoderAgent extends BaseAgent {

    public CoderAgent(OpenCodeClient openCodeClient,
                      PromptTemplateRegistry promptRegistry,
                      AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    public AgentRun run(AgentContext context) {
        AgentRun agentRun = super.run(context);
        outputExtractor.extractCoderResult(agentRun);
        return agentRun;
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        return PromptTemplateRegistry.Template.CODER_IMPLEMENT;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("title", context.title());
        vars.put("description", context.description());
        vars.put("test_output", context.testOutput() != null ? context.testOutput() : "");
        vars.put("test_review_output", context.testReviewOutput() != null ? context.testReviewOutput() : "");
        vars.put("dependency_context", context.dependencyContext() != null ? context.dependencyContext() : "");
        vars.put("file_path", context.filePath() != null ? context.filePath() : "");
        vars.put("line_number", context.lineNumber() != null ? context.lineNumber() : "");
        vars.put("plan_output", context.planOutput() != null ? context.planOutput() : "");
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "coder";
    }
}
