package com.example.rag.ingest_dynamic.dto;

import java.util.List;

public record InternetIngestRequest(
        List<SourceItem> sources
) {
    public record SourceItem(
            String url,
            SourceType type,
            String docId,
            String orgUnit,
            String version // Optional (e.g., "v1", "v2"); auto-incremented if omitted
    ) {
        public SourceItem(String url, SourceType type, String docId) {
            this(url, type, docId, null, null);
        }

        public SourceItem(String url, SourceType type, String docId, String orgUnit) {
            this(url, type, docId, orgUnit, null);
        }
    }

    public enum SourceType {
        WEB_PAGE,
        REMOTE_PDF,
        REPO_DOC
    }
}