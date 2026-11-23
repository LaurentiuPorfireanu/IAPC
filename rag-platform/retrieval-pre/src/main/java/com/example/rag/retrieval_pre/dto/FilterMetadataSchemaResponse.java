package com.example.rag.retrieval_pre.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FilterMetadataSchemaResponse(
        Map<String, MetadataFieldType> validFields,
        List<String> validOperators,
        List<String> validLogicalOperators
) {
    public FilterMetadataSchemaResponse {
        validFields = validFields == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(validFields));
        validOperators = validOperators == null ? List.of() : List.copyOf(validOperators);
        validLogicalOperators = validLogicalOperators == null ? List.of() : List.copyOf(validLogicalOperators);
    }
}
