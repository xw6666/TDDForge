package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.Collectors;

class TaskStatusTest {

    @Test
    void shouldHaveAllRequiredStates() {
        var expected = Arrays.asList(
            "PENDING", "PLANNING", "TEST_WRITING", "TEST_WRITE_FAILED",
            "TEST_REVIEWING", "TEST_REVIEW_FAILED", "CODING", "REVIEWING",
            "REVIEW_FAILED", "NEEDS_ARBITRATION", "COMPLETED", "FAILED", "CANCELLED"
        );
        var actual = Arrays.stream(TaskStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    void shouldParseValidStatus() {
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString("PENDING"));
        assertEquals(TaskStatus.TEST_WRITING, TaskStatus.fromString("TEST_WRITING"));
        assertEquals(TaskStatus.NEEDS_ARBITRATION, TaskStatus.fromString("NEEDS_ARBITRATION"));
        assertEquals(TaskStatus.COMPLETED, TaskStatus.fromString("COMPLETED"));
    }

    @Test
    void shouldRejectInvalidStatus() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskStatus.fromString("INVALID_STATUS"));
        assertTrue(ex.getMessage().contains("Invalid TaskStatus"));
        assertTrue(ex.getMessage().contains("PENDING"));
    }

    @Test
    void shouldRejectNullStatus() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskStatus.fromString(null));
        assertTrue(ex.getMessage().contains("must not be null"));
    }

    @Test
    void shouldRejectBlankStatus() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskStatus.fromString("  "));
        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    void shouldBeCaseSensitive() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskStatus.fromString("pending"));
        assertTrue(ex.getMessage().contains("Invalid TaskStatus"));
    }

    @Test
    void shouldSerializeToName() {
        assertEquals("PENDING", TaskStatus.PENDING.toValue());
        assertEquals("NEEDS_ARBITRATION", TaskStatus.NEEDS_ARBITRATION.toValue());
    }
}
