package com.tddforge.config;

import jakarta.validation.constraints.NotBlank;

public class ModelSpec {

    @NotBlank(message = "model must not be blank")
    private String model;

    private String variant = "";

    private String agent = "";

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }
}
