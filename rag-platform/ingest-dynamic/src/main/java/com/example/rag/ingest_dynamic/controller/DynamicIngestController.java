package com.example.rag.ingest_dynamic.controller;

import com.example.rag.ingest_dynamic.dto.BatchIngestResult;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest;
import com.example.rag.ingest_dynamic.dto.VersionedSearchResult;
import com.example.rag.ingest_dynamic.service.InternetIngestionService;
import com.example.rag.ingest_dynamic.service.VersionedSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ingest/dynamic")
public class DynamicIngestController {

    private static final Logger log = LoggerFactory.getLogger(DynamicIngestController.class);

    private final InternetIngestionService internetIngestionService;
    private final VersionedSearchService versionedSearchService;

    public DynamicIngestController(
            InternetIngestionService internetIngestionService,
            VersionedSearchService versionedSearchService) {
        this.internetIngestionService = internetIngestionService;
        this.versionedSearchService = versionedSearchService;
    }

    @PostMapping("/internet")
    public ResponseEntity<BatchIngestResult> ingestFromInternet(@RequestBody InternetIngestRequest request) {
        if (request == null || request.sources() == null || request.sources().isEmpty()) {
            log.warn("Rejected internet ingestion request: empty or missing sources list");
            return ResponseEntity.badRequest().build();
        }
        log.info("Received internet ingestion request with {} source(s)", request.sources().size());
        return ResponseEntity.ok(internetIngestionService.ingestBatch(request));
    }

    @GetMapping("/search")
    public ResponseEntity<VersionedSearchResult> searchByVersion(
            @RequestParam String query,
            @RequestParam(required = false) String docId,
            @RequestParam(required = false) String version,
            @RequestParam(defaultValue = "5") int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            log.warn("Rejected versioned search request: invalid query or topK={}", topK);
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(versionedSearchService.search(query, docId, version, topK));
    }
}