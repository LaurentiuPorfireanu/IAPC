package com.example.rag.ingest_dynamic.controller;

import com.example.rag.ingest_dynamic.dto.BatchIngestResult;
import com.example.rag.ingest_dynamic.dto.BatchIngestResult.SourceOutcome;
import com.example.rag.ingest_dynamic.service.InternetIngestionService;
import com.example.rag.ingest_dynamic.service.VersionedSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DynamicIngestControllerTest {

    @Mock
    private InternetIngestionService internetIngestionService;
    @Mock
    private VersionedSearchService versionedSearchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DynamicIngestController(internetIngestionService, versionedSearchService)).build();
    }

    @Test
    void ingestFromInternet_ValidRequest_Returns200() throws Exception {
        BatchIngestResult mockResult = new BatchIngestResult(
                1, 1, 0,
                List.of(new SourceOutcome("https://example.org/guide", true, 3, "Ingested successfully"))
        );
        when(internetIngestionService.ingestBatch(any())).thenReturn(mockResult);

        String payload = """
                {
                  "sources": [
                    {
                      "url": "https://example.org/guide",
                      "type": "WEB_PAGE",
                      "docId": "sample-guide",
                      "orgUnit": "clinical-ops"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/dynamic/internet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void ingestFromInternet_EmptySources_Returns400() throws Exception {
        String emptyPayload = """
                {
                  "sources": []
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/dynamic/internet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyPayload))
                .andExpect(status().isBadRequest());
    }
}