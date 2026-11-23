package com.example.rag.retrieval_pre.service;

import java.util.List;

public class InvalidMetadataFilterException extends RuntimeException {

    private final List<String> validFields;
    private final List<String> validOperators;

    public InvalidMetadataFilterException(
            String message,
            List<String> validFields,
            List<String> validOperators
    ) {
        super(message);
        this.validFields = validFields == null ? List.of() : List.copyOf(validFields);
        this.validOperators = validOperators == null ? List.of() : List.copyOf(validOperators);
    }

    public List<String> getValidFields() {
        return validFields;
    }

    public List<String> getValidOperators() {
        return validOperators;
    }
}
