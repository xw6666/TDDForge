package com.tddforge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddforge.service.ModelConfigService;
import com.tddforge.web.dto.ModelConfigResponse;
import com.tddforge.web.dto.ModelConfigUpdateRequest;
import com.tddforge.web.dto.ModelSpecDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ModelConfigController.class)
class ModelConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModelConfigService modelConfigService;

    @Nested
    class ListModels {

        @Test
        void shouldReturnModelList() throws Exception {
            when(modelConfigService.listAvailableModels())
                    .thenReturn(List.of("anthropic/claude-3", "openai/gpt-3.5-turbo", "openai/gpt-4"));

            mockMvc.perform(get("/api/models"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models", hasSize(3)))
                    .andExpect(jsonPath("$.models[0]").value("anthropic/claude-3"))
                    .andExpect(jsonPath("$.models[1]").value("openai/gpt-3.5-turbo"))
                    .andExpect(jsonPath("$.models[2]").value("openai/gpt-4"));
        }

        @Test
        void shouldReturnEmptyListWhenNoModels() throws Exception {
            when(modelConfigService.listAvailableModels()).thenReturn(List.of());

            mockMvc.perform(get("/api/models"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.models", hasSize(0)));
        }

        @Test
        void shouldReturn500WhenOpencodeFails() throws Exception {
            when(modelConfigService.listAvailableModels())
                    .thenThrow(new RuntimeException("opencode not found"));

            mockMvc.perform(get("/api/models"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value(containsString("opencode not found")));
        }
    }

    @Nested
    class GetConfig {

        @Test
        void shouldReturnCurrentConfig() throws Exception {
            ModelConfigResponse config = new ModelConfigResponse(
                    new ModelSpecDto("planner-m", "pv", "pa"),
                    new ModelSpecDto("tw-m", "", ""),
                    new ModelSpecDto("tr-m", "", ""),
                    new ModelSpecDto("coder-m", "cv", "ca"),
                    Map.of("simple", new ModelSpecDto("simple-m", "", "")),
                    List.of(new ModelSpecDto("reviewer-m", "rv", "ra"))
            );
            when(modelConfigService.getCurrentConfig()).thenReturn(config);

            mockMvc.perform(get("/api/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planner.model").value("planner-m"))
                    .andExpect(jsonPath("$.planner.variant").value("pv"))
                    .andExpect(jsonPath("$.testWriter.model").value("tw-m"))
                    .andExpect(jsonPath("$.testReviewer.model").value("tr-m"))
                    .andExpect(jsonPath("$.coderDefault.model").value("coder-m"))
                    .andExpect(jsonPath("$.coderByComplexity.simple.model").value("simple-m"))
                    .andExpect(jsonPath("$.reviewers", hasSize(1)))
                    .andExpect(jsonPath("$.reviewers[0].model").value("reviewer-m"));
        }
    }

    @Nested
    class UpdateConfig {

        @Test
        void shouldUpdateConfig() throws Exception {
            doNothing().when(modelConfigService).updateConfig(any(ModelConfigUpdateRequest.class));

            mockMvc.perform(post("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "planner": {"model": "new-planner", "variant": "", "agent": ""},
                                        "test_writer": {"model": "new-tw", "variant": "", "agent": ""}
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Model configuration updated"));

            verify(modelConfigService).updateConfig(any(ModelConfigUpdateRequest.class));
        }

        @Test
        void shouldHandlePartialUpdate() throws Exception {
            doNothing().when(modelConfigService).updateConfig(any(ModelConfigUpdateRequest.class));

            mockMvc.perform(post("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "planner": {"model": "only-planner", "variant": "", "agent": ""}
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
