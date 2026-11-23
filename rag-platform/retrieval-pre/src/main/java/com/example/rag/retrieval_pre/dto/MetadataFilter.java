package com.example.rag.retrieval_pre.dto;

import java.util.List;
import java.util.Objects;

public record MetadataFilter(
        LogicalOperator rootOperator,
        List<FilterGroup> groups
) {
    public MetadataFilter {
        Objects.requireNonNull(rootOperator, "rootOperator must not be null");
        Objects.requireNonNull(groups, "groups must not be null");
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("MetadataFilter must contain at least one filter group");
        }
        groups = List.copyOf(groups);
    }

    public static MetadataFilter singleGroup(LogicalOperator operator, List<FilterCondition> conditions) {
        return new MetadataFilter(operator, List.of(new FilterGroup(operator, conditions)));
    }

    public int totalConditionCount() {
        return groups.stream().mapToInt(group -> group.conditions().size()).sum();
    }
}
