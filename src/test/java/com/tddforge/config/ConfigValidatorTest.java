package com.tddforge.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValidatorTest {

    @Test
    void repoPathDoesNotExist_throwsException(@TempDir Path tempDir) {
        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.resolve("nonexistent").toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath(tempDir.resolve("opencode.json").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);

        assertThatThrownBy(validator::validateRepoPath)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    void repoPathNotAGitRepository_throwsException(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("empty-dir"));

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.resolve("empty-dir").toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath(tempDir.resolve("opencode.json").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);

        assertThatThrownBy(validator::validateRepoPath)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not a git repository");
    }

    @Test
    void validGitRepoPath_passes(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path repoDir = tempDir.resolve("my-repo");
        Files.createDirectories(repoDir);

        Process init = new ProcessBuilder("git", "init")
            .directory(repoDir.toFile())
            .start();
        int exitCode = init.waitFor();
        assertThat(exitCode).isZero();

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(repoDir.toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath(tempDir.resolve("opencode.json").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);
        validator.validateRepoPath();
    }

    @Test
    void worktreeDirCreatedWhenMissing(@TempDir Path tempDir) {
        Path worktreeDir = tempDir.resolve("worktrees");

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.toString());
        repoConfig.setWorktreeDir(worktreeDir.toString());

        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath(tempDir.resolve("opencode.json").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);
        validator.validateWorktreeDir();

        assertThat(worktreeDir).isDirectory();
    }

    @Test
    void worktreeDirAlreadyExists_passes(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("worktrees"));

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath(tempDir.resolve("opencode.json").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);
        validator.validateWorktreeDir();
    }

    @Test
    void opencodeConfigPathResolvedToAbsolute(@TempDir Path tempDir) {
        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("relative/path/opencode.json");

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);
        validator.validateOpencodeConfigPath();

        assertThat(opencodeConfig.getConfigPath()).startsWith("/");
        assertThat(opencodeConfig.getConfigPath()).endsWith("/relative/path/opencode.json");
    }

    @Test
    void opencodeConfigPathAlreadyAbsolute_staysUnchanged(@TempDir Path tempDir) {
        OpencodeConfig opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("/etc/opengiraffe-java/opencode.json");

        RepoConfig repoConfig = new RepoConfig();
        repoConfig.setPath(tempDir.toString());
        repoConfig.setWorktreeDir(tempDir.resolve("worktrees").toString());

        ConfigValidator validator = new ConfigValidator(repoConfig, opencodeConfig);
        validator.validateOpencodeConfigPath();

        assertThat(opencodeConfig.getConfigPath()).isEqualTo("/etc/opengiraffe-java/opencode.json");
    }
}
