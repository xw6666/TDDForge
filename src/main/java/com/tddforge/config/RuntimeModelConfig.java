package com.tddforge.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeModelConfig {

    private final OpencodeConfig opencodeConfig;
    private volatile ModelConfigSnapshot snapshot;

    public RuntimeModelConfig(OpencodeConfig opencodeConfig) {
        this.opencodeConfig = opencodeConfig;
    }

    @PostConstruct
    public void init() {
        this.snapshot = buildSnapshot(opencodeConfig);
    }

    public void initFrom(OpencodeConfig source) {
        this.snapshot = buildSnapshot(source);
    }

    public ModelConfigSnapshot snapshot() {
        return snapshot;
    }

    public void replaceAll(ModelConfigSnapshot newSnapshot) {
        this.snapshot = newSnapshot;
    }

    public void setPlanner(ModelSpec planner) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                planner, prev.testWriter(), prev.testReviewer(), prev.coderDefault(),
                prev.coderByComplexity(), prev.reviewers());
    }

    public void setTestWriter(ModelSpec testWriter) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                prev.planner(), testWriter, prev.testReviewer(), prev.coderDefault(),
                prev.coderByComplexity(), prev.reviewers());
    }

    public void setTestReviewer(ModelSpec testReviewer) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                prev.planner(), prev.testWriter(), testReviewer, prev.coderDefault(),
                prev.coderByComplexity(), prev.reviewers());
    }

    public void setCoderDefault(ModelSpec coderDefault) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                prev.planner(), prev.testWriter(), prev.testReviewer(), coderDefault,
                prev.coderByComplexity(), prev.reviewers());
    }

    public void setCoderByComplexity(Map<String, ModelSpec> coderByComplexity) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                prev.planner(), prev.testWriter(), prev.testReviewer(), prev.coderDefault(),
                coderByComplexity, prev.reviewers());
    }

    public void setReviewers(List<ModelSpec> reviewers) {
        ModelConfigSnapshot prev = this.snapshot;
        this.snapshot = new ModelConfigSnapshot(
                prev.planner(), prev.testWriter(), prev.testReviewer(), prev.coderDefault(),
                prev.coderByComplexity(), reviewers);
    }

    private static ModelConfigSnapshot buildSnapshot(OpencodeConfig source) {
        return new ModelConfigSnapshot(
                source.getPlanner(),
                source.getTestWriter(),
                source.getTestReviewer(),
                source.getCoderDefault(),
                source.getCoderByComplexity(),
                source.getReviewers()
        );
    }
}
