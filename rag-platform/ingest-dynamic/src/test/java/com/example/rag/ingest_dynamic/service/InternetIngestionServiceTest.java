package com.example.rag.ingest_dynamic.service;

import com.example.rag.ingest_dynamic.dto.BatchIngestResult;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest.SourceItem;
import com.example.rag.ingest_dynamic.dto.InternetIngestRequest.SourceType;
import com.example.rag.ingest_dynamic.repository.VectorVersionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternetIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private VectorVersionRepository versionRepository;
    private InternetIngestionService service;
    private HttpServer mockServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        String selectors = "nav, footer, header, aside, script, style, .cookie-banner";
        service = new InternetIngestionService(vectorStore, splitter, versionRepository, 5000, selectors);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/page", exchange -> {
            String html = """
                    <html>
                      <head><title>Sample Protocol</title></head>
                      <body>
                        <nav>Home | About | Contact</nav>
                        <div class="cookie-banner">Accept all cookies</div>
                        <main><p>Core clinical documentation content for vector storage.</p></main>
                        <footer>Copyright 2026</footer>
                      </body>
                    </html>
                    """;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        mockServer.createContext("/repo-doc.md", exchange -> {
            String markdown = "# Reference Guide\n\nStructured documentation content from a third-party repository.";
            byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/markdown; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        mockServer.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        mockServer.createContext("/sample.pdf", exchange -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PDDocument pdf = new PDDocument()) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText("Remote PDF clinical guideline content for automated testing.");
                    cs.endText();
                }
                pdf.save(baos);
            }
            byte[] pdfBytes = baos.toByteArray();
            exchange.getResponseHeaders().add("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdfBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(pdfBytes);
            }
        });

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
    @SuppressWarnings("unchecked")
    void ingestBatch_SuccessPath_StripsBoilerplateAndStoresMetadata() {
        String pageUrl = baseUrl + "/page";
        SourceItem webItem = new SourceItem(pageUrl, SourceType.WEB_PAGE, "doc-web-1");
        InternetIngestRequest request = new InternetIngestRequest(List.of(webItem));

        BatchIngestResult result = service.ingestBatch(request);

        assertEquals(1, result.totalRequested());
        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());
        assertTrue(result.outcomes().get(0).success());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> storedDocs = captor.getValue();
        assertFalse(storedDocs.isEmpty());

        Document firstChunk = storedDocs.get(0);
        String text = firstChunk.getText();
        assertTrue(text.contains("Core clinical documentation content"));
        assertFalse(text.contains("Accept all cookies"));
        assertFalse(text.contains("Copyright 2026"));
        assertFalse(text.contains("Home | About"));

        assertEquals("doc-web-1", firstChunk.getMetadata().get("doc_id"));
        assertEquals(pageUrl, firstChunk.getMetadata().get("source_url"));
        assertNotNull(firstChunk.getMetadata().get("fetch_date"));
    }

    @Test
    void ingestBatch_FailureIsolation_OneFailedSourceDoesNotStopBatch() {
        SourceItem brokenItem = new SourceItem(baseUrl + "/broken", SourceType.REPO_DOC, "doc-broken");
        SourceItem validRepoItem = new SourceItem(baseUrl + "/repo-doc.md", SourceType.REPO_DOC, "doc-repo-1");
        InternetIngestRequest request = new InternetIngestRequest(List.of(brokenItem, validRepoItem));

        BatchIngestResult result = service.ingestBatch(request);

        assertEquals(2, result.totalRequested());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());

        assertFalse(result.outcomes().get(0).success());
        assertTrue(result.outcomes().get(1).success());
        verify(vectorStore, times(1)).add(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestBatch_RemotePdf_ParsesAndStoresWithMetadata() {
        String pdfUrl = baseUrl + "/sample.pdf";
        SourceItem pdfItem = new SourceItem(pdfUrl, SourceType.REMOTE_PDF, "doc-pdf-1");
        InternetIngestRequest request = new InternetIngestRequest(List.of(pdfItem));

        BatchIngestResult result = service.ingestBatch(request);

        assertEquals(1, result.succeeded());
        assertEquals(0, result.failed());

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        Document chunk = captor.getValue().get(0);
        String normalizedText = chunk.getText().replaceAll("\\s+", " ").trim();
        assertTrue(
                normalizedText.contains("Remote PDF clinical guideline content"),
                "Unexpected extracted PDF text: " + normalizedText
        );
        assertEquals("doc-pdf-1", chunk.getMetadata().get("doc_id"));
        assertEquals(pdfUrl, chunk.getMetadata().get("source_url"));
        assertNotNull(chunk.getMetadata().get("fetch_date"));
    }
}