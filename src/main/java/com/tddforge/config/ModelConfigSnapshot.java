package com.tddforge.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ModelConfigSnapshot(
        ModelSpec planner,
        ModelSpec testWriter,
        ModelSpec testReviewer,
        ModelSpec coderDefault,
        Map<String, ModelSpec> coderByComplexity,
        List<ModelSpec> reviewers
) {
    public ModelConfigSnapshot {
        planner = copySpec(planner);
        testWriter = copySpec(testWriter);
        testReviewer = copySpec(testReviewer);
        coderDefault = copySpec(coderDefault);
        coderByComplexity = coderByComplexity != null
                ? Collections.unmodifiableMap(copySpecMap(coderByComplexity))
                : Collections.emptyMap();
        reviewers = reviewers != null
                ? Collections.unmodifiableList(copySpecList(reviewers))
                : Collections.emptyList();
    }

    private static ModelSpec copySpec(ModelSpec source) {
        if (source == null) return null;
        ModelSpec copy = new ModelSpec();
        copy.setModel(source.getModel());
        copy.setVariant(source.getVariant());
        copy.setAgent(source.getAgent());
        return copy;
    }

    private static Map<String, ModelSpec> copySpecMap(Map<String, ModelSpec> source) {
        Map<String, ModelSpec> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> copy.put(k, copySpec(v)));
        return copy;
    }

    private static List<ModelSpec> copySpecList(List<ModelSpec> source) {
        return source.stream().map(ModelConfigSnapshot::copySpec).toList();
    }
}
