package com.tddforge.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    private final RepoConfig repoConfig;
    private final OpencodeConfig opencodeConfig;

    public ConfigValidator(RepoConfig repoConfig, OpencodeConfig opencodeConfig) {
        this.repoConfig = repoConfig;
        this.opencodeConfig = opencodeConfig;
    }

    @PostConstruct
    public void validate() {
        validateRepoPath();
        validateWorktreeDir();
        validateOpencodeConfigPath();
    }

    void validateRepoPath() {
        Path repoPath = Path.of(repoConfig.getPath());
        if (!Files.exists(repoPath)) {
            throw new IllegalStateException(
                "Repo path does not exist: " + repoPath.toAbsolutePath()
            );
        }
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalStateException(
                "Repo path is not a directory: " + repoPath.toAbsolutePath()
            );
        }
        Path gitDir = repoPath.resolve(".git");
        if (!Files.exists(gitDir) || !Files.isDirectory(gitDir)) {
            throw new IllegalStateException(
                "Repo path is not a git repository (no .git directory): " + repoPath.toAbsolutePath()
            );
        }
        log.info("Repo path validated: {}", repoPath.toAbsolutePath());
    }

    void validateWorktreeDir() {
        Path worktreeDir = Path.of(repoConfig.getWorktreeDir());
        if (Files.exists(worktreeDir)) {
            if (!Files.isDirectory(worktreeDir)) {
                throw new IllegalStateException(
                    "Worktree dir exists but is not a directory: " + worktreeDir.toAbsolutePath()
                );
            }
            log.info("Worktree dir already exists: {}", worktreeDir.toAbsolutePath());
            return;
        }
        try {
            Files.createDirectories(worktreeDir);
            log.info("Created worktree dir: {}", worktreeDir.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to create worktree dir: " + worktreeDir.toAbsolutePath(), e
            );
        }
    }

    void validateOpencodeConfigPath() {
        String configPathStr = opencodeConfig.getConfigPath();
        Path configPath = Path.of(configPathStr);
        Path absolutePath = configPath.isAbsolute() ? configPath : configPath.toAbsolutePath().normalize();
        opencodeConfig.setConfigPath(absolutePath.toString());
        log.info("Opencode config path resolved to: {}", absolutePath);
    }
}
