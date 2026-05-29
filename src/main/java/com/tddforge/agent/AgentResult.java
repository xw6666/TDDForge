package com.tddforge.agent;

public record AgentResult<T>(
        com.tddforge.domain.AgentRun agentRun,
        ExtractionResult<T> extractionResult
) {
    public AgentResult {
        if (agentRun == null) {
            throw new IllegalArgumentException("agentRun must not be null");
        }
        if (extractionResult == null) {
            throw new IllegalArgumentException("extractionResult must not be null");
        }
    }
}
