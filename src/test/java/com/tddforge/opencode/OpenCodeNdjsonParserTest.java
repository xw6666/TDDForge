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
    void shouldExposeLastTextEventSeparately() {
        String output = """
                {"type":"text","text":"Intermediate note"}
                {"type":"text","text":"APPROVE\\nFinal reviewer response"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.text()).contains("Intermediate note").contains("APPROVE");
        assertThat(result.lastText()).isEqualTo("APPROVE\nFinal reviewer response");
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

    @Test
    void shouldOverwriteSessionIdFromLaterEvents() {
        String output = """
                {"type":"text","sessionId":"sess-first","text":"Hello"}
                {"type":"text","sessionId":"sess-second","text":"World"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.sessionId()).isEqualTo("sess-second");
    }

    @Test
    void shouldSkipWhitespaceOnlyLines() {
        String output = """
                {"type":"text","text":"Before"}
                \s\s\s\s
                \t\t
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).contains("Before");
    }

    @Test
    void shouldHandleAllInvalidJsonLines() {
        String output = """
                not valid json
                also bad
                {broken
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isFalse();
        assertThat(result.text()).isEmpty();
        assertThat(result.sessionId()).isNull();
        assertThat(result.readableSteps()).isEmpty();
        assertThat(result.toolCalls()).isEmpty();
    }

    @Test
    void shouldBuildMultipleReadableSteps() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"First step work"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                {"type":"step_start","step_start":{"type":"coding"}}
                {"type":"text","text":"Writing code"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.readableSteps()).hasSize(2);
        assertThat(result.readableSteps().get(0)).contains("thinking");
        assertThat(result.readableSteps().get(1)).contains("coding");
    }

    @Test
    void shouldIgnoreBlankSessionId() {
        String output = """
                {"type":"text","sessionId":"","text":"Hello"}
                {"type":"text","sessionId":"  ","text":"World"}
                {"type":"text","sessionId":"real-sess","text":"Actual"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.sessionId()).isEqualTo("real-sess");
    }

    @Test
    void shouldHandleToolUseWithNullInput() {
        String output = """
                {"type":"tool_use","tool_name":"search"}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).toolName()).isEqualTo("search");
    }

    @Test
    void shouldHandleStepFinishWithoutPriorStepStart() {
        String output = """
                {"type":"text","text":"Some text"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).contains("Some text");
    }

    @Test
    void shouldNotBeCompleteWhenLastStepReasonIsNotStop() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"First step"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                {"type":"step_start","step_start":{"type":"coding"}}
                {"type":"text","text":"Second step"}
                {"type":"step_finish","step_finish":{"reason":"error"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isFalse();
        assertThat(result.readableSteps()).hasSize(2);
    }

    @Test
    void shouldReturnNullLastStopStepTextWhenReasonIsNotStop() {
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"Some work"}
                {"type":"step_finish","step_finish":{"reason":"error"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isFalse();
        assertThat(result.lastStopStepText()).isNull();
    }

    @Test
    void shouldHandleMultipleToolCalls() {
        String output = """
                {"type":"tool_use","tool_name":"read","tool_input":{"file":"a.java"}}
                {"type":"tool_use","tool_name":"write","tool_input":{"file":"b.java","content":"x"}}
                {"type":"tool_use","tool_name":"bash","tool_input":{"command":"mvn test"}}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.toolCalls()).hasSize(3);
        assertThat(result.toolCalls().get(0).toolName()).isEqualTo("read");
        assertThat(result.toolCalls().get(1).toolName()).isEqualTo("write");
        assertThat(result.toolCalls().get(2).toolName()).isEqualTo("bash");
    }

    @Test
    void shouldHandleTextEventWithEmptyText() {
        String output = """
                {"type":"text","text":""}
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldTruncateLongStepText() {
        String longText = "A".repeat(200);
        String output = """
                {"type":"step_start","step_start":{"type":"thinking"}}
                {"type":"text","text":"%s"}
                {"type":"step_finish","step_finish":{"reason":"stop"}}
                """.formatted(longText);

        OpenCodeResult result = parser.parse(output);

        assertThat(result.complete()).isTrue();
        assertThat(result.readableSteps()).hasSize(1);
        assertThat(result.readableSteps().get(0)).endsWith("...");
    }

    @Test
    void shouldParseCurrentOpenCodePartEvents() {
        String output = """
                {"type":"step_start","timestamp":1780680815618,"sessionID":"ses-new","part":{"id":"prt-1","messageID":"msg-1","sessionID":"ses-new","snapshot":"abc","type":"step-start"}}
                {"type":"text","timestamp":1780680817468,"sessionID":"ses-new","part":{"id":"prt-2","messageID":"msg-1","sessionID":"ses-new","type":"text","text":"{\\"complexity\\":\\"simple\\",\\"split\\":false,\\"reason\\":\\"ok\\",\\"plan\\":\\"test\\"}","time":{"start":1780680817000,"end":1780680817468}}}
                {"type":"step_finish","timestamp":1780680817561,"sessionID":"ses-new","part":{"id":"prt-3","reason":"stop","snapshot":"abc","messageID":"msg-1","sessionID":"ses-new","type":"step-finish","tokens":{"total":10}}}
                """.stripIndent();

        OpenCodeResult result = parser.parse(output);

        assertThat(result.sessionId()).isEqualTo("ses-new");
        assertThat(result.text()).contains("\"complexity\":\"simple\"");
        assertThat(result.complete()).isTrue();
        assertThat(result.lastStopStepText()).contains("\"plan\":\"test\"");
    }
}
