package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.domain.PlannerResult;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlannerAgent extends BaseAgent<PlannerResult> {

    public PlannerAgent(OpenCodeClient openCodeClient,
                        PromptTemplateRegistry promptRegistry,
                        AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        if (context.forceNoSplit()) {
            return PromptTemplateRegistry.Template.PLANNER_NO_SPLIT;
        }
        return PromptTemplateRegistry.Template.PLANNER_ANALYZE_SPLIT;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("title", context.title());
        vars.put("description", context.description());
        vars.put("repo_path", context.repoPath());
        vars.put("human_revision_feedback", humanRevisionFeedbackBlock(context));
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "planner";
    }

    @Override
    protected ExtractionResult<PlannerResult> extractResult(AgentRun agentRun, AgentContext context) {
        return outputExtractor.extractPlannerResult(agentRun);
    }
}
