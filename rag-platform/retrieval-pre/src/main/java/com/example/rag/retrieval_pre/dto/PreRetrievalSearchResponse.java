package com.example.rag.retrieval_pre.dto;

import java.util.List;

public record PreRetrievalSearchResponse(
        String query,
        int topK,
        boolean filterApplied,
        MetadataFilter appliedFilter,
        int resultCount,
        List<DocumentHitDto> results
) {
    public PreRetrievalSearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
