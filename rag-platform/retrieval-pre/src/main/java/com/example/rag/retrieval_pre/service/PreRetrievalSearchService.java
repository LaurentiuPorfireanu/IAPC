package com.example.rag.retrieval_pre.service;

import com.example.rag.retrieval_pre.config.MetadataFilterProperties;
import com.example.rag.retrieval_pre.dto.DocumentHitDto;
import com.example.rag.retrieval_pre.dto.MetadataFilter;
import com.example.rag.retrieval_pre.dto.PreRetrievalSearchResponse;
import com.example.rag.retrieval_pre.repository.VectorSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.List;

@Service
public class PreRetrievalSearchService {

    private static final Logger log = LoggerFactory.getLogger(PreRetrievalSearchService.class);

    private final MetadataFilterService metadataFilterService;
    private final VectorSearchRepository vectorSearchRepository;
    private final MetadataFilterProperties filterProperties;

    public PreRetrievalSearchService(
            MetadataFilterService metadataFilterService,
            VectorSearchRepository vectorSearchRepository,
            MetadataFilterProperties filterProperties
    ) {
        this.metadataFilterService = metadataFilterService;
        this.vectorSearchRepository = vectorSearchRepository;
        this.filterProperties = filterProperties;
    }

    public PreRetrievalSearchResponse search(
            String query,
            Integer requestedTopK,
            MultiValueMap<String, String> queryParams
    ) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'query' must not be empty");
        }

        int resolvedTopK = resolveTopK(requestedTopK);
        MetadataFilter validatedFilter = metadataFilterService.parseAndValidate(queryParams);
        Filter.Expression filterExpression = metadataFilterService.toFilterExpression(validatedFilter);

        if (validatedFilter == null) {
            log.info("Executing pre-retrieval search without metadata filter (standard flow) [query='{}', topK={}]",
                    query, resolvedTopK);
        } else {
            log.info("Executing pre-retrieval search with validated metadata filter [query='{}', topK={}, groups={}, conditions={}, rootOperator={}]",
                    query, resolvedTopK, validatedFilter.groups().size(),
                    validatedFilter.totalConditionCount(), validatedFilter.rootOperator());
        }

        List<Document> documents = vectorSearchRepository.search(query, resolvedTopK, filterExpression);
        List<DocumentHitDto> hits = documents.stream()
                .map(doc -> new DocumentHitDto(doc.getId(), doc.getText(), doc.getMetadata()))
                .toList();

        log.info("Pre-retrieval search completed [results={}, filterApplied={}]", hits.size(), validatedFilter != null);

        return new PreRetrievalSearchResponse(
                query,
                resolvedTopK,
                validatedFilter != null,
                validatedFilter,
                hits.size(),
                hits
        );
    }

    private int resolveTopK(Integer requestedTopK) {
        int topK = requestedTopK == null ? filterProperties.getDefaultTopK() : requestedTopK;
        if (topK <= 0 || topK > filterProperties.getMaxTopK()) {
            throw new IllegalArgumentException(
                    "Parameter 'topK' must be between 1 and " + filterProperties.getMaxTopK());
        }
        return topK;
    }
}
