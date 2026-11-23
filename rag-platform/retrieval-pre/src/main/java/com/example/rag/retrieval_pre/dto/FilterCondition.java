package com.example.rag.retrieval_pre.dto;

import java.util.List;
import java.util.Objects;

public record FilterCondition(
        String field,
        FilterOperator operator,
        Object value
) {
    public FilterCondition {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (value instanceof List<?> list) {
            value = List.copyOf(list);
        }
    }
}
