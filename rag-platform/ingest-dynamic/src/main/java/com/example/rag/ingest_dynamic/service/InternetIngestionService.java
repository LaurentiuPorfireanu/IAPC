package com.example.rag.ingest_dynamic.service;

import com.example.rag.ingest_dynamic.dto.BatchIngestResult;
import com.example.rag.ingest_dynamic.dto.BatchIngestResult.SourceOutcome;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest.SourceItem;
import com.example.rag.ingest_dynamic.repository.VectorVersionRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InternetIngestionService {

    private static final Logger log = LoggerFactory.getLogger(InternetIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final VectorVersionRepository versionRepository;
    private final HttpClient httpClient;
    private final int timeoutMs;
    private final String boilerplateSelectors;

    public InternetIngestionService(
            VectorStore vectorStore,
            TokenTextSplitter textSplitter,
            VectorVersionRepository versionRepository,
            @Value("${app.ingest.fetch-timeout-ms:10000}") int timeoutMs,
            @Value("${app.ingest.boilerplate-selectors:nav,footer,header,aside,script,style,.cookie-banner}") String boilerplateSelectors) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
        this.versionRepository = versionRepository;
        this.timeoutMs = timeoutMs;
        this.boilerplateSelectors = boilerplateSelectors;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public BatchIngestResult ingestBatch(InternetIngestRequest request) {
        long startTime = System.currentTimeMillis();
        int total = request.sources().size();
        log.info("Starting dynamic internet ingestion batch: {} source(s)", total);

        List<SourceOutcome> outcomes = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (SourceItem item : request.sources()) {
            try {
                log.info("Fetching source [type={}, url={}, version={}]", item.type(), item.url(), item.version());
                int chunks = ingestSingleSource(item);
                succeeded++;
                outcomes.add(new SourceOutcome(item.url(), true, chunks, "Ingested successfully"));
                log.info("Stored {} chunk(s) for source [type={}, url={}]", chunks, item.type(), item.url());
            } catch (Exception ex) {
                failed++;
                log.error("Failed to ingest source [type={}, url={}]: {}", item.type(), item.url(), ex.getMessage(), ex);
                outcomes.add(new SourceOutcome(item.url(), false, 0, ex.getMessage()));
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Completed ingestion batch in {}ms [total={}, succeeded={}, failed={}]",
                durationMs, total, succeeded, failed);

        return new BatchIngestResult(total, succeeded, failed, outcomes);
    }

    @Transactional
    protected int ingestSingleSource(SourceItem item) throws IOException, InterruptedException {
        String now = Instant.now().toString();
        String docId = (item.docId() != null && !item.docId().isBlank()) ? item.docId() : item.url();
        String orgUnit = (item.orgUnit() != null && !item.orgUnit().isBlank()) ? item.orgUnit() : "default";
        String version = resolveVersion(docId, item.version());

        if (versionRepository.versionExists(docId, version)) {
            throw new IllegalArgumentException(
                    "Version '" + version + "' already exists for doc_id='" + docId + "'. Provide a new version.");
        }

        List<Document> rawDocs = switch (item.type()) {
            case WEB_PAGE -> fetchWebPage(item.url());
            case REMOTE_PDF -> fetchRemotePdf(item.url());
            case REPO_DOC -> fetchRepoDoc(item.url());
        };

        List<Document> chunks = textSplitter.apply(rawDocs);
        List<Document> enrichedChunks = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.put("doc_id", docId);
            metadata.put("source_url", item.url());
            metadata.put("org_unit", orgUnit);
            metadata.put("source", item.url());
            metadata.put("fetch_date", now);
            metadata.put("source_type", item.type().name());
            metadata.put("chunk_index", i);
            metadata.put("version", version);
            metadata.put("is_latest", true);
            metadata.put("valid_from", now);

            enrichedChunks.add(new Document(chunk.getText(), metadata));
        }

        if (!enrichedChunks.isEmpty()) {
            // Keep old chunks in the store, only flipping their is_latest flag to false
            versionRepository.markPreviousVersionsNotLatest(docId);
            vectorStore.add(enrichedChunks);
            log.info("Activated doc_id={} version={} (is_latest=true, valid_from={})", docId, version, now);
        }

        return enrichedChunks.size();
    }

    private String resolveVersion(String docId, String requestedVersion) {
        if (requestedVersion != null && !requestedVersion.isBlank()) {
            return requestedVersion.trim();
        }
        int nextVersionNumber = versionRepository.countDistinctVersions(docId) + 1;
        return "v" + nextVersionNumber;
    }

    private List<Document> fetchWebPage(String url) throws IOException {
        org.jsoup.nodes.Document htmlDoc = Jsoup.connect(url)
                .timeout(timeoutMs)
                .get();

        htmlDoc.select(boilerplateSelectors).remove();

        String cleanText = htmlDoc.body().text();
        if (cleanText.isBlank()) {
            throw new IllegalStateException("Extracted web page text is empty for URL: " + url);
        }

        return List.of(new Document(cleanText, Map.of("title", htmlDoc.title())));
    }

    private List<Document> fetchRemotePdf(String url) throws IOException, InterruptedException {
        byte[] pdfBytes = downloadBytes(url);
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new ByteArrayResource(pdfBytes));
        return pdfReader.get();
    }

    private List<Document> fetchRepoDoc(String url) throws IOException, InterruptedException {
        byte[] contentBytes = downloadBytes(url);
        String text = new String(contentBytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new IllegalStateException("Repository document is empty for URL: " + url);
        }
        return List.of(new Document(text));
    }

    private byte[] downloadBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " while fetching " + url);
        }
        return response.body();
    }
}