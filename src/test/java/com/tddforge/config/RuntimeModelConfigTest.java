package com.tddforge.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeModelConfigTest {

    @Test
    void shouldInitializeFromOpencodeConfig() {
        OpencodeConfig source = new OpencodeConfig();
        source.setConfigPath("/tmp/test.json");
        source.setPlanner(createSpec("planner-m", "pv", "pa"));
        source.setTestWriter(createSpec("tw-m", "", ""));
        source.setTestReviewer(createSpec("tr-m", "", ""));
        source.setCoderDefault(createSpec("coder-m", "cv", "ca"));
        source.getCoderByComplexity().put("simple", createSpec("simple-m", "", ""));
        source.getReviewers().add(createSpec("reviewer-m", "rv", "ra"));

        RuntimeModelConfig config = new RuntimeModelConfig(source);
        config.init();

        ModelConfigSnapshot snap = config.snapshot();
        assertThat(snap.planner().getModel()).isEqualTo("planner-m");
        assertThat(snap.planner().getVariant()).isEqualTo("pv");
        assertThat(snap.testWriter().getModel()).isEqualTo("tw-m");
        assertThat(snap.testReviewer().getModel()).isEqualTo("tr-m");
        assertThat(snap.coderDefault().getModel()).isEqualTo("coder-m");
        assertThat(snap.coderByComplexity()).containsKey("simple");
        assertThat(snap.reviewers()).hasSize(1);
        assertThat(snap.reviewers().get(0).getModel()).isEqualTo("reviewer-m");
    }

    @Test
    void snapshotShouldBeImmutable() {
        OpencodeConfig source = new OpencodeConfig();
        source.setConfigPath("/tmp/test.json");
        source.setPlanner(createSpec("original", "", ""));

        RuntimeModelConfig config = new RuntimeModelConfig(source);
        config.init();

        ModelConfigSnapshot snap1 = config.snapshot();
        config.setPlanner(createSpec("modified", "", ""));
        ModelConfigSnapshot snap2 = config.snapshot();

        assertThat(snap1.planner().getModel()).isEqualTo("original");
        assertThat(snap2.planner().getModel()).isEqualTo("modified");
    }

    @Test
    void shouldUpdatePlanner() {
        OpencodeConfig source = new OpencodeConfig();
        source.setConfigPath("/tmp/test.json");
        source.setPlanner(createSpec("old", "", ""));

        RuntimeModelConfig config = new RuntimeModelConfig(source);
        config.init();
        config.setPlanner(createSpec("new-planner", "v", "a"));

        assertThat(config.snapshot().planner().getModel()).isEqualTo("new-planner");
        assertThat(config.snapshot().planner().getVariant()).isEqualTo("v");
    }

    @Test
    void shouldUpdateCoderByComplexity() {
        OpencodeConfig source = new OpencodeConfig();
        source.setConfigPath("/tmp/test.json");
        source.setPlanner(createSpec("p", "", ""));

        RuntimeModelConfig config = new RuntimeModelConfig(source);
        config.init();
        config.setCoderByComplexity(Map.of(
                "simple", createSpec("simple-m", "", ""),
                "complex", createSpec("complex-m", "", "")
        ));

        assertThat(config.snapshot().coderByComplexity()).hasSize(2);
        assertThat(config.snapshot().coderByComplexity().get("simple").getModel()).isEqualTo("simple-m");
    }

    @Test
    void shouldUpdateReviewers() {
        OpencodeConfig source = new OpencodeConfig();
        source.setConfigPath("/tmp/test.json");
        source.setPlanner(createSpec("p", "", ""));

        RuntimeModelConfig config = new RuntimeModelConfig(source);
        config.init();
        config.setReviewers(List.of(createSpec("r1", "", ""), createSpec("r2", "", "")));

        assertThat(config.snapshot().reviewers()).hasSize(2);
    }

    @Test
    void initFromShouldReplaceAllValues() {
        OpencodeConfig source1 = new OpencodeConfig();
        source1.setConfigPath("/tmp/test1.json");
        source1.setPlanner(createSpec("planner-1", "", ""));
        source1.setTestWriter(createSpec("tw-1", "", ""));
        source1.setCoderDefault(createSpec("coder-1", "", ""));

        OpencodeConfig source2 = new OpencodeConfig();
        source2.setConfigPath("/tmp/test2.json");
        source2.setPlanner(createSpec("planner-2", "", ""));
        source2.setTestWriter(createSpec("tw-2", "", ""));
        source2.setCoderDefault(createSpec("coder-2", "", ""));

        RuntimeModelConfig config = new RuntimeModelConfig(source1);
        config.init();
        config.initFrom(source2);

        ModelConfigSnapshot snap = config.snapshot();
        assertThat(snap.planner().getModel()).isEqualTo("planner-2");
        assertThat(snap.testWriter().getModel()).isEqualTo("tw-2");
        assertThat(snap.coderDefault().getModel()).isEqualTo("coder-2");
    }

    private ModelSpec createSpec(String model, String variant, String agent) {
        ModelSpec spec = new ModelSpec();
        spec.setModel(model);
        spec.setVariant(variant);
        spec.setAgent(agent);
        return spec;
    }
}
