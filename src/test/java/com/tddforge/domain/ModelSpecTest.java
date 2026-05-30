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

    @Test
    void shouldAcceptEmptyVariantAndAgent() {
        var spec = new ModelSpec("gpt-4", "", "");
        assertEquals("gpt-4", spec.model());
        assertEquals("", spec.variant());
        assertEquals("", spec.agent());
    }

    @Test
    void shouldSerializeExcludesNullVariantAndAgent() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var spec = new ModelSpec("gpt-4", null, null);
        var json = mapper.writeValueAsString(spec);
        assertTrue(json.contains("gpt-4"));
        assertFalse(json.contains("variant"));
        assertFalse(json.contains("agent"));
    }

    @Test
    void shouldSerializeIncludesNonNullVariantAndAgent() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var spec = new ModelSpec("gpt-4", "v1", "coder");
        var json = mapper.writeValueAsString(spec);
        assertTrue(json.contains("gpt-4"));
        assertTrue(json.contains("v1"));
        assertTrue(json.contains("coder"));
    }
}
