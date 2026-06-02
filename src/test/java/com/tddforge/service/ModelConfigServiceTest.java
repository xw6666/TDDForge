package com.tddforge.service;

import com.tddforge.config.ModelSpec;
import com.tddforge.config.OpencodeConfig;
import com.tddforge.config.RuntimeModelConfig;
import com.tddforge.web.dto.ModelConfigResponse;
import com.tddforge.web.dto.ModelConfigUpdateRequest;
import com.tddforge.web.dto.ModelSpecDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelConfigServiceTest {

    private RuntimeModelConfig runtimeModelConfig;
    private OpencodeConfig opencodeConfig;
    private ModelConfigService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        opencodeConfig = new OpencodeConfig();
        opencodeConfig.setConfigPath("/tmp/test-opencode.json");
        opencodeConfig.setPlanner(createSpec("planner-model", "pv1", "pa"));
        opencodeConfig.setTestWriter(createSpec("tw-model", "", ""));
        opencodeConfig.setTestReviewer(createSpec("tr-model", "", ""));
        opencodeConfig.setCoderDefault(createSpec("coder-model", "cv2", "ca"));
        opencodeConfig.getCoderByComplexity().put("simple", createSpec("simple-model", "", ""));
        opencodeConfig.getCoderByComplexity().put("complex", createSpec("complex-model", "", ""));
        opencodeConfig.getReviewers().add(createSpec("reviewer-1", "r1v", "r1a"));
        opencodeConfig.getReviewers().add(createSpec("reviewer-2", "", ""));

        runtimeModelConfig = new RuntimeModelConfig(opencodeConfig);
        runtimeModelConfig.init();

        service = new ModelConfigService(runtimeModelConfig, opencodeConfig);
        service.setConfigDir(tempDir.toString());
    }

    private ModelSpec createSpec(String model, String variant, String agent) {
        ModelSpec spec = new ModelSpec();
        spec.setModel(model);
        spec.setVariant(variant);
        spec.setAgent(agent);
        return spec;
    }

    @Nested
    class GetCurrentConfig {

        @Test
        void shouldReturnCurrentModelConfig() {
            ModelConfigResponse config = service.getCurrentConfig();

            assertThat(config.getPlanner()).isNotNull();
            assertThat(config.getPlanner().getModel()).isEqualTo("planner-model");
            assertThat(config.getPlanner().getVariant()).isEqualTo("pv1");
            assertThat(config.getPlanner().getAgent()).isEqualTo("pa");

            assertThat(config.getTestWriter()).isNotNull();
            assertThat(config.getTestWriter().getModel()).isEqualTo("tw-model");

            assertThat(config.getTestReviewer()).isNotNull();
            assertThat(config.getTestReviewer().getModel()).isEqualTo("tr-model");

            assertThat(config.getCoderDefault()).isNotNull();
            assertThat(config.getCoderDefault().getModel()).isEqualTo("coder-model");

            assertThat(config.getCoderByComplexity()).isNotNull();
            assertThat(config.getCoderByComplexity()).containsKey("simple");
            assertThat(config.getCoderByComplexity()).containsKey("complex");

            assertThat(config.getReviewers()).hasSize(2);
            assertThat(config.getReviewers().get(0).getModel()).isEqualTo("reviewer-1");
        }
    }

    @Nested
    class UpdateConfig {

        @Test
        void shouldUpdatePlannerModel() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setPlanner(new ModelSpecDto("new-planner", "npv", "npa"));

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getPlanner().getModel()).isEqualTo("new-planner");
            assertThat(config.getPlanner().getVariant()).isEqualTo("npv");
            assertThat(config.getPlanner().getAgent()).isEqualTo("npa");
        }

        @Test
        void shouldUpdateCoderDefault() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setCoderDefault(new ModelSpecDto("new-coder", "", ""));

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getCoderDefault().getModel()).isEqualTo("new-coder");
        }

        @Test
        void shouldUpdateCoderByComplexity() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setCoderByComplexity(Map.of(
                    "simple", new ModelSpecDto("new-simple", "", ""),
                    "medium", new ModelSpecDto("new-medium", "", "")
            ));

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getCoderByComplexity()).containsKey("simple");
            assertThat(config.getCoderByComplexity()).containsKey("medium");
            assertThat(config.getCoderByComplexity()).doesNotContainKey("complex");
        }

        @Test
        void shouldUpdateReviewers() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setReviewers(List.of(
                    new ModelSpecDto("new-reviewer", "rv", "ra")
            ));

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getReviewers()).hasSize(1);
            assertThat(config.getReviewers().get(0).getModel()).isEqualTo("new-reviewer");
        }

        @Test
        void shouldNotAffectOtherFieldsWhenUpdatingOne() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setPlanner(new ModelSpecDto("changed-planner", "", ""));

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getPlanner().getModel()).isEqualTo("changed-planner");
            assertThat(config.getTestWriter().getModel()).isEqualTo("tw-model");
            assertThat(config.getCoderDefault().getModel()).isEqualTo("coder-model");
        }
    }

    @Nested
    class ListAvailableModels {

        @Test
        void shouldThrowWhenOpencodeBinaryNotFound() {
            ModelConfigService svc = new ModelConfigService(runtimeModelConfig, opencodeConfig);
            svc.setOpencodeBinary("/nonexistent/opencode-binary");
            svc.setConfigDir(tempDir.toString());

            assertThatThrownBy(() -> svc.listAvailableModels())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to run opencode models");
        }
    }

    @Nested
    class Persistence {

        @Test
        void shouldPersistConfigToDisk() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setPlanner(new ModelSpecDto("persisted-planner", "ppv", "ppa"));
            request.setTestWriter(new ModelSpecDto("persisted-tw", "", ""));
            request.setCoderDefault(new ModelSpecDto("persisted-coder", "", ""));
            request.setReviewers(List.of(new ModelSpecDto("persisted-reviewer", "", "")));

            service.updateConfig(request);

            Path configFile = tempDir.resolve("model-config.yaml");
            assertThat(Files.exists(configFile)).isTrue();
        }

        @Test
        void shouldReloadPersistedConfigOnNewServiceInstance() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            request.setPlanner(new ModelSpecDto("reloaded-planner", "rpv", "rpa"));
            request.setTestWriter(new ModelSpecDto("reloaded-tw", "", ""));
            request.setCoderDefault(new ModelSpecDto("reloaded-coder", "", ""));
            request.setReviewers(List.of(new ModelSpecDto("reloaded-reviewer", "", "")));

            service.updateConfig(request);

            RuntimeModelConfig freshRuntimeConfig = new RuntimeModelConfig(opencodeConfig);
            freshRuntimeConfig.init();
            ModelConfigService freshService = new ModelConfigService(freshRuntimeConfig, opencodeConfig);
            freshService.setConfigDir(tempDir.toString());
            freshService.init();

            ModelConfigResponse config = freshService.getCurrentConfig();
            assertThat(config.getPlanner().getModel()).isEqualTo("reloaded-planner");
            assertThat(config.getPlanner().getVariant()).isEqualTo("rpv");
            assertThat(config.getTestWriter().getModel()).isEqualTo("reloaded-tw");
            assertThat(config.getCoderDefault().getModel()).isEqualTo("reloaded-coder");
            assertThat(config.getReviewers()).hasSize(1);
            assertThat(config.getReviewers().get(0).getModel()).isEqualTo("reloaded-reviewer");
        }

        @Test
        void shouldHandleMissingPersistedFile() {
            RuntimeModelConfig freshConfig = new RuntimeModelConfig(opencodeConfig);
            freshConfig.init();
            ModelConfigService freshService = new ModelConfigService(freshConfig, opencodeConfig);
            freshService.setConfigDir(tempDir.resolve("empty-dir").toString());
            freshService.init();

            ModelConfigResponse config = freshService.getCurrentConfig();
            assertThat(config.getPlanner().getModel()).isEqualTo("planner-model");
        }
    }

    @Nested
    class ModelSpecDtoMapping {

        @Test
        void shouldHandleNullVariantAndAgent() {
            ModelConfigUpdateRequest request = new ModelConfigUpdateRequest();
            ModelSpecDto dto = new ModelSpecDto();
            dto.setModel("test-model");
            dto.setVariant(null);
            dto.setAgent(null);
            request.setPlanner(dto);

            service.updateConfig(request);

            ModelConfigResponse config = service.getCurrentConfig();
            assertThat(config.getPlanner().getModel()).isEqualTo("test-model");
            assertThat(config.getPlanner().getVariant()).isEmpty();
            assertThat(config.getPlanner().getAgent()).isEmpty();
        }
    }
}
