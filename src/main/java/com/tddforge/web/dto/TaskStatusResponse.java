package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tddforge.domain.TaskStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStatusResponse(
        @JsonProperty("taskId") String taskId,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("error") String error
) {
}
