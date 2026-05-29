package com.tddforge.opencode;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddforge.domain.OpenCodeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class OpenCodeNdjsonParser {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeNdjsonParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OpenCodeResult parse(String rawOutput) {
        if (rawOutput == null) {
            return new OpenCodeResult(null, "", null, List.of(), List.of(), false);
        }

        String sessionId = null;
        StringBuilder textBuilder = new StringBuilder();
        List<String> readableSteps = new ArrayList<>();
        List<OpenCodeResult.ToolCallSummary> toolCalls = new ArrayList<>();

        String currentStepType = null;
        StringBuilder currentStepText = new StringBuilder();
        boolean hasStepFinish = false;
        String lastFinishReason = null;

        String[] lines = rawOutput.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            OpenCodeEvent event;
            try {
                event = MAPPER.readValue(trimmed, OpenCodeEvent.class);
            } catch (Exception e) {
                log.warn("Skipping invalid JSON line: {} – {}", trimmed, e.getMessage());
                continue;
            }

            if (event == null) {
                continue;
            }

            if (event.sessionId() != null && !event.sessionId().isBlank()) {
                sessionId = event.sessionId();
            }

            if ("text".equals(event.type()) && event.text() != null) {
                textBuilder.append(event.text());
                currentStepText.append(event.text());
            }

            if ("step_start".equals(event.type()) && event.stepStart() != null) {
                if (currentStepType != null && !currentStepText.isEmpty()) {
                    readableSteps.add(describeStep(currentStepType, currentStepText.toString()));
                }
                currentStepType = event.stepStart().type();
                currentStepText = new StringBuilder();
                hasStepFinish = false;
            }

            if ("step_finish".equals(event.type()) && event.stepFinish() != null) {
                if (currentStepType != null) {
                    readableSteps.add(describeStep(currentStepType, currentStepText.toString()));
                }
                currentStepType = null;
                currentStepText = new StringBuilder();
                hasStepFinish = true;
                lastFinishReason = event.stepFinish().reason();
            }

            if ("tool_use".equals(event.type()) && event.toolName() != null) {
                String inputSummary = event.toolInput() != null ? event.toolInput().toString() : "";
                toolCalls.add(new OpenCodeResult.ToolCallSummary(event.toolName(), inputSummary));
            }
        }

        if (currentStepType != null && !currentStepText.isEmpty()) {
            readableSteps.add(describeStep(currentStepType, currentStepText.toString()));
        }

        boolean isComplete = hasStepFinish && "stop".equals(lastFinishReason);

        String lastStopStepText = null;
        if ("stop".equals(lastFinishReason)) {
            int idx = readableSteps.size() - 1;
            if (idx >= 0) {
                lastStopStepText = readableSteps.get(idx);
            }
        }

        return new OpenCodeResult(
                sessionId,
                textBuilder.toString(),
                lastStopStepText,
                List.copyOf(readableSteps),
                List.copyOf(toolCalls),
                isComplete
        );
    }

    private static String describeStep(String stepType, String textContent) {
        if (stepType == null || stepType.isBlank()) {
            return textContent.isBlank() ? "(unknown step)" : textContent;
        }
        String preview = textContent.isBlank() ? "" : ": " + truncate(textContent, 120);
        return "[" + stepType + "]" + preview;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
