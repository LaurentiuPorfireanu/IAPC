package com.example.rag.retrieval_pre.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchRepositoryTest {

    @Mock
    private VectorStore vectorStore;

    private VectorSearchRepository repository;

    @BeforeEach
    void setUp() {
        repository = new VectorSearchRepository(vectorStore);
    }

    @Test
    void search_WithNullFilter_BuildsUnfilteredSearchRequest() {
        Document expected = new Document("id-1", "Standard content", Map.of("doc_id", "doc-1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(expected));

        List<Document> results = repository.search("policy question", 5, null);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals("policy question", captor.getValue().getQuery());
        assertEquals(5, captor.getValue().getTopK());
        assertNull(captor.getValue().getFilterExpression());
        assertEquals(1, results.size());
    }

    @Test
    void search_WithTypedFilterExpression_PassesFilterExpressionToVectorStore() {
        Filter.Expression expr = new FilterExpressionBuilder().eq("doc_id", "doc-001").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        repository.search("policy question", 3, expr);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(expr, captor.getValue().getFilterExpression());
        assertEquals(3, captor.getValue().getTopK());
    }
}
