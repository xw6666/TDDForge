package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 512, message = "title must not exceed 512 characters")
        @JsonProperty("title") String title,

        @NotBlank(message = "description must not be blank")
        @JsonProperty("description") String description,

        @JsonProperty("priority") String priority,

        @JsonProperty("forceNoSplit") Boolean forceNoSplit
) {
}
