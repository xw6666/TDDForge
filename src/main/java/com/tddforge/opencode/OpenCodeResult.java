package com.tddforge.opencode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenCodeResult(
        @Nullable @JsonProperty("sessionId") String sessionId,
        @JsonProperty("text") String text,
        @Nullable @JsonProperty("lastStopStepText") String lastStopStepText,
        @JsonProperty("readableSteps") List<String> readableSteps,
        @JsonProperty("toolCalls") List<ToolCallSummary> toolCalls,
        @JsonProperty("complete") boolean complete
) {

    public OpenCodeResult {
        if (text == null) text = "";
        if (readableSteps == null) readableSteps = List.of();
        if (toolCalls == null) toolCalls = List.of();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallSummary(
            @Nullable @JsonProperty("toolName") String toolName,
            @Nullable @JsonProperty("toolInputSummary") String toolInputSummary
    ) {
        public ToolCallSummary {
            if (toolName == null) toolName = "";
            if (toolInputSummary == null) toolInputSummary = "";
        }
    }
}
