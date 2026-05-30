package com.tddforge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BaseConfigurationHealthIndicatorTest {

    @TempDir
    Path tempDir;

    private RepoConfig createRepoConfig(String path, String worktreeDir) {
        RepoConfig config = new RepoConfig();
        config.setPath(path);
        config.setWorktreeDir(worktreeDir);
        return config;
    }

    private OpencodeConfig createOpencodeConfig(String configPath, String plannerModel, String coderModel) {
        OpencodeConfig config = new OpencodeConfig();
        config.setConfigPath(configPath);
        com.tddforge.config.ModelSpec planner = new com.tddforge.config.ModelSpec();
        planner.setModel(plannerModel);
        config.setPlanner(planner);
        com.tddforge.config.ModelSpec coder = new com.tddforge.config.ModelSpec();
        coder.setModel(coderModel);
        config.setCoderDefault(coder);
        return config;
    }

    @Test
    void shouldReportUpWhenConfigIsValid() {
        String repoPath = tempDir.toString();
        String worktreeDir = tempDir.resolve("worktrees").toString();

        RepoConfig repoConfig = createRepoConfig(repoPath, worktreeDir);
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("repo.path");
        assertThat(health.getDetails()).containsKey("repo.worktreeDir");
        assertThat(health.getDetails()).containsKey("opencode.configPath");
    }

    @Test
    void shouldReportDownWhenRepoPathDoesNotExist() {
        RepoConfig repoConfig = createRepoConfig("/nonexistent/path", "/tmp/worktrees");
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void shouldReportDownWhenRepoPathIsBlank() {
        RepoConfig repoConfig = createRepoConfig("", "/tmp/worktrees");
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldReportDownWhenRepoPathIsNotDirectory(@TempDir Path tempDir) throws IOException {
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "content");

        RepoConfig repoConfig = createRepoConfig(filePath.toString(), "/tmp/worktrees");
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldReportDownWhenWorktreeDirIsBlank() {
        RepoConfig repoConfig = createRepoConfig(tempDir.toString(), "");
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldReportDownWhenOpencodeConfigPathIsBlank() {
        RepoConfig repoConfig = createRepoConfig(tempDir.toString(), "/tmp/worktrees");
        OpencodeConfig opencodeConfig = createOpencodeConfig("", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldReportDownWhenPlannerModelIsMissing() {
        RepoConfig repoConfig = createRepoConfig(tempDir.toString(), "/tmp/worktrees");
        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("/tmp/opencode.json");
        com.tddforge.config.ModelSpec planner = new com.tddforge.config.ModelSpec();
        planner.setModel("");
        opencodeConfig.setPlanner(planner);
        opencodeConfig.setCoderDefault(new com.tddforge.config.ModelSpec());

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldReportDownWhenCoderModelIsMissing() {
        RepoConfig repoConfig = createRepoConfig(tempDir.toString(), "/tmp/worktrees");
        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("/tmp/opencode.json");
        com.tddforge.config.ModelSpec planner = new com.tddforge.config.ModelSpec();
        planner.setModel("test-model");
        opencodeConfig.setPlanner(planner);
        opencodeConfig.setCoderDefault(new com.tddforge.config.ModelSpec());

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void shouldIncludeDetailsInUpHealth() {
        String repoPath = tempDir.toString();
        String worktreeDir = tempDir.resolve("worktrees").toString();

        RepoConfig repoConfig = createRepoConfig(repoPath, worktreeDir);
        OpencodeConfig opencodeConfig = createOpencodeConfig("/tmp/opencode.json", "test-model", "coder-model");

        BaseConfigurationHealthIndicator indicator = new BaseConfigurationHealthIndicator(repoConfig, opencodeConfig);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("repo.path")).isEqualTo(repoPath);
        assertThat(health.getDetails().get("repo.worktreeDir")).isEqualTo(worktreeDir);
        assertThat(health.getDetails().get("opencode.configPath")).isEqualTo("/tmp/opencode.json");
    }
}
