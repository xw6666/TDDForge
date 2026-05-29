package com.tddforge.opencode;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OpenCodeNdjsonParserTest {

    private final OpenCodeNdjsonParser parser = new OpenCodeNdjsonParser();

    @Test
    void shouldParseNormalStop() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"Analyzing the problem..."}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.sessionId()).isNull();
        assertThat(result.text()).contains("Analyzing the problem...");
        assertThat(result.complete()).isTrue();
        assertThat(result.readableSteps()).hasSize(1);
        assertThat(result.toolCalls()).isEmpty();
    }

    @Test
    void shouldExtractSessionId() {
        String output = """
                {"type":"text","sessionId":"sess-123","text":"Hello"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"Working..."}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.sessionId()).isEqualTo("sess-123");
        assertThat(result.complete()).isTrue();
    }

    @Test
    void shouldMarkIncompleteWhenNoStepFinish() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"In progress..."}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isFalse();
        assertThat(result.text()).contains("In progress...");
    }

    @Test
    void shouldMarkIncompleteWhenFinishReasonIsNotStop() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"Something broke"}
                {"type":"step_finish","step_finish":{"reason":"error"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isFalse();
    }

    @Test
    void shouldSkipBlankLines() {
        String output = """
                {"type":"text","text":"Line1"}
                                
                                
                {"type":"step_start","step_start":{"type":"thinking"}}
                                
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).contains("Line1");
    }

    @Test
    void shouldSkipInvalidJsonLines() {
        String output = """
                {"type":"text","text":"Valid line"}
                not valid json
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).contains("Valid line");
    }

    @Test
    void shouldIgnoreUnknownFields() {
        String output = """
                {"type":"text","text":"Hello","unknown_field":"value","extra":123}
                {"type":"step_start","step_start":{"type":"thinking"},"newMeta":"data"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).contains("Hello");
    }

    @Test
    void shouldReturnIncompleteForNullInput() {
        OpenCodeResult result = parser.parse(null);

        assertThat(result.complete()).isFalse();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldReturnIncompleteForEmptyInput() {
        OpenCodeResult result = parser.parse("");

        assertThat(result.complete()).isFalse();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldCollectToolCalls() {
        String output = """
                {"type":"tool_use","tool_name":"read","tool_input":{"file":"src/main.java"}}
                {"type":"tool_result","tool_result":"content"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).toolName()).isEqualTo("read");
    }

    @Test
    void shouldProvideLastStopStepText() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"First step work"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.lastStopStepText()).contains("First step work");
    }

    @Test
    void shouldConcatTextAcrossMultipleEvents() {
        String output = """
                {"type":"text","text":"Part1"}
                {"type":"text","text":"Part2"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.text()).contains("Part1").contains("Part2");
    }

    @Test
    void shouldGenerateReadableSteps() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"Thinking about the problem"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.readableSteps()).isNotEmpty();
        assertThat(result.readableSteps().get(0)).contains("thinking");
    }
}
