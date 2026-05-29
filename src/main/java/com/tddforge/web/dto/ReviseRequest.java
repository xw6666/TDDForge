package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ReviseRequest(
        @NotBlank(message = "feedback must not be blank")
        @JsonProperty("feedback") String feedback
) {
}
