package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.domain.ModelSpec;
import com.tddforge.domain.OpenCodeRequest;
import com.tddforge.opencode.OpenCodeClient;

import java.util.Map;

public abstract class BaseAgent implements Agent {

    protected final OpenCodeClient openCodeClient;
    protected final PromptTemplateRegistry promptRegistry;
    protected final AgentOutputExtractor outputExtractor;

    protected BaseAgent(OpenCodeClient openCodeClient,
                        PromptTemplateRegistry promptRegistry,
                        AgentOutputExtractor outputExtractor) {
        if (openCodeClient == null) {
            throw new IllegalArgumentException("openCodeClient must not be null");
        }
        if (promptRegistry == null) {
            throw new IllegalArgumentException("promptRegistry must not be null");
        }
        if (outputExtractor == null) {
            throw new IllegalArgumentException("outputExtractor must not be null");
        }
        this.openCodeClient = openCodeClient;
        this.promptRegistry = promptRegistry;
        this.outputExtractor = outputExtractor;
    }

    @Override
    public AgentRun run(AgentContext context) {
        String prompt = promptRegistry.render(getTemplate(context), getPromptVariables(context));

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

        return openCodeClient.run(context.taskId(), getAgentType(), request);
    }

    protected abstract PromptTemplateRegistry.Template getTemplate(AgentContext context);

    protected abstract Map<String, String> getPromptVariables(AgentContext context);

    protected abstract String getAgentType();
}
