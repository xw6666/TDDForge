package com.tddforge.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties("repo")
public class RepoConfig {

    @NotBlank(message = "repo.path must not be blank")
    private String path;

    @NotBlank(message = "repo.base-branch must not be blank")
    private String baseBranch = "master";

    @NotBlank(message = "repo.worktree-dir must not be blank")
    private String worktreeDir;

    private List<String> worktreeHooks = new ArrayList<>();

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getWorktreeDir() {
        return worktreeDir;
    }

    public void setWorktreeDir(String worktreeDir) {
        this.worktreeDir = worktreeDir;
    }

    public List<String> getWorktreeHooks() {
        return worktreeHooks;
    }

    public void setWorktreeHooks(List<String> worktreeHooks) {
        this.worktreeHooks = worktreeHooks;
    }
}
