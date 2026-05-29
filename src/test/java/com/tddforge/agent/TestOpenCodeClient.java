package com.tddforge.agent;

import com.tddforge.domain.AgentRun;
import com.tddforge.domain.OpenCodeRequest;
import com.tddforge.opencode.OpenCodeClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestOpenCodeClient extends OpenCodeClient {

    private final List<CapturedCall> capturedCalls = new ArrayList<>();
    private AgentRun nextResult;

    public TestOpenCodeClient() {
        super(createTestConfig());
    }

    private static com.tddforge.config.OpencodeConfig createTestConfig() {
        com.tddforge.config.OpencodeConfig config = new com.tddforge.config.OpencodeConfig();
        config.setConfigPath("/tmp/test-opencode-config.json");
        return config;
    }

    public void setNextResult(AgentRun result) {
        this.nextResult = result;
    }

    @Override
    public AgentRun run(String taskId, String agentType, OpenCodeRequest request) {
        capturedCalls.add(new CapturedCall(taskId, agentType, request));
        if (nextResult != null) {
            return nextResult;
        }
        return new AgentRun(
                UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                taskId,
                agentType,
                request.model(),
                request.variant(),
                request.agent(),
                request.prompt(),
                "fake output",
                0,
                100L,
                "fake-session-id",
                0,
                Instant.now()
        );
    }

    public List<CapturedCall> getCapturedCalls() {
        return capturedCalls;
    }

    public CapturedCall getLastCall() {
        if (capturedCalls.isEmpty()) {
            throw new IllegalStateException("No calls captured");
        }
        return capturedCalls.get(capturedCalls.size() - 1);
    }

    public record CapturedCall(String taskId, String agentType, OpenCodeRequest request) {
    }
}
