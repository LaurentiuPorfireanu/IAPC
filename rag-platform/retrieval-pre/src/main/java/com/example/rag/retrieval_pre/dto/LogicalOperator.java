package com.example.rag.retrieval_pre.dto;

import java.util.Optional;

public enum LogicalOperator {
    AND,
    OR;

    public static Optional<LogicalOperator> fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase();
        if ("AND".equals(normalized)) {
            return Optional.of(AND);
        }
        if ("OR".equals(normalized)) {
            return Optional.of(OR);
        }
        return Optional.empty();
    }
}
