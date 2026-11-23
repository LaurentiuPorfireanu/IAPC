package com.example.rag.ingest_dynamic.service;

import com.example.rag.ingest_dynamic.dto.BatchIngestResult;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest.SourceItem;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest.SourceType;
import com.example.rag.ingest_dynamic.dto.VersionedSearchResult;
import com.example.rag.ingest_dynamic.repository.VectorVersionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DocumentVersioningTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private VectorVersionRepository versionRepository;

    private InternetIngestionService ingestionService;
    private VersionedSearchService searchService;
    private HttpServer mockServer;
    private String baseUrl;

    // In-memory store backing the mocks so we prove real two-version behavior without Docker
    private final List<Document> inMemoryStore = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        inMemoryStore.clear();

        // Simulate VectorStore.add(...)
        lenient().doAnswer(invocation -> {
            List<Document> docs = invocation.getArgument(0);
            inMemoryStore.addAll(docs);
            return null;
        }).when(vectorStore).add(anyList());

        // Simulate VectorStore.similaritySearch(...) honoring Filter.Expression
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest req = invocation.getArgument(0);
            Filter.Expression filter = req.getFilterExpression();
            return inMemoryStore.stream()
                    .filter(doc -> matchesFilter(doc, filter))
                    .limit(req.getTopK())
                    .toList();
        });

        // Simulate VectorVersionRepository marking old chunks is_latest=false
        lenient().when(versionRepository.markPreviousVersionsNotLatest(anyString())).thenAnswer(invocation -> {
            String docId = invocation.getArgument(0);
            int updated = 0;
            for (int i = 0; i < inMemoryStore.size(); i++) {
                Document doc = inMemoryStore.get(i);
                if (docId.equals(doc.getMetadata().get("doc_id"))
                        && Boolean.TRUE.equals(doc.getMetadata().get("is_latest"))) {
                    Map<String, Object> copy = new HashMap<>(doc.getMetadata());
                    copy.put("is_latest", false);
                    inMemoryStore.set(i, new Document(doc.getId(), doc.getText(), copy));
                    updated++;
                }
            }
            return updated;
        });

        lenient().when(versionRepository.versionExists(anyString(), anyString())).thenAnswer(invocation -> {
            String docId = invocation.getArgument(0);
            String version = invocation.getArgument(1);
            return inMemoryStore.stream().anyMatch(doc ->
                    docId.equals(doc.getMetadata().get("doc_id"))
                            && version.equals(doc.getMetadata().get("version")));
        });

        lenient().when(versionRepository.countDistinctVersions(anyString())).thenAnswer(invocation -> {
            String docId = invocation.getArgument(0);
            return (int) inMemoryStore.stream()
                    .filter(doc -> docId.equals(doc.getMetadata().get("doc_id")))
                    .map(doc -> doc.getMetadata().get("version"))
                    .distinct()
                    .count();
        });

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(100)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        ingestionService = new InternetIngestionService(vectorStore, splitter, versionRepository, 5000, "nav,footer");
        searchService = new VersionedSearchService(vectorStore);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/doc-v1.md", exchange -> sendText(exchange,
                "Protocol version one: initial baseline procedure approved in 2025."));
        mockServer.createContext("/doc-v2.md", exchange -> sendText(exchange,
                "Protocol version two: updated 2026 procedure replacing the baseline."));
        mockServer.start();
        baseUrl = "http://localhost:" + mockServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void twoVersionsOfSameDocument_DefaultReturnsLatestAndExplicitReturnsOlder() {
        String docId = "guideline-protocol-1";

        // 1. Ingest v1
        BatchIngestResult v1Result = ingestionService.ingestBatch(new InternetIngestRequest(List.of(
                new SourceItem(baseUrl + "/doc-v1.md", SourceType.REPO_DOC, docId, "unit-a", "v1")
        )));
        assertEquals(1, v1Result.succeeded());

        // 2. Ingest v2 for the same doc_id
        BatchIngestResult v2Result = ingestionService.ingestBatch(new InternetIngestRequest(List.of(
                new SourceItem(baseUrl + "/doc-v2.md", SourceType.REPO_DOC, docId, "unit-a", "v2")
        )));
        assertEquals(1, v2Result.succeeded());

        // Verify old v1 chunks stay in the store with is_latest=false and valid_from intact
        List<Document> v1Stored = inMemoryStore.stream()
                .filter(d -> "v1".equals(d.getMetadata().get("version")))
                .toList();
        assertFalse(v1Stored.isEmpty());
        assertEquals(false, v1Stored.get(0).getMetadata().get("is_latest"));
        assertNotNull(v1Stored.get(0).getMetadata().get("valid_from"));

        // Verify v2 chunks are marked is_latest=true with valid_from
        List<Document> v2Stored = inMemoryStore.stream()
                .filter(d -> "v2".equals(d.getMetadata().get("version")))
                .toList();
        assertFalse(v2Stored.isEmpty());
        assertEquals(true, v2Stored.get(0).getMetadata().get("is_latest"));
        assertNotNull(v2Stored.get(0).getMetadata().get("valid_from"));

        // 3. Default query (no version parameter) returns ONLY the latest version (v2)
        VersionedSearchResult defaultSearch = searchService.search("procedure", docId, null, 5);
        assertEquals("latest", defaultSearch.requestedVersion());
        assertEquals(1, defaultSearch.totalHits());
        assertTrue(defaultSearch.hits().get(0).content().contains("version two"));
        assertEquals("v2", defaultSearch.hits().get(0).metadata().get("version"));

        // 4. Explicit version query (version="v1") returns the older content
        VersionedSearchResult explicitV1Search = searchService.search("procedure", docId, "v1", 5);
        assertEquals("v1", explicitV1Search.requestedVersion());
        assertEquals(1, explicitV1Search.totalHits());
        assertTrue(explicitV1Search.hits().get(0).content().contains("version one"));
        assertEquals("v1", explicitV1Search.hits().get(0).metadata().get("version"));
    }

    @Test
    void duplicateVersion_IsRejectedWithoutCorruptingExistingChunks() {
        String docId = "guideline-protocol-dup";
        SourceItem v1 = new SourceItem(baseUrl + "/doc-v1.md", SourceType.REPO_DOC, docId, "unit-a", "v1");

        BatchIngestResult first = ingestionService.ingestBatch(new InternetIngestRequest(List.of(v1)));
        BatchIngestResult duplicate = ingestionService.ingestBatch(new InternetIngestRequest(List.of(v1)));

        assertEquals(1, first.succeeded());
        assertEquals(0, duplicate.succeeded());
        assertEquals(1, duplicate.failed());
        assertTrue(duplicate.outcomes().get(0).message().contains("already exists"));
        assertEquals(true, inMemoryStore.get(0).getMetadata().get("is_latest"));
    }

    @Test
    void search_BlankQuery_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> searchService.search("   ", null, null, 5));
    }

    private void sendText(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/markdown; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private boolean matchesFilter(Document doc, Filter.Expression expr) {
        if (expr == null) {
            return true;
        }
        return switch (expr.type()) {
            case EQ -> {
                String key = ((Filter.Key) expr.left()).key();
                Object expected = ((Filter.Value) expr.right()).value();
                yield expected.equals(doc.getMetadata().get(key));
            }
            case AND -> matchesFilter(doc, (Filter.Expression) expr.left())
                    && matchesFilter(doc, (Filter.Expression) expr.right());
            case OR -> matchesFilter(doc, (Filter.Expression) expr.left())
                    || matchesFilter(doc, (Filter.Expression) expr.right());
            default -> true;
        };
    }
}