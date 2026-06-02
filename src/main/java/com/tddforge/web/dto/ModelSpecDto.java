package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelSpecDto {

    private String model;
    private String variant = "";
    private String agent = "";

    public ModelSpecDto() {}

    public ModelSpecDto(String model, String variant, String agent) {
        this.model = model;
        this.variant = variant != null ? variant : "";
        this.agent = agent != null ? agent : "";
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
}
