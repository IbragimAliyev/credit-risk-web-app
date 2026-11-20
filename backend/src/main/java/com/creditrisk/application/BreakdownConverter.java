package com.creditrisk.application;

import com.creditrisk.risk.FactorContribution;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/** Stores the per-factor score breakdown as a JSON string column. */
@Converter
public class BreakdownConverter implements AttributeConverter<List<FactorContribution>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<FactorContribution>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<FactorContribution> value) {
        try {
            return MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize breakdown", e);
        }
    }

    @Override
    public List<FactorContribution> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read breakdown", e);
        }
    }
}
