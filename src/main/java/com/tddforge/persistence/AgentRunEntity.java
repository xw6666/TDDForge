package com.tddforge.persistence;

import com.tddforge.domain.AgentRun;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    @Column(name = "agent_type", nullable = false, length = 64)
    private String agentType;

    @Column(name = "model", nullable = false, length = 256)
    private String model;

    @Column(name = "variant", nullable = false, length = 128)
    private String variant = "";

    @Column(name = "agent", nullable = false, length = 128)
    private String agent = "";

    @Column(name = "prompt", nullable = false, columnDefinition = "LONGTEXT")
    private String prompt;

    @Column(name = "output", nullable = false, columnDefinition = "LONGTEXT")
    private String output;

    @Column(name = "exit_code", nullable = false)
    private int exitCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "session_id", nullable = false, length = 256)
    private String sessionId = "";

    @Column(name = "continue_count", nullable = false)
    private int continueCount;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt;

    public AgentRunEntity() {
    }

    public static AgentRunEntity fromDomain(AgentRun run) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.id = run.id();
        entity.taskId = run.taskId();
        entity.agentType = run.agentType();
        entity.model = run.model();
        entity.variant = run.variant() != null ? run.variant() : "";
        entity.agent = run.agent() != null ? run.agent() : "";
        entity.prompt = run.prompt();
        entity.output = run.output();
        entity.exitCode = run.exitCode();
        entity.durationMs = run.durationMs();
        entity.sessionId = run.sessionId() != null ? run.sessionId() : "";
        entity.continueCount = run.continueCount();
        entity.createdAt = run.createdAt();
        return entity;
    }

    public AgentRun toDomain() {
        return new AgentRun(
                id, taskId, agentType, model,
                variant.isEmpty() ? null : variant,
                agent.isEmpty() ? null : agent,
                prompt, output, exitCode, durationMs,
                sessionId.isEmpty() ? null : sessionId,
                continueCount, createdAt
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public int getContinueCount() { return continueCount; }
    public void setContinueCount(int continueCount) { this.continueCount = continueCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
