package com.example.rag.retrieval_pre.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchRepository.class);

    private final VectorStore vectorStore;

    public VectorSearchRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Executes similarity search against the VectorStore.
     * Accepts only a typed Spring AI Filter.Expression (or null for unfiltered standard retrieval)
     * so raw query strings can never reach the vector database.
     */
    public List<Document> search(String query, int topK, Filter.Expression filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }

        SearchRequest request = builder.build();
        log.debug("Querying vector store [topK={}, hasFilter={}]", topK, filterExpression != null);
        List<Document> results = vectorStore.similaritySearch(request);
        return results == null ? List.of() : results;
    }
}
