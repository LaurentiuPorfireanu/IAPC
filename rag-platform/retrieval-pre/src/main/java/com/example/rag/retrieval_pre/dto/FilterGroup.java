package com.example.rag.retrieval_pre.dto;

import java.util.List;
import java.util.Objects;

public record FilterGroup(
        LogicalOperator operator,
        List<FilterCondition> conditions
) {
    public FilterGroup {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(conditions, "conditions must not be null");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("FilterGroup must contain at least one condition");
        }
        conditions = List.copyOf(conditions);
    }
}
