package com.tddforge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDefaultsTest {

    @Test
    void orchestratorConfigDefaults() {
        OrchestratorConfig config = new OrchestratorConfig();
        assertThat(config.getMaxParallelTasks()).isEqualTo(3);
        assertThat(config.getMaxTestRetries()).isEqualTo(2);
        assertThat(config.getMaxCodeRetries()).isEqualTo(4);
        assertThat(config.getPollIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void publishConfigDefaults() {
        PublishConfig config = new PublishConfig();
        assertThat(config.getRemote()).isEqualTo("origin");
    }

    @Test
    void repoConfigDefaults() {
        RepoConfig config = new RepoConfig();
        assertThat(config.getBaseBranch()).isEqualTo("master");
        assertThat(config.getPath()).isNull();
        assertThat(config.getWorktreeDir()).isNull();
        assertThat(config.getWorktreeHooks()).isEmpty();
    }

    @Test
    void opencodeConfigDefaults() {
        OpencodeConfig config = new OpencodeConfig();
        assertThat(config.getTimeoutSeconds()).isEqualTo(3600);
        assertThat(config.getMaxContinues()).isEqualTo(8);
        assertThat(config.getPlanner()).isNull();
        assertThat(config.getTestWriter()).isNull();
        assertThat(config.getTestReviewer()).isNull();
        assertThat(config.getCoderDefault()).isNull();
        assertThat(config.getCoderByComplexity()).isEmpty();
        assertThat(config.getReviewers()).isEmpty();
    }

    @Test
    void configModelSpecDefaults() {
        ModelSpec spec = new ModelSpec();
        assertThat(spec.getModel()).isNull();
        assertThat(spec.getVariant()).isEqualTo("");
        assertThat(spec.getAgent()).isEqualTo("");
    }

    @Test
    void orchestratorConfigSettersOverrideDefaults() {
        OrchestratorConfig config = new OrchestratorConfig();
        config.setMaxParallelTasks(10);
        config.setMaxTestRetries(5);
        config.setMaxCodeRetries(8);
        config.setPollIntervalSeconds(60);

        assertThat(config.getMaxParallelTasks()).isEqualTo(10);
        assertThat(config.getMaxTestRetries()).isEqualTo(5);
        assertThat(config.getMaxCodeRetries()).isEqualTo(8);
        assertThat(config.getPollIntervalSeconds()).isEqualTo(60);
    }
}
