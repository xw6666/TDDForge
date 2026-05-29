package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlannerResult(
        @JsonProperty("complexity") String complexity,
        @JsonProperty("split") boolean split,
        @JsonProperty("reason") String reason,
        @Nullable @JsonProperty("plan") String plan,
        @Nullable @JsonProperty("subTasks") List<SubTask> subTasks
) {

    public PlannerResult {
        if (complexity == null || complexity.isBlank()) {
            throw new IllegalArgumentException("complexity must not be null or blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubTask(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("priority") String priority,
            @Nullable @JsonProperty("dependsOn") List<Integer> dependsOn
    ) {

        public SubTask {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be null or blank");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description must not be null or blank");
            }
            if (priority == null || priority.isBlank()) {
                throw new IllegalArgumentException("priority must not be null or blank");
            }
        }
    }
}
