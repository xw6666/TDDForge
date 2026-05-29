package com.tddforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TddForgePropertiesTest {

    @MockBean
    private ConfigValidator configValidator;

    @Autowired
    private RepoConfig repoConfig;

    @Autowired
    private OpencodeConfig opencodeConfig;

    @Autowired
    private OrchestratorConfig orchestratorConfig;

    @Autowired
    private MysqlConfig mysqlConfig;

    @Autowired
    private PublishConfig publishConfig;

    @Test
    void repoConfigBinding() {
        assertThat(repoConfig.getPath()).isEqualTo("/tmp/test-repo");
        assertThat(repoConfig.getBaseBranch()).isEqualTo("main");
        assertThat(repoConfig.getWorktreeDir()).isEqualTo("/tmp/test-worktrees");
        assertThat(repoConfig.getWorktreeHooks()).containsExactly("pre-create", "post-remove");
    }

    @Test
    void opencodeConfigBinding() {
        assertThat(opencodeConfig.getConfigPath()).isEqualTo("/tmp/test-opencode.json");
        assertThat(opencodeConfig.getTimeoutSeconds()).isEqualTo(1800);
        assertThat(opencodeConfig.getMaxContinues()).isEqualTo(5);
    }

    @Test
    void opencodePlannerModelSpecBinding() {
        ModelSpec planner = opencodeConfig.getPlanner();
        assertThat(planner.getModel()).isEqualTo("test-planner-model");
        assertThat(planner.getVariant()).isEqualTo("planner-v1");
        assertThat(planner.getAgent()).isEqualTo("planner-agent");
    }

    @Test
    void opencodeTestWriterModelSpecBinding() {
        ModelSpec spec = opencodeConfig.getTestWriter();
        assertThat(spec.getModel()).isEqualTo("test-writer-model");
        assertThat(spec.getVariant()).isEqualTo("");
        assertThat(spec.getAgent()).isEqualTo("");
    }

    @Test
    void opencodeTestReviewerModelSpecBinding() {
        ModelSpec spec = opencodeConfig.getTestReviewer();
        assertThat(spec.getModel()).isEqualTo("test-reviewer-model");
    }

    @Test
    void opencodeCoderDefaultModelSpecBinding() {
        ModelSpec spec = opencodeConfig.getCoderDefault();
        assertThat(spec.getModel()).isEqualTo("test-coder-model");
        assertThat(spec.getVariant()).isEqualTo("coder-v2");
        assertThat(spec.getAgent()).isEqualTo("coder-agent");
    }

    @Test
    void opencodeCoderByComplexityBinding() {
        assertThat(opencodeConfig.getCoderByComplexity()).containsOnlyKeys("very_complex", "simple");
        assertThat(opencodeConfig.getCoderByComplexity().get("very_complex").getModel()).isEqualTo("complex-model");
        assertThat(opencodeConfig.getCoderByComplexity().get("simple").getModel()).isEqualTo("simple-model");
    }

    @Test
    void opencodeReviewersBinding() {
        assertThat(opencodeConfig.getReviewers()).hasSize(2);
        assertThat(opencodeConfig.getReviewers().get(0).getModel()).isEqualTo("reviewer-1-model");
        assertThat(opencodeConfig.getReviewers().get(0).getVariant()).isEqualTo("r1-variant");
        assertThat(opencodeConfig.getReviewers().get(1).getModel()).isEqualTo("reviewer-2-model");
        assertThat(opencodeConfig.getReviewers().get(1).getVariant()).isEqualTo("");
    }

    @Test
    void orchestratorConfigBinding() {
        assertThat(orchestratorConfig.getMaxParallelTasks()).isEqualTo(5);
        assertThat(orchestratorConfig.getMaxTestRetries()).isEqualTo(3);
        assertThat(orchestratorConfig.getMaxCodeRetries()).isEqualTo(6);
        assertThat(orchestratorConfig.getPollIntervalSeconds()).isEqualTo(15);
    }

    @Test
    void mysqlConfigBinding() {
        assertThat(mysqlConfig.getUrl()).isEqualTo("jdbc:mysql://localhost:3306/testdb");
        assertThat(mysqlConfig.getUsername()).isEqualTo("test_user");
        assertThat(mysqlConfig.getPassword()).isEqualTo("test_pass");
    }

    @Test
    void publishConfigBinding() {
        assertThat(publishConfig.getRemote()).isEqualTo("test-remote");
    }
}
