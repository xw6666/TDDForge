package com.tddforge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties("opencode")
public class OpencodeConfig {

    @NotBlank(message = "opencode.config-path must not be blank")
    private String configPath;

    private int timeoutSeconds = 3600;

    private int maxContinues = 8;

    @Valid
    @NotNull(message = "opencode.planner must be configured")
    private ModelSpec planner;

    @Valid
    @NotNull(message = "opencode.test-writer must be configured")
    private ModelSpec testWriter;

    @Valid
    @NotNull(message = "opencode.test-reviewer must be configured")
    private ModelSpec testReviewer;

    @Valid
    @NotNull(message = "opencode.coder-default must be configured")
    private ModelSpec coderDefault;

    private Map<String, @Valid ModelSpec> coderByComplexity = new LinkedHashMap<>();

    @Valid
    private List<ModelSpec> reviewers = new ArrayList<>();

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxContinues() {
        return maxContinues;
    }

    public void setMaxContinues(int maxContinues) {
        this.maxContinues = maxContinues;
    }

    public ModelSpec getPlanner() {
        return planner;
    }

    public void setPlanner(ModelSpec planner) {
        this.planner = planner;
    }

    public ModelSpec getTestWriter() {
        return testWriter;
    }

    public void setTestWriter(ModelSpec testWriter) {
        this.testWriter = testWriter;
    }

    public ModelSpec getTestReviewer() {
        return testReviewer;
    }

    public void setTestReviewer(ModelSpec testReviewer) {
        this.testReviewer = testReviewer;
    }

    public ModelSpec getCoderDefault() {
        return coderDefault;
    }

    public void setCoderDefault(ModelSpec coderDefault) {
        this.coderDefault = coderDefault;
    }

    public Map<String, ModelSpec> getCoderByComplexity() {
        return coderByComplexity;
    }

    public void setCoderByComplexity(Map<String, ModelSpec> coderByComplexity) {
        this.coderByComplexity = coderByComplexity;
    }

    public List<ModelSpec> getReviewers() {
        return reviewers;
    }

    public void setReviewers(List<ModelSpec> reviewers) {
        this.reviewers = reviewers;
    }
}
