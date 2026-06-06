package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.domain.ModelSpec;
import com.tddforge.domain.OpenCodeRequest;
import com.tddforge.domain.TestWriterResult;
import com.tddforge.opencode.OpenCodeClient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestWriterAgent extends BaseAgent<TestWriterResult> {

    public TestWriterAgent(OpenCodeClient openCodeClient,
                           PromptTemplateRegistry promptRegistry,
                           AgentOutputExtractor outputExtractor) {
        super(openCodeClient, promptRegistry, outputExtractor);
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
        vars.put("human_revision_feedback", humanRevisionFeedbackBlock(context));
        return vars;
    }

    @Override
    protected String getAgentType() {
        return "test_writer";
    }

    @Override
    protected ExtractionResult<TestWriterResult> extractResult(AgentRun agentRun, AgentContext context) {
        return outputExtractor.extractTestWriterResult(agentRun);
    }

    @Override
    public AgentResult<TestWriterResult> run(AgentContext context) {
        String prompt;
        if (context.testPhaseFeedback() != null && !context.testPhaseFeedback().isBlank()) {
            String basePrompt = promptRegistry.render(PromptTemplateRegistry.Template.TEST_WRITER, getPromptVariables(context));
            Map<String, String> retryVars = new LinkedHashMap<>();
            retryVars.put("attempt", String.valueOf(context.attempt() != null ? context.attempt() : 1));
            retryVars.put("test_phase_feedback", context.testPhaseFeedback());
            retryVars.put("human_revision_feedback", humanRevisionFeedbackBlock(context));
            String retryPrompt = promptRegistry.render(PromptTemplateRegistry.Template.TEST_WRITER_RETRY, retryVars);
            prompt = basePrompt + "\n\n" + retryPrompt;
        } else {
            prompt = promptRegistry.render(getTemplate(context), getPromptVariables(context));
        }

        ModelSpec modelSpec = context.modelSpec();
        OpenCodeRequest request = new OpenCodeRequest(
                modelSpec.model(),
                context.worktreePath(),
                prompt,
                context.sessionId(),
                modelSpec.variant(),
                modelSpec.agent(),
                null,
                context.timeoutSeconds()
        );

        AgentRun agentRun = openCodeClient.run(context.taskId(), getAgentType(), request);
        ExtractionResult<TestWriterResult> extractionResult = extractResult(agentRun, context);
        return new AgentResult<>(agentRun, extractionResult);
    }
}
