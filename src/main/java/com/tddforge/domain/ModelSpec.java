package com.tddforge.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelSpec(
        @JsonProperty("model") String model,
        @Nullable @JsonProperty("variant") String variant,
        @Nullable @JsonProperty("agent") String agent
) {

    public ModelSpec {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
    }
}
