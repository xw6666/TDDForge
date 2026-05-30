package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tddforge.service.ResourceSnapshot;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemStatusResponse(
        @JsonProperty("started") boolean started,
        @JsonProperty("runningTasks") int runningTasks,
        @JsonProperty("pendingTasks") int pendingTasks,
        @JsonProperty("maxParallelTasks") int maxParallelTasks,
        @JsonProperty("resourceSnapshot") List<ResourceSnapshot.TaskResourceStatus> resourceSnapshot
) {
}
