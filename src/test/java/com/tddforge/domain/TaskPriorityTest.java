package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TaskPriorityTest {

    @Test
    void shouldParseValidPriority() {
        assertEquals(TaskPriority.LOW, TaskPriority.fromString("LOW"));
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromString("MEDIUM"));
        assertEquals(TaskPriority.HIGH, TaskPriority.fromString("HIGH"));
        assertEquals(TaskPriority.CRITICAL, TaskPriority.fromString("CRITICAL"));
    }

    @Test
    void shouldRejectInvalidPriority() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskPriority.fromString("URGENT"));
        assertTrue(ex.getMessage().contains("Invalid TaskPriority"));
    }

    @Test
    void shouldRejectNullPriority() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskPriority.fromString(null));
    }
}
