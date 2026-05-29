package com.tddforge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSpecTest {

    @Test
    void variantDefaultsToEmptyString() {
        ModelSpec spec = new ModelSpec();
        assertThat(spec.getVariant()).isEqualTo("");
    }

    @Test
    void agentDefaultsToEmptyString() {
        ModelSpec spec = new ModelSpec();
        assertThat(spec.getAgent()).isEqualTo("");
    }

    @Test
    void modelIsNullByDefault() {
        ModelSpec spec = new ModelSpec();
        assertThat(spec.getModel()).isNull();
    }

    @Test
    void settersAndGetters() {
        ModelSpec spec = new ModelSpec();
        spec.setModel("test-model");
        spec.setVariant("test-variant");
        spec.setAgent("test-agent");

        assertThat(spec.getModel()).isEqualTo("test-model");
        assertThat(spec.getVariant()).isEqualTo("test-variant");
        assertThat(spec.getAgent()).isEqualTo("test-agent");
    }
}
