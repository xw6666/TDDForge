package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestWriterAgent extends BaseAgent {

    public TestWriterAgent(OpenCodeClient openCodeClient,
                           PromptTemplateRegistry promptRegistry,
                           AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
    }

    @Override
    public AgentRun run(AgentContext context) {
        AgentRun agentRun = super.run(context);
        outputExtractor.extractTestWriterResult(agentRun);
        return agentRun;
    }

    @Override
    protected PromptTemplateRegistry.Template getTemplate(AgentContext context) {
        return PromptTemplateRegistry.Template.TEST_WRITER;
    }

    @Override
    protected Map<String, String> getPromptVariables(AgentContext context) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("title", context.title());
        vars.put("description", context.description());
        vars.put("repo_path", context.repoPath());
        vars.put("plan_output", context.planOutput() != null ? context.planOutput() : "");
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "test_writer";
    }
}
