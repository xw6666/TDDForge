package com.tddforge.config;

import com.tddforge.agent.*;
import com.tddforge.git.WorktreeManager;
import com.tddforge.opencode.OpenCodeClient;
import com.tddforge.opencode.OpenCodeNdjsonParser;
import com.tddforge.orchestrator.Orchestrator;
import com.tddforge.persistence.AgentRunRepository;
import com.tddforge.persistence.TaskEventRepository;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.service.DependencyTracker;
import com.tddforge.service.PlannerResultProcessor;
import com.tddforge.service.PlannerResultValidator;
import com.tddforge.service.PlannerService;
import com.tddforge.service.TaskExecutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public OpenCodeNdjsonParser openCodeNdjsonParser() {
        return new OpenCodeNdjsonParser();
    }

    @Bean
    public OpenCodeClient openCodeClient(OpencodeConfig opencodeConfig) {
        return new OpenCodeClient(opencodeConfig);
    }

    @Bean
    public WorktreeManager worktreeManager(RepoConfig repoConfig, PublishConfig publishConfig) {
        return new WorktreeManager(repoConfig, publishConfig);
    }

    @Bean
    public DependencyTracker dependencyTracker(TaskRepository taskRepository) {
        return new DependencyTracker(taskRepository);
    }

    @Bean
    public PlannerResultValidator plannerResultValidator() {
        return new PlannerResultValidator();
    }

    @Bean
    public PlannerResultProcessor plannerResultProcessor() {
        return new PlannerResultProcessor(new PlannerResultProcessor.DefaultIdGenerator());
    }

    @Bean
    public PlannerService plannerService(AgentOutputExtractor agentOutputExtractor,
                                         PlannerResultValidator plannerResultValidator,
                                         PlannerResultProcessor plannerResultProcessor) {
        return new PlannerService(agentOutputExtractor, plannerResultValidator, plannerResultProcessor);
    }

    @Bean
    public AgentOutputExtractor agentOutputExtractor() {
        return new AgentOutputExtractor();
    }

    @Bean
    public PromptTemplateRegistry promptTemplateRegistry() {
        return new PromptTemplateRegistry();
    }

    @Bean
    public PlannerAgent plannerAgent(OpenCodeClient openCodeClient,
                                     PromptTemplateRegistry promptTemplateRegistry,
                                     AgentOutputExtractor agentOutputExtractor) {
        return new PlannerAgent(openCodeClient, promptTemplateRegistry, agentOutputExtractor);
    }

    @Bean
    public TestWriterAgent testWriterAgent(OpenCodeClient openCodeClient,
                                           PromptTemplateRegistry promptTemplateRegistry,
                                           AgentOutputExtractor agentOutputExtractor) {
        return new TestWriterAgent(openCodeClient, promptTemplateRegistry, agentOutputExtractor);
    }

    @Bean
    public TestReviewerAgent testReviewerAgent(OpenCodeClient openCodeClient,
                                               PromptTemplateRegistry promptTemplateRegistry,
                                               AgentOutputExtractor agentOutputExtractor) {
        return new TestReviewerAgent(openCodeClient, promptTemplateRegistry, agentOutputExtractor);
    }

    @Bean
    public CoderAgent coderAgent(OpenCodeClient openCodeClient,
                                 PromptTemplateRegistry promptTemplateRegistry,
                                 AgentOutputExtractor agentOutputExtractor) {
        return new CoderAgent(openCodeClient, promptTemplateRegistry, agentOutputExtractor);
    }

    @Bean
    public ReviewerAgent reviewerAgent(OpenCodeClient openCodeClient,
                                       PromptTemplateRegistry promptTemplateRegistry,
                                       AgentOutputExtractor agentOutputExtractor) {
        return new ReviewerAgent(openCodeClient, promptTemplateRegistry, agentOutputExtractor);
    }

    @Bean
    public TaskExecutionService taskExecutionService(
            TaskRepository taskRepository,
            AgentRunRepository agentRunRepository,
            TaskEventRepository taskEventRepository,
            PlannerAgent plannerAgent,
            TestWriterAgent testWriterAgent,
            TestReviewerAgent testReviewerAgent,
            CoderAgent coderAgent,
            ReviewerAgent reviewerAgent,
            PlannerService plannerService,
            DependencyTracker dependencyTracker,
            WorktreeManager worktreeManager,
            OrchestratorConfig orchestratorConfig,
            OpencodeConfig opencodeConfig,
            RepoConfig repoConfig) {
        return new TaskExecutionService(
                taskRepository, agentRunRepository, taskEventRepository,
                plannerAgent, testWriterAgent, testReviewerAgent, coderAgent, reviewerAgent,
                plannerService, dependencyTracker, worktreeManager,
                orchestratorConfig, opencodeConfig, repoConfig);
    }

    @Bean
    public Orchestrator orchestrator(
            TaskRepository taskRepository,
            DependencyTracker dependencyTracker,
            TaskExecutionService taskExecutionService,
            OrchestratorConfig orchestratorConfig) {
        return new Orchestrator(taskRepository, dependencyTracker, taskExecutionService, orchestratorConfig);
    }
}
