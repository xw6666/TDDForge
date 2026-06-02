package com.tddforge.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelConfigResponse {

    private ModelSpecDto planner;
    private ModelSpecDto testWriter;
    private ModelSpecDto testReviewer;
    private ModelSpecDto coderDefault;
    private Map<String, ModelSpecDto> coderByComplexity;
    private List<ModelSpecDto> reviewers;

    public ModelConfigResponse() {}

    public ModelConfigResponse(ModelSpecDto planner, ModelSpecDto testWriter, ModelSpecDto testReviewer,
                               ModelSpecDto coderDefault, Map<String, ModelSpecDto> coderByComplexity,
                               List<ModelSpecDto> reviewers) {
        this.planner = planner;
        this.testWriter = testWriter;
        this.testReviewer = testReviewer;
        this.coderDefault = coderDefault;
        this.coderByComplexity = coderByComplexity;
        this.reviewers = reviewers;
    }

    public ModelSpecDto getPlanner() { return planner; }
    public void setPlanner(ModelSpecDto planner) { this.planner = planner; }

    public ModelSpecDto getTestWriter() { return testWriter; }
    public void setTestWriter(ModelSpecDto testWriter) { this.testWriter = testWriter; }

    public ModelSpecDto getTestReviewer() { return testReviewer; }
    public void setTestReviewer(ModelSpecDto testReviewer) { this.testReviewer = testReviewer; }

    public ModelSpecDto getCoderDefault() { return coderDefault; }
    public void setCoderDefault(ModelSpecDto coderDefault) { this.coderDefault = coderDefault; }

    public Map<String, ModelSpecDto> getCoderByComplexity() { return coderByComplexity; }
    public void setCoderByComplexity(Map<String, ModelSpecDto> coderByComplexity) { this.coderByComplexity = coderByComplexity; }

    public List<ModelSpecDto> getReviewers() { return reviewers; }
    public void setReviewers(List<ModelSpecDto> reviewers) { this.reviewers = reviewers; }
}
