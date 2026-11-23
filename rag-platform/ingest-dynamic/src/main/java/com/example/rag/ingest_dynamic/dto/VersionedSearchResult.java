package com.example.rag.ingest_dynamic.dto;

import java.util.List;
import java.util.Map;

public record VersionedSearchResult(
        String query,
        String requestedVersion,
        int totalHits,
        List<DocumentHit> hits
) {
    public record DocumentHit(
            String id,
            String content,
            Map<String, Object> metadata
    ) {}
}