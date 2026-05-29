package com.tddforge.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ModelSpecTest {

    @Test
    void shouldCreateWithRequiredFields() {
        var spec = new ModelSpec("gpt-4", null, null);
        assertEquals("gpt-4", spec.model());
        assertNull(spec.variant());
        assertNull(spec.agent());
    }

    @Test
    void shouldCreateWithAllFields() {
        var spec = new ModelSpec("gpt-4", "v1", "coder");
        assertEquals("gpt-4", spec.model());
        assertEquals("v1", spec.variant());
        assertEquals("coder", spec.agent());
    }

    @Test
    void shouldRejectNullModel() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec(null, null, null));
    }

    @Test
    void shouldRejectBlankModel() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelSpec("  ", null, null));
    }
}
