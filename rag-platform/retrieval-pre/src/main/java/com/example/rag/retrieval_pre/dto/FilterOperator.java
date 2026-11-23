package com.example.rag.retrieval_pre.dto;

import java.util.Arrays;
import java.util.Optional;

public enum FilterOperator {
    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    IN("in");

    private final String code;

    FilterOperator(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isRangeOperator() {
        return this == GT || this == GTE || this == LT || this == LTE;
    }

    public static Optional<FilterOperator> fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawCode.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(op -> op.code.equals(normalized))
                .findFirst();
    }
}
