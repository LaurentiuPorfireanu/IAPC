package com.example.rag.retrieval_pre.dto;

import java.time.Instant;
import java.util.List;

public record FilterErrorResponse(
        int status,
        String error,
        String message,
        List<String> validFields,
        List<String> validOperators,
        Instant timestamp
) {
    public FilterErrorResponse {
        validFields = validFields == null ? List.of() : List.copyOf(validFields);
        validOperators = validOperators == null ? List.of() : List.copyOf(validOperators);
    }
}
