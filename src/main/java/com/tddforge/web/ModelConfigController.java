package com.tddforge.web;

import com.tddforge.service.ModelConfigService;
import com.tddforge.web.dto.ModelConfigResponse;
import com.tddforge.web.dto.ModelConfigUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ModelConfigController {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigController.class);

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        try {
            List<String> models = modelConfigService.listAvailableModels();
            return ResponseEntity.ok(Map.of("models", models));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<ModelConfigResponse> getConfig() {
        return ResponseEntity.ok(modelConfigService.getCurrentConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody ModelConfigUpdateRequest request) {
        try {
            modelConfigService.updateConfig(request);
            return ResponseEntity.ok(Map.of("success", true, "message", "Model configuration updated"));
        } catch (RuntimeException e) {
            log.error("Failed to update model configuration", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
