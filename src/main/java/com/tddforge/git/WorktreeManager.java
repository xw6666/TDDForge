package com.tddforge.git;

import com.tddforge.config.PublishConfig;
import com.tddforge.config.RepoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class WorktreeManager {

    private static final Logger log = LoggerFactory.getLogger(WorktreeManager.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;
    private static final Pattern SAFE_BRANCH_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._/-]{0,62}$");

    private final RepoConfig repoConfig;
    private final PublishConfig publishConfig;
    private final long timeoutSeconds;

    public WorktreeManager(RepoConfig repoConfig, PublishConfig publishConfig) {
        this(repoConfig, publishConfig, DEFAULT_TIMEOUT_SECONDS);
    }

    public WorktreeManager(RepoConfig repoConfig, PublishConfig publishConfig, long timeoutSeconds) {
        this.repoConfig = repoConfig;
        this.publishConfig = publishConfig;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Path createWorktree(String taskId, String branchName) {
        validateBranchName(branchName);
        Path worktreeDir = resolveWorktreePath(taskId);

        try {
            Files.createDirectories(worktreeDir.getParent());
        } catch (IOException e) {
            throw new WorktreeManagerException("Failed to create worktree parent directory: " + worktreeDir.getParent(), e);
        }

        fetchBaseBranch();

        Path repoPath = Path.of(repoConfig.getPath());
        String baseBranch = repoConfig.getBaseBranch();

        GitCommandResult result = runGit(repoPath,
                "worktree", "add", "-b", branchName, worktreeDir.toString(), "origin/" + baseBranch);

        if (!result.success()) {
            throw new WorktreeManagerException(
                    "Failed to create worktree for task " + taskId + ": " + result.combinedOutput(), result);
        }

        copyAgentsMd(repoPath, worktreeDir);
        copyHooks(repoPath, worktreeDir);
        executeHooks(worktreeDir);

        log.info("Created worktree for task {} at {}", taskId, worktreeDir);
        return worktreeDir;
    }

    public GitStatus status(Path worktreePath) {
        validatePathInWorktreeDir(worktreePath);

        GitCommandResult result = runGit(worktreePath, "status", "--short", "--branch");

        if (!result.success()) {
            throw new WorktreeManagerException("Failed to get status: " + result.combinedOutput(), result);
        }

        return parseStatus(result.stdout());
    }

    public List<String> changedFiles(Path worktreePath) {
        validatePathInWorktreeDir(worktreePath);

        GitCommandResult statusResult = runGit(worktreePath, "status", "--short", "--porcelain");
        if (!statusResult.success()) {
            throw new WorktreeManagerException("Failed to get changed files: " + statusResult.combinedOutput(), statusResult);
        }

        return parseChangedFiles(statusResult.stdout());
    }

    public String currentHead(Path worktreePath) {
        validatePathInWorktreeDir(worktreePath);

        GitCommandResult result = runGit(worktreePath, "rev-parse", "HEAD");
        if (!result.success()) {
            throw new WorktreeManagerException("Failed to resolve HEAD: " + result.combinedOutput(), result);
        }
        return result.stdout();
    }

    public boolean hasCommitsSinceBase(Path worktreePath) {
        validatePathInWorktreeDir(worktreePath);

        String baseBranch = repoConfig.getBaseBranch();
        GitCommandResult result = runGit(worktreePath, "rev-list", "--count", "origin/" + baseBranch + "..HEAD");
        if (!result.success()) {
            throw new WorktreeManagerException("Failed to count worktree commits: " + result.combinedOutput(), result);
        }
        try {
            return Integer.parseInt(result.stdout().trim()) > 0;
        } catch (NumberFormatException e) {
            throw new WorktreeManagerException("Invalid git rev-list count: " + result.stdout(), e);
        }
    }

    public List<String> committedFiles(Path worktreePath, String commitHash) {
        validatePathInWorktreeDir(worktreePath);
        if (commitHash == null || commitHash.isBlank()) {
            throw new WorktreeManagerException("Commit hash must not be null or blank");
        }

        GitCommandResult result = runGit(worktreePath, "diff-tree", "--no-commit-id", "--name-only", "-r", commitHash);
        if (!result.success()) {
            throw new WorktreeManagerException("Failed to list committed files: " + result.combinedOutput(), result);
        }
        return parseLines(result.stdout());
    }

    public void removeWorktree(Path worktreePath) {
        validatePathInWorktreeDir(worktreePath);

        if (!Files.exists(worktreePath)) {
            log.warn("Worktree path does not exist, skipping removal: {}", worktreePath);
            return;
        }

        Path repoPath = Path.of(repoConfig.getPath());
        GitCommandResult result = runGit(repoPath, "worktree", "remove", "--force", worktreePath.toString());
        if (!result.success()) {
            throw new WorktreeManagerException(
                    "Failed to remove worktree at " + worktreePath + ": " + result.combinedOutput(), result);
        }

        runGit(repoPath, "worktree", "prune");
        log.info("Removed worktree at {}", worktreePath);
    }

    public void deleteBranch(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new WorktreeManagerException("Branch name must not be null or blank");
        }

        Path repoPath = Path.of(repoConfig.getPath());
        GitCommandResult result = runGit(repoPath, "branch", "-D", branchName);
        if (!result.success()) {
            throw new WorktreeManagerException(
                    "Failed to delete branch " + branchName + ": " + result.combinedOutput(), result);
        }

        log.info("Deleted branch {}", branchName);
    }

    public String publish(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new WorktreeManagerException("Branch name must not be null or blank");
        }

        Path repoPath = Path.of(repoConfig.getPath());
        String remote = publishConfig.getRemote();

        GitCommandResult result = runGit(repoPath, "push", "--force", "--set-upstream", remote, branchName);
        if (!result.success()) {
            throw new WorktreeManagerException(
                    "Failed to publish branch " + branchName + ": " + result.combinedOutput(), result);
        }

        log.info("Published branch {} to remote {}", branchName, remote);
        return result.stdout() + "\n" + result.stderr();
    }

    public String generateBranchName(String taskId, String title) {
        String slug = slugify(title);
        String branch = "task/" + taskId + "/" + slug;
        if (branch.length() > 63) {
            branch = branch.substring(0, 63);
        }
        return branch;
    }

    public boolean worktreeExists(Path worktreePath) {
        if (worktreePath == null) {
            return false;
        }
        return Files.isDirectory(worktreePath);
    }

    public boolean branchExists(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return false;
        }
        Path repoPath = Path.of(repoConfig.getPath());
        GitCommandResult result = runGit(repoPath, "rev-parse", "--verify", "refs/heads/" + branchName);
        return result.success();
    }

    public void fetchBaseBranch() {
        Path repoPath = Path.of(repoConfig.getPath());
        String baseBranch = repoConfig.getBaseBranch();

        GitCommandResult result = runGit(repoPath, "fetch", "origin", baseBranch);
        if (!result.success()) {
            throw new WorktreeManagerException(
                    "Failed to fetch base branch " + baseBranch + ": " + result.combinedOutput(), result);
        }
    }

    private Path resolveWorktreePath(String taskId) {
        String worktreeDir = repoConfig.getWorktreeDir();
        if (worktreeDir == null || worktreeDir.isBlank()) {
            throw new WorktreeManagerException("repo.worktreeDir must not be blank");
        }
        return Path.of(worktreeDir, taskId).toAbsolutePath().normalize();
    }

    private void validateBranchName(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new WorktreeManagerException("Branch name must not be null or blank");
        }
        if (!SAFE_BRANCH_PATTERN.matcher(branchName).matches()) {
            throw new WorktreeManagerException("Invalid branch name: " + branchName);
        }
    }

    void validatePathInWorktreeDir(Path path) {
        if (path == null) {
            throw new WorktreeManagerException("Path must not be null");
        }

        Path normalizedPath = path.toAbsolutePath().normalize();
        Path worktreeDir = Path.of(repoConfig.getWorktreeDir()).toAbsolutePath().normalize();

        if (!normalizedPath.startsWith(worktreeDir)) {
            throw new WorktreeManagerException(
                    "Path " + normalizedPath + " is not under configured worktree_dir " + worktreeDir);
        }

        if (normalizedPath.equals(worktreeDir)) {
            throw new WorktreeManagerException("Cannot operate on the worktree_dir itself: " + normalizedPath);
        }
    }

    private void copyAgentsMd(Path repoRoot, Path worktreePath) {
        Path source = repoRoot.resolve("AGENTS.md");
        if (Files.exists(source)) {
            try {
                Files.copy(source, worktreePath.resolve("AGENTS.md"), StandardCopyOption.REPLACE_EXISTING);
                log.debug("Copied AGENTS.md to {}", worktreePath);
            } catch (IOException e) {
                log.warn("Failed to copy AGENTS.md to {}: {}", worktreePath, e.getMessage());
            }
        }
    }

    private void copyHooks(Path repoRoot, Path worktreePath) {
        Path hooksSource = repoRoot.resolve("hooks");
        if (!Files.isDirectory(hooksSource)) {
            return;
        }

        Path hooksTarget = worktreePath.resolve("hooks");
        try {
            Files.createDirectories(hooksTarget);
            try (Stream<Path> files = Files.list(hooksSource)) {
                for (Path file : files.toList()) {
                    Path target = hooksTarget.resolve(file.getFileName());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    if (file.toFile().canRead()) {
                        target.toFile().setExecutable(true);
                    }
                }
            }
            log.debug("Copied hooks/ to {}", worktreePath);
        } catch (IOException e) {
            log.warn("Failed to copy hooks/ to {}: {}", worktreePath, e.getMessage());
        }
    }

    private void executeHooks(Path worktreePath) {
        List<String> hooks = repoConfig.getWorktreeHooks();
        if (hooks == null || hooks.isEmpty()) {
            return;
        }

        for (String hook : hooks) {
            if (hook == null || hook.isBlank()) {
                continue;
            }
            log.debug("Executing worktree hook in {}: {}", worktreePath, hook);
            GitCommandResult result = runCommand(worktreePath, List.of("sh", "-c", hook));
            if (!result.success()) {
                log.warn("Worktree hook failed in {}: {}", worktreePath, result.combinedOutput());
            }
        }
    }

    GitCommandResult runGit(Path workingDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        return runCommand(workingDir, command);
    }

    private GitCommandResult runCommand(Path workingDir, List<String> command) {
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            String stdout;
            String stderr;
            try {
                stdout = new String(process.getInputStream().readAllBytes());
                stderr = new String(process.getErrorStream().readAllBytes());
            } catch (IOException e) {
                process.destroyForcibly();
                throw new WorktreeManagerException("Failed to read command output", e);
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new WorktreeManagerException(
                        "Git command timed out after " + timeoutSeconds + "s: " + String.join(" ", command));
            }

            long duration = System.currentTimeMillis() - start;
            int exitCode = process.exitValue();

            return new GitCommandResult(exitCode, stdout.trim(), stderr.trim(), duration, command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorktreeManagerException("Git command interrupted: " + String.join(" ", command), e);
        } catch (IOException e) {
            throw new WorktreeManagerException("Failed to execute git command: " + String.join(" ", command), e);
        }
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "untitled";
        }
        String slug = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .chars()
                .limit(40)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return slug.isBlank() ? "untitled" : slug;
    }

    private GitStatus parseStatus(String output) {
        String branch = "";
        String tracking = "";
        List<String> staged = new ArrayList<>();
        List<String> unstaged = new ArrayList<>();
        List<String> untracked = new ArrayList<>();

        for (String line : output.split("\n")) {
            if (line.startsWith("## ")) {
                String branchInfo = line.substring(3);
                int arrowIdx = branchInfo.indexOf("...");
                if (arrowIdx >= 0) {
                    branch = branchInfo.substring(0, arrowIdx);
                    int bracketIdx = branchInfo.indexOf('[');
                    tracking = bracketIdx >= 0
                            ? branchInfo.substring(arrowIdx + 3, bracketIdx).trim()
                            : branchInfo.substring(arrowIdx + 3).trim();
                } else {
                    int bracketIdx = branchInfo.indexOf('[');
                    branch = bracketIdx >= 0 ? branchInfo.substring(0, bracketIdx).trim() : branchInfo.trim();
                }
            } else if (line.startsWith("?? ")) {
                untracked.add(line.substring(3).trim());
            } else if (line.length() >= 3) {
                char indexStatus = line.charAt(0);
                char workTreeStatus = line.charAt(1);
                String filePath = line.length() > 3 ? line.substring(3).trim() : "";

                if (indexStatus != ' ' && indexStatus != '?') {
                    staged.add(filePath);
                }
                if (workTreeStatus != ' ' && workTreeStatus != '?') {
                    unstaged.add(filePath);
                }
            }
        }

        return new GitStatus(branch, tracking, staged, unstaged, untracked);
    }

    private List<String> parseChangedFiles(String output) {
        List<String> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("##")) {
                continue;
            }
            if (line.startsWith("?? ")) {
                files.add(line.substring(3).trim());
            } else if (line.length() >= 3) {
                files.add(line.substring(3).trim());
            }
        }
        return files.stream().distinct().toList();
    }

    private List<String> parseLines(String output) {
        List<String> lines = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return lines;
        }
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
