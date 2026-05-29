package com.tddforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orchestrator")
public class OrchestratorConfig {

    private int maxParallelTasks = 3;

    private int maxTestRetries = 2;

    private int maxCodeRetries = 4;

    private int pollIntervalSeconds = 30;

    public int getMaxParallelTasks() {
        return maxParallelTasks;
    }

    public void setMaxParallelTasks(int maxParallelTasks) {
        this.maxParallelTasks = maxParallelTasks;
    }

    public int getMaxTestRetries() {
        return maxTestRetries;
    }

    public void setMaxTestRetries(int maxTestRetries) {
        this.maxTestRetries = maxTestRetries;
    }

    public int getMaxCodeRetries() {
        return maxCodeRetries;
    }

    public void setMaxCodeRetries(int maxCodeRetries) {
        this.maxCodeRetries = maxCodeRetries;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
    }
}
