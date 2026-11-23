package com.example.rag.retrieval_pre.controller;

import com.example.rag.retrieval_pre.dto.FilterMetadataSchemaResponse;
import com.example.rag.retrieval_pre.dto.PreRetrievalSearchResponse;
import com.example.rag.retrieval_pre.service.MetadataFilterService;
import com.example.rag.retrieval_pre.service.PreRetrievalSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieve/pre")
public class PreRetrievalController {

    private final PreRetrievalSearchService searchService;
    private final MetadataFilterService metadataFilterService;

    public PreRetrievalController(
            PreRetrievalSearchService searchService,
            MetadataFilterService metadataFilterService
    ) {
        this.searchService = searchService;
        this.metadataFilterService = metadataFilterService;
    }

    @GetMapping("/search")
    public ResponseEntity<PreRetrievalSearchResponse> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "topK", required = false) Integer topK,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        PreRetrievalSearchResponse response = searchService.search(query, topK, queryParams);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/filters/metadata")
    public ResponseEntity<FilterMetadataSchemaResponse> getValidFilterSchema() {
        return ResponseEntity.ok(metadataFilterService.getMetadataSchema());
    }
}
