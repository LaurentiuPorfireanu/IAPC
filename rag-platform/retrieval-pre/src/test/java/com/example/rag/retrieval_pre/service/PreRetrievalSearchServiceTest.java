package com.example.rag.retrieval_pre.service;

import com.example.rag.retrieval_pre.config.MetadataFilterProperties;
import com.example.rag.retrieval_pre.dto.FilterCondition;
import com.example.rag.retrieval_pre.dto.FilterOperator;
import com.example.rag.retrieval_pre.dto.LogicalOperator;
import com.example.rag.retrieval_pre.dto.MetadataFilter;
import com.example.rag.retrieval_pre.dto.PreRetrievalSearchResponse;
import com.example.rag.retrieval_pre.repository.VectorSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreRetrievalSearchServiceTest {

    @Mock
    private MetadataFilterService metadataFilterService;

    @Mock
    private VectorSearchRepository vectorSearchRepository;

    private PreRetrievalSearchService searchService;

    @BeforeEach
    void setUp() {
        MetadataFilterProperties properties = new MetadataFilterProperties();
        properties.setDefaultTopK(5);
        properties.setMaxTopK(50);
        searchService = new PreRetrievalSearchService(metadataFilterService, vectorSearchRepository, properties);
    }

    @Test
    void search_NullFilter_FallsThroughToStandardFlow() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "audit retention policy");

        Document doc = new Document("chunk-1", "Retention period is 7 years.", Map.of("doc_id", "doc-001", "page", 1));
        when(metadataFilterService.parseAndValidate(params)).thenReturn(null);
        when(metadataFilterService.toFilterExpression(null)).thenReturn(null);
        when(vectorSearchRepository.search("audit retention policy", 5, null)).thenReturn(List.of(doc));

        PreRetrievalSearchResponse response = searchService.search("audit retention policy", null, params);

        assertFalse(response.filterApplied());
        assertNull(response.appliedFilter());
        assertEquals(5, response.topK());
        assertEquals(1, response.resultCount());
        assertEquals("chunk-1", response.results().get(0).id());
        verify(vectorSearchRepository).search("audit retention policy", 5, null);
    }

    @Test
    void search_WithValidatedFilter_PassesTypedExpressionToRepository() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "access control");
        params.add("filter", "doc_id:eq:doc-001");

        MetadataFilter validatedFilter = MetadataFilter.singleGroup(
                LogicalOperator.AND,
                List.of(new FilterCondition("doc_id", FilterOperator.EQ, "doc-001"))
        );
        Filter.Expression expression = new FilterExpressionBuilder().eq("doc_id", "doc-001").build();
        Document doc = new Document("chunk-2", "Role-based access control rules.", Map.of("doc_id", "doc-001"));

        when(metadataFilterService.parseAndValidate(params)).thenReturn(validatedFilter);
        when(metadataFilterService.toFilterExpression(validatedFilter)).thenReturn(expression);
        when(vectorSearchRepository.search("access control", 10, expression)).thenReturn(List.of(doc));

        PreRetrievalSearchResponse response = searchService.search("access control", 10, params);

        assertTrue(response.filterApplied());
        assertNotNull(response.appliedFilter());
        assertEquals(10, response.topK());
        assertEquals(1, response.resultCount());
        verify(vectorSearchRepository).search("access control", 10, expression);
    }

    @Test
    void search_InvalidFilter_NeverCallsRepository() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "audit");
        params.add("filter", "bad_field:eq:value");

        when(metadataFilterService.parseAndValidate(params))
                .thenThrow(new InvalidMetadataFilterException("Invalid field", List.of("doc_id"), List.of("eq")));

        assertThrows(InvalidMetadataFilterException.class, () -> searchService.search("audit", 5, params));
        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void search_BlankQueryOrInvalidTopK_ThrowsIllegalArgumentAndNeverCallsRepository() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        assertThrows(IllegalArgumentException.class, () -> searchService.search("   ", 5, params));
        assertThrows(IllegalArgumentException.class, () -> searchService.search("valid query", 0, params));
        assertThrows(IllegalArgumentException.class, () -> searchService.search("valid query", 51, params));
        verifyNoInteractions(vectorSearchRepository);
    }
}
