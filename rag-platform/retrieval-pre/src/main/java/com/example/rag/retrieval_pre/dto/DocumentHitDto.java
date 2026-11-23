package com.example.rag.retrieval_pre.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentHitDto(
        String id,
        String content,
        Map<String, Object> metadata
) {
    public DocumentHitDto {
        metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
