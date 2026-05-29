package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TaskSourceTest {

    @Test
    void shouldParseValidSource() {
        assertEquals(TaskSource.MANUAL, TaskSource.fromString("MANUAL"));
        assertEquals(TaskSource.WEBHOOK, TaskSource.fromString("WEBHOOK"));
        assertEquals(TaskSource.CLI, TaskSource.fromString("CLI"));
    }

    @Test
    void shouldRejectInvalidSource() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TaskSource.fromString("SCHEDULED"));
        assertTrue(ex.getMessage().contains("Invalid TaskSource"));
    }
}
