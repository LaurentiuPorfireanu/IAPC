package com.example.rag.retrieval_pre.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreRetrievalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void search_NullFilter_FallsThroughToStandardFlowAndReturns200() throws Exception {
        Document doc = new Document("chunk-1", "General compliance overview.", Map.of("doc_id", "doc-001", "page", 1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "compliance overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("compliance overview"))
                .andExpect(jsonPath("$.filterApplied").value(false))
                .andExpect(jsonPath("$.appliedFilter").doesNotExist())
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results[0].id").value("chunk-1"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertNull(captor.getValue().getFilterExpression());
    }

    @Test
    void search_MultiVariableAndFilter_Returns200AndPassesTypedFilterExpression() throws Exception {
        Document doc = new Document("chunk-2", "Policy section 2.", Map.of("source", "handbook.pdf", "page", 3));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "retention rules")
                        .param("topK", "5")
                        .param("filter", "source:eq:handbook.pdf")
                        .param("filter", "page:gte:2")
                        .param("logic", "AND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterApplied").value(true))
                .andExpect(jsonPath("$.appliedFilter.rootOperator").value("AND"))
                .andExpect(jsonPath("$.appliedFilter.groups[0].conditions.length()").value(2))
                .andExpect(jsonPath("$.resultCount").value(1));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        Filter.Expression expr = captor.getValue().getFilterExpression();
        assertNotNull(expr);
        assertEquals(Filter.ExpressionType.AND, expr.type());
    }

    @Test
    void search_MultiVariableOrFilter_Returns200AndPassesOrExpression() throws Exception {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "audit scope")
                        .param("filter", "doc_id:eq:doc-001")
                        .param("filter", "doc_id:eq:doc-002")
                        .param("logic", "OR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterApplied").value(true))
                .andExpect(jsonPath("$.appliedFilter.rootOperator").value("OR"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        Filter.Expression expr = captor.getValue().getFilterExpression();
        assertNotNull(expr);
        assertEquals(Filter.ExpressionType.OR, expr.type());
    }

    @Test
    void search_CombinedAndOrFilterGroups_Returns200() throws Exception {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "governance")
                        .param("filterGroup", "org_unit:eq:unit-a;page:gte:2")
                        .param("filterGroup", "org_unit:eq:unit-b;is_latest:eq:true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterApplied").value(true))
                .andExpect(jsonPath("$.appliedFilter.rootOperator").value("OR"))
                .andExpect(jsonPath("$.appliedFilter.groups.length()").value(2));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        Filter.Expression expr = captor.getValue().getFilterExpression();
        assertNotNull(expr);
        assertEquals(Filter.ExpressionType.OR, expr.type());
    }

    @Test
    void search_InvalidMetadataField_Returns400ListingValidFields() throws Exception {
        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "audit controls")
                        .param("filter", "secret_column:eq:admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Metadata Filter"))
                .andExpect(jsonPath("$.validFields", hasItem("doc_id")))
                .andExpect(jsonPath("$.validFields", hasItem("source")))
                .andExpect(jsonPath("$.validFields", hasItem("page")))
                .andExpect(jsonPath("$.validOperators", hasItem("eq")));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void search_UnknownDirectUrlParameter_Returns400ListingValidFields() throws Exception {
        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "audit controls")
                        .param("unapproved_field", "value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validFields", hasItem("doc_id")));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void search_InvalidOperator_Returns400ListingValidOperatorsAndFields() throws Exception {
        mockMvc.perform(get("/api/v1/retrieve/pre/search")
                        .param("query", "audit controls")
                        .param("filter", "doc_id:regex:.*"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validFields", hasItem("doc_id")))
                .andExpect(jsonPath("$.validOperators", hasItem("eq")));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void search_MissingQuery_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/retrieve/pre/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(vectorStore);
    }

    @Test
    void getValidFilterSchema_ReturnsWhitelistedFieldsAndOperators() throws Exception {
        mockMvc.perform(get("/api/v1/retrieve/pre/filters/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validFields.doc_id").value("STRING"))
                .andExpect(jsonPath("$.validFields.page").value("INTEGER"))
                .andExpect(jsonPath("$.validFields.is_latest").value("BOOLEAN"))
                .andExpect(jsonPath("$.validOperators", hasItem("eq")))
                .andExpect(jsonPath("$.validLogicalOperators", hasItem("AND")))
                .andExpect(jsonPath("$.validLogicalOperators", hasItem("OR")));
    }
}
