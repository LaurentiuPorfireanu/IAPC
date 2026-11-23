package com.example.rag.ingest_dynamic.service;

import com.example.rag.ingest_dynamic.dto.VersionedSearchResult;
import com.example.rag.ingest_dynamic.dto.VersionedSearchResult.DocumentHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VersionedSearchService {

    private static final Logger log = LoggerFactory.getLogger(VersionedSearchService.class);

    private final VectorStore vectorStore;

    public VersionedSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public VersionedSearchResult search(String query, String docId, String version, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query parameter must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }

        Filter.Expression filter = buildVersionFilter(docId, version);
        String effectiveVersionLabel = (version != null && !version.isBlank()) ? version.trim() : "latest";

        log.info("Executing versioned search [query='{}', docId={}, version={}, topK={}]",
                query, docId, effectiveVersionLabel, topK);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filter)
                .build();

        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        List<DocumentHit> hits = docs == null ? List.of() : docs.stream()
                .map(doc -> new DocumentHit(doc.getId(), doc.getText(), doc.getMetadata()))
                .toList();

        return new VersionedSearchResult(query, effectiveVersionLabel, hits.size(), hits);
    }

    private Filter.Expression buildVersionFilter(String docId, String version) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        FilterExpressionBuilder.Op versionOp = (version != null && !version.isBlank())
                ? b.eq("version", version.trim())
                : b.eq("is_latest", true);

        if (docId != null && !docId.isBlank()) {
            return b.and(b.eq("doc_id", docId.trim()), versionOp).build();
        }
        return versionOp.build();
    }
}