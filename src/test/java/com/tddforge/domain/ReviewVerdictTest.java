package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ReviewVerdictTest {

    @Test
    void shouldParseValidVerdict() {
        assertEquals(ReviewVerdict.APPROVE, ReviewVerdict.fromString("APPROVE"));
        assertEquals(ReviewVerdict.REQUEST_CHANGES, ReviewVerdict.fromString("REQUEST_CHANGES"));
    }

    @Test
    void shouldRejectInvalidVerdict() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ReviewVerdict.fromString("REJECT"));
        assertTrue(ex.getMessage().contains("Invalid ReviewVerdict"));
    }
}
