package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestReviewerAgent extends BaseAgent<TestReviewerResult> {

    public TestReviewerAgent(OpenCodeClient openCodeClient,
                             PromptTemplateRegistry promptRegistry,
                             AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        return PromptTemplateRegistry.Template.TEST_REVIEWER;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("title", context.title());
        vars.put("description", context.description());
        vars.put("plan_output", context.planOutput() != null ? context.planOutput() : "");
        vars.put("test_writer_response", context.testWriterResponse() != null ? context.testWriterResponse() : "");
        vars.put("human_revision_feedback", humanRevisionFeedbackBlock(context));
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "test_reviewer";
    }

    @Override
    protected ExtractionResult<TestReviewerResult> extractResult(AgentRun agentRun, AgentContext context) {
        return outputExtractor.extractTestReviewerResult(agentRun);
    }
}
