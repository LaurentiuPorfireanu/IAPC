package com.example.rag.ingest_dynamic.dto;

import java.util.List;

public record BatchIngestResult(
        int totalRequested,
        int succeeded,
        int failed,
        List<SourceOutcome> outcomes
) {
    public record SourceOutcome(
            String url,
            boolean success,
            int chunksStored,
            String message
    ) {}
}