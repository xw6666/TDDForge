package com.tddforge.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tddforge.domain.ReviewerResult;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class ReviewerResultsConverter implements AttributeConverter<List<ReviewerResult>, String> {

    private static final ObjectMapper MAPPER = createMapper();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    public String convertToDatabaseColumn(List<ReviewerResult> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize List<ReviewerResult> to JSON", e);
        }
    }

    @Override
    public List<ReviewerResult> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        String json = dbData.trim();
        // H2 JSON column may wrap the value in extra quotes; strip them
        if (json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length() - 1).replace("\\\"", "\"");
        }
        try {
            List<ReviewerResult> result = MAPPER.readValue(json, new TypeReference<>() {});
            return new ArrayList<>(result);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to List<ReviewerResult>: " + dbData, e);
        }
    }
}
