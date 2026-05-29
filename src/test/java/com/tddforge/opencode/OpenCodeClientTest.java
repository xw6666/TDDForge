package com.tddforge.opencode;

import static org.assertj.core.api.Assertions.assertThat;

import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.ModelSpec;
import com.tddforge.domain.AgentRun;
import com.tddforge.domain.OpenCodeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

class OpenCodeClientTest {

    @TempDir
    Path worktreeDir;

    private OpencodeConfig config;
    private String scriptsDir;

    @BeforeEach
    void setUp() throws IOException {
        config = new OpencodeConfig();
        config.setConfigPath("/tmp/test-opencode.json");
        config.setTimeoutSeconds(30);
        config.setMaxContinues(5);
        config.setPlanner(new ModelSpec());

        // Resolve path to test scripts
        scriptsDir = Paths.get("src/test/resources/scripts").toAbsolutePath().normalize().toString();
    }

    private OpenCodeRequest createRequest(String prompt) {
        return new OpenCodeRequest(
                "test-model",
                worktreeDir,
                prompt,
                null,
                null,
                null,
                Path.of("/tmp/test-opencode.json"),
                30
        );
    }

    @Test
    void shouldCompleteNormallyWithoutContinue() throws IOException {
        OpenCodeClient client = new OpenCodeClient(config);
        client.setOpencodeBinary(scriptsDir + "/fake-opencode-complete.sh");

        AgentRun run = client.run("task-1", "planner", createRequest("Implement feature X"));

        assertThat(run.exitCode()).isEqualTo(0);
        assertThat(run.continueCount()).isEqualTo(0);
        assertThat(run.output()).contains("normal output");
        assertThat(run.sessionId()).isEqualTo("test-sess");
    }

    @Test
    void shouldNotContinueWhenIncompleteWithoutSession() throws IOException {
        OpenCodeClient client = new OpenCodeClient(config);
        client.setOpencodeBinary(scriptsDir + "/fake-opencode-incomplete.sh");

        AgentRun run = client.run("task-2", "planner", createRequest("Implement feature Y"));

        assertThat(run.exitCode()).isEqualTo(0);
        assertThat(run.continueCount()).isEqualTo(0);
        assertThat(run.output()).contains("Working on something...");
        assertThat(run.sessionId()).isNull();
    }

    @Test
    void shouldAutoContinueOnIncompleteOutputWithSession() throws IOException {
        OpenCodeClient client = new OpenCodeClient(config);
        client.setOpencodeBinary(scriptsDir + "/fake-opencode.sh");

        AgentRun run = client.run("task-3", "planner", createRequest("Implement feature Z"));

        // First call outputs incomplete with session, continue outputs complete
        assertThat(run.continueCount()).isGreaterThan(0);
        assertThat(run.output()).contains("first run output");
        assertThat(run.output()).contains("continuation output");
        assertThat(run.sessionId()).isEqualTo("test-sess");
        assertThat(run.exitCode()).isEqualTo(0);
    }

    @Test
    void shouldAutoContinueOnNonZeroExitWithSession() throws IOException {
        config.setMaxContinues(3);
        OpenCodeClient client = new OpenCodeClient(config);
        client.setOpencodeBinary(scriptsDir + "/fake-opencode-fail-with-session.sh");

        AgentRun run = client.run("task-4", "coder", createRequest("Fix bug"));

        // First call: exit code 1, incomplete with session -> triggers continue
        // Continue calls also fail (script doesn't handle "Continue" prompt), exhausts maxContinues=3
        assertThat(run.continueCount()).isEqualTo(3);
        assertThat(run.output()).contains("about to fail");
        assertThat(run.exitCode()).isNotZero();
    }

    @Test
    void shouldStopAfterMaxContinues() throws IOException {
        config.setMaxContinues(2);
        OpenCodeClient client = new OpenCodeClient(config);
        // Use a script that always outputs incomplete with session regardless of "Continue"
        client.setOpencodeBinary(scriptsDir + "/fake-opencode-incomplete-with-session.sh");

        AgentRun run = client.run("task-5", "planner", createRequest("Long task"));

        // Each call outputs incomplete with session, so continue is always triggered
        assertThat(run.continueCount()).isEqualTo(2);
        assertThat(run.output()).contains("first run output");
    }

    @Test
    void shouldFailOnMissingBinary() {
        OpenCodeClient client = new OpenCodeClient(config);
        client.setOpencodeBinary("/nonexistent/opencode");

        AgentRun run = client.run("task-6", "planner", createRequest("Test"));

        assertThat(run.exitCode()).isNegative();
    }
}
