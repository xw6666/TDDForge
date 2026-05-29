package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message,
        @JsonProperty("taskId") String taskId
) {
    public static OperationResponse success(String taskId, String message) {
        return new OperationResponse(true, message, taskId);
    }

    public static OperationResponse failure(String taskId, String message) {
        return new OperationResponse(false, message, taskId);
    }
}
