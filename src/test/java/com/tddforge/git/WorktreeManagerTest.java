package com.tddforge.git;

import com.tddforge.config.PublishConfig;
import com.tddforge.config.RepoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorktreeManagerTest {

    @TempDir
    Path tempDir;

    Path bareRemote;
    Path localRepo;
    Path worktreeDir;

    RepoConfig repoConfig;
    PublishConfig publishConfig;
    WorktreeManager manager;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        bareRemote = tempDir.resolve("remote.git");
        localRepo = tempDir.resolve("local-repo");
        worktreeDir = tempDir.resolve("worktrees");

        run(tempDir, "git", "init", "--bare", bareRemote.toString());

        run(tempDir, "git", "clone", bareRemote.toString(), localRepo.toString());

        Path agentsFile = localRepo.resolve("AGENTS.md");
        Files.writeString(agentsFile, "# Agent Instructions\nTest content\n");

        Path hooksDir = localRepo.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("pre-task.sh"), "#!/bin/sh\ntrue\n");
        hooksDir.resolve("pre-task.sh").toFile().setExecutable(true);

        run(localRepo, "git", "add", ".");
        run(localRepo, "git", "config", "user.email", "test@test.com");
        run(localRepo, "git", "config", "user.name", "Test");
        run(localRepo, "git", "commit", "-m", "initial commit");
        run(localRepo, "git", "push", "origin", "master");

        repoConfig = new RepoConfig();
        repoConfig.setPath(localRepo.toString());
        repoConfig.setBaseBranch("master");
        repoConfig.setWorktreeDir(worktreeDir.toString());
        repoConfig.setWorktreeHooks(List.of("sh hooks/pre-task.sh"));

        publishConfig = new PublishConfig();
        publishConfig.setRemote("origin");

        manager = new WorktreeManager(repoConfig, publishConfig);
    }

    @Nested
    class CreateWorktree {

        @Test
        void createsWorktreeWithBranchAndCopiesFiles() throws Exception {
            Path wt = manager.createWorktree("task-1", "task/task-1/fix-login");

            assertThat(Files.exists(wt)).isTrue();
            assertThat(Files.exists(wt.resolve("AGENTS.md"))).isTrue();
            assertThat(Files.readString(wt.resolve("AGENTS.md"))).contains("Agent Instructions");
            assertThat(Files.exists(wt.resolve("hooks/pre-task.sh"))).isTrue();
        }

        @Test
        void createsWorktreeOnNewBranch() throws Exception {
            Path wt = manager.createWorktree("task-2", "task/task-2/new-feature");

            run(wt, "git", "status");

            assertThat(wt.resolve(".git").toFile().exists() || Files.exists(wt.resolve(".git"))).isTrue();
        }

        @Test
        void executesWorktreeHooks() throws Exception {
            repoConfig.setWorktreeHooks(List.of("echo hook-executed > hook-marker.txt"));

            Path wt = manager.createWorktree("task-3", "task/task-3/hook-test");

            Path marker = wt.resolve("hook-marker.txt");
            assertThat(Files.exists(marker)).isTrue();
            assertThat(Files.readString(marker)).contains("hook-executed");
        }

        @Test
        void copiesAgentsMdToWorktree() {
            Path wt = manager.createWorktree("task-4", "task/task-4/agents-test");

            assertThat(Files.exists(wt.resolve("AGENTS.md"))).isTrue();
        }

        @Test
        void rejectsInvalidBranchName() {
            assertThatThrownBy(() -> manager.createWorktree("task-5", "../escape"))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("Invalid branch name");
        }

        @Test
        void rejectsEmptyBranchName() {
            assertThatThrownBy(() -> manager.createWorktree("task-6", ""))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        void rejectsNullBranchName() {
            assertThatThrownBy(() -> manager.createWorktree("task-7", null))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("must not be null or blank");
        }
    }

    @Nested
    class StatusQuery {

        @Test
        void returnsCleanStatusOnNewWorktree() {
            Path wt = manager.createWorktree("task-10", "task/task-10/clean-status");

            com.tddforge.git.GitStatus status = manager.status(wt);

            assertThat(status.branch()).isEqualTo("task/task-10/clean-status");
            assertThat(status.isClean()).isTrue();
            assertThat(status.stagedFiles()).isEmpty();
            assertThat(status.unstagedFiles()).isEmpty();
            assertThat(status.untrackedFiles()).isEmpty();
        }

        @Test
        void detectsModifiedFiles() throws Exception {
            Path wt = manager.createWorktree("task-11", "task/task-11/modified");

            Files.writeString(wt.resolve("new-file.txt"), "new content");

            com.tddforge.git.GitStatus status = manager.status(wt);

            assertThat(status.isClean()).isFalse();
            assertThat(status.untrackedFiles()).contains("new-file.txt");
        }

        @Test
        void fileStagedAndFurtherModified_appearsInBothLists() throws Exception {
            Path wt = manager.createWorktree("task-12", "task/task-12/dual");

            Path file = wt.resolve("dual.txt");
            Files.writeString(file, "first");
            run(wt, "git", "add", "dual.txt");
            Files.writeString(file, "second");

            com.tddforge.git.GitStatus status = manager.status(wt);

            assertThat(status.stagedFiles()).contains("dual.txt");
            assertThat(status.unstagedFiles()).contains("dual.txt");
        }
    }

    @Nested
    class ChangedFiles {

        @Test
        void returnsEmptyListOnCleanWorktree() {
            Path wt = manager.createWorktree("task-20", "task/task-20/clean-files");

            List<String> files = manager.changedFiles(wt);

            assertThat(files).isEmpty();
        }

        @Test
        void detectsNewFiles() throws Exception {
            Path wt = manager.createWorktree("task-21", "task/task-21/new-files");

            Files.writeString(wt.resolve("added.txt"), "content");

            List<String> files = manager.changedFiles(wt);

            assertThat(files).contains("added.txt");
        }

        @Test
        void detectsStagedFiles() throws Exception {
            Path wt = manager.createWorktree("task-22", "task/task-22/staged-files");

            Files.writeString(wt.resolve("staged.txt"), "content");
            run(wt, "git", "add", "staged.txt");

            List<String> files = manager.changedFiles(wt);

            assertThat(files).contains("staged.txt");
        }
    }

    @Nested
    class RemoveWorktree {

        @Test
        void removesExistingWorktree() {
            Path wt = manager.createWorktree("task-30", "task/task-30/to-remove");

            assertThat(Files.exists(wt)).isTrue();

            manager.removeWorktree(wt);

            assertThat(Files.exists(wt)).isFalse();
        }

        @Test
        void skipsRemovalIfPathDoesNotExist() {
            Path fakePath = worktreeDir.resolve("nonexistent-task");

            manager.removeWorktree(fakePath);
        }

        @Test
        void rejectsPathOutsideWorktreeDir() {
            Path outsidePath = tempDir.resolve("outside");

            assertThatThrownBy(() -> manager.removeWorktree(outsidePath))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("not under configured worktree_dir");
        }

        @Test
        void rejectsWorktreeDirItself() {
            assertThatThrownBy(() -> manager.removeWorktree(worktreeDir))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("Cannot operate on the worktree_dir itself");
        }

        @Test
        void rejectsNullPath() {
            assertThatThrownBy(() -> manager.removeWorktree(null))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    @Nested
    class DeleteBranch {

        @Test
        void deletesLocalBranch() {
            Path wt = manager.createWorktree("task-40", "task/task-40/to-delete");
            manager.removeWorktree(wt);

            manager.deleteBranch("task/task-40/to-delete");

            com.tddforge.git.GitCommandResult branches = manager.runGit(localRepo, "branch", "--list", "task/task-40/to-delete");
            assertThat(branches.stdout().trim()).isEmpty();
        }

        @Test
        void throwsOnNonexistentBranch() {
            assertThatThrownBy(() -> manager.deleteBranch("nonexistent-branch"))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("Failed to delete branch");
        }

        @Test
        void rejectsBlankBranchName() {
            assertThatThrownBy(() -> manager.deleteBranch(""))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("must not be null or blank");
        }
    }

    @Nested
    class Publish {

        @Test
        void publishesBranchToRemote() throws Exception {
            Path wt = manager.createWorktree("task-50", "task/task-50/publish-me");
            Files.writeString(wt.resolve("publish.txt"), "data");
            run(wt, "git", "add", ".");
            run(wt, "git", "config", "user.email", "test@test.com");
            run(wt, "git", "config", "user.name", "Test");
            run(wt, "git", "commit", "-m", "publish commit");

            String output = manager.publish("task/task-50/publish-me");

            assertThat(output).isNotNull();

            com.tddforge.git.GitCommandResult lsRemote = manager.runGit(localRepo, "ls-remote", "--heads", "origin", "task/task-50/publish-me");
            assertThat(lsRemote.stdout()).contains("task/task-50/publish-me");
        }

        @Test
        void rejectsBlankBranchName() {
            assertThatThrownBy(() -> manager.publish(""))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("must not be null or blank");
        }
    }

    @Nested
    class GenerateBranchName {

        @Test
        void generatesBranchFromTaskIdAndTitle() {
            String branch = manager.generateBranchName("task-123", "Fix Login Bug");

            assertThat(branch).isEqualTo("task/task-123/fix-login-bug");
        }

        @Test
        void handlesSpecialCharacters() {
            String branch = manager.generateBranchName("t-1", "Fix: user's @login! bug");

            assertThat(branch).startsWith("task/t-1/");
            assertThat(branch).doesNotContain("!");
            assertThat(branch).doesNotContain("@");
            assertThat(branch).doesNotContain("'");
        }

        @Test
        void fallsBackWhenTitleHasNoAsciiSlugCharacters() {
            String branch = manager.generateBranchName("task-zh", "中文任务");

            assertThat(branch).isEqualTo("task/task-zh/untitled");
        }

        @Test
        void truncatesLongNames() {
            String longTitle = "This is a very long task title that should be truncated to fit within the branch name length limit";
            String branch = manager.generateBranchName("task-999", longTitle);

            assertThat(branch.length()).isLessThanOrEqualTo(63);
        }
    }

    @Nested
    class PathValidation {

        @Test
        void rejectsPathTraversal() {
            Path traversalPath = worktreeDir.resolve("task-1").resolve("..").resolve("..").resolve("etc");

            assertThatThrownBy(() -> manager.validatePathInWorktreeDir(traversalPath))
                    .isInstanceOf(WorktreeManagerException.class)
                    .hasMessageContaining("not under configured worktree_dir");
        }

        @Test
        void acceptsValidPath() {
            Path validPath = worktreeDir.resolve("task-1");

            manager.validatePathInWorktreeDir(validPath);
        }
    }

    @Nested
    class FetchBaseBranch {

        @Test
        void fetchesBaseBranch() {
            manager.fetchBaseBranch();

            com.tddforge.git.GitCommandResult result = manager.runGit(localRepo, "branch", "-r");
            assertThat(result.stdout()).contains("origin/master");
        }
    }

    @Nested
    class CommandResultTests {

        @Test
        void successReturnsTrueForZeroExitCode() {
            com.tddforge.git.GitCommandResult result = new com.tddforge.git.GitCommandResult(0, "output", "", 100, List.of("git", "status"));

            assertThat(result.success()).isTrue();
        }

        @Test
        void successReturnsFalseForNonZeroExitCode() {
            com.tddforge.git.GitCommandResult result = new com.tddforge.git.GitCommandResult(1, "", "error", 100, List.of("git", "bad"));

            assertThat(result.success()).isFalse();
        }

        @Test
        void combinedOutputMergesStdoutAndStderr() {
            com.tddforge.git.GitCommandResult result = new com.tddforge.git.GitCommandResult(0, "out", "err", 100, List.of());

            assertThat(result.combinedOutput()).contains("out");
            assertThat(result.combinedOutput()).contains("err");
        }
    }

    @Nested
    class StatusRecordTests {

        @Test
        void isCleanReturnsTrueWhenNoChanges() {
            com.tddforge.git.GitStatus status = new com.tddforge.git.GitStatus("main", "", List.of(), List.of(), List.of());

            assertThat(status.isClean()).isTrue();
        }

        @Test
        void isCleanReturnsFalseWhenStagedFiles() {
            com.tddforge.git.GitStatus status = new com.tddforge.git.GitStatus("main", "", List.of("file.txt"), List.of(), List.of());

            assertThat(status.isClean()).isFalse();
        }

        @Test
        void allChangedFilesDeduplicates() {
            com.tddforge.git.GitStatus status = new com.tddforge.git.GitStatus("main", "",
                    List.of("file.txt"), List.of("file.txt"), List.of("other.txt"));

            assertThat(status.allChangedFiles()).containsExactlyInAnyOrder("file.txt", "other.txt");
        }
    }

    private int run(Path dir, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IOException("Command failed (" + exit + "): " + String.join(" ", cmd) + "\n" + output);
        }
        return exit;
    }
}
