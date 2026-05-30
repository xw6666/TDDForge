package com.tddforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class BaseConfigurationHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BaseConfigurationHealthIndicator.class);

    private final RepoConfig repoConfig;
    private final OpencodeConfig opencodeConfig;

    public BaseConfigurationHealthIndicator(RepoConfig repoConfig, OpencodeConfig opencodeConfig) {
        this.repoConfig = repoConfig;
        this.opencodeConfig = opencodeConfig;
    }

    @Override
    public Health health() {
        try {
            validateRepoPath();
            validateWorktreeDir();
            validateOpencodeConfigPath();
            validateModelConfigs();
            return Health.up()
                    .withDetail("repo.path", repoConfig.getPath())
                    .withDetail("repo.worktreeDir", repoConfig.getWorktreeDir())
                    .withDetail("opencode.configPath", opencodeConfig.getConfigPath())
                    .build();
        } catch (Exception e) {
            log.warn("Base configuration health check failed: {}", e.getMessage());
            return Health.down()
                    .withException(e)
                    .build();
        }
    }

    private void validateRepoPath() {
        String path = repoConfig.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("repo.path is not configured");
        }
        Path repoPath = Path.of(path);
        if (!Files.exists(repoPath)) {
            throw new IllegalStateException("repo.path does not exist: " + path);
        }
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalStateException("repo.path is not a directory: " + path);
        }
    }

    private void validateWorktreeDir() {
        String dir = repoConfig.getWorktreeDir();
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("repo.worktree-dir is not configured");
        }
    }

    private void validateOpencodeConfigPath() {
        String configPath = opencodeConfig.getConfigPath();
        if (configPath == null || configPath.isBlank()) {
            throw new IllegalStateException("opencode.config-path is not configured");
        }
    }

    private void validateModelConfigs() {
        if (opencodeConfig.getPlanner() == null) {
            throw new IllegalStateException("opencode.planner is not configured");
        }
        if (opencodeConfig.getPlanner().getModel() == null || opencodeConfig.getPlanner().getModel().isBlank()) {
            throw new IllegalStateException("opencode.planner.model is not configured");
        }
        if (opencodeConfig.getCoderDefault() == null) {
            throw new IllegalStateException("opencode.coder-default is not configured");
        }
        if (opencodeConfig.getCoderDefault().getModel() == null || opencodeConfig.getCoderDefault().getModel().isBlank()) {
            throw new IllegalStateException("opencode.coder-default.model is not configured");
        }
    }
}
