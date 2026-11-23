# Story 2: Dynamic Ingestion Resources (`ingest-dynamic`)

## Overview
The `ingest-dynamic` microservice handles dynamic external data ingestion, non-destructive document versioning, and version-aware retrieval. It follows a strict Layered Architecture (`controller/`, `service/`, `repository/`, `dto/`, `config/`) and is built to be completely domain-agnostic so it can serve any industry without code changes.

---

## Progress Summary

| Task | Description | Status |
| :--- | :--- | :--- |
| **Task 1: Internet Ingestion** | Multi-source fetching (`WEB_PAGE`, `REMOTE_PDF`, `REPO_DOC`), boilerplate cleanup, metadata enrichment, and batch error isolation | **Done** |
| **Task 2: Versioning** | Non-destructive version archiving (`version`, `is_latest`, `valid_from`), duplicate guards, and latest vs. historical retrieval | **Done** |
| **Task 3: Automation** | Folder watcher (<30s), HMAC webhook receiver, event deduplication, retry queue with backoff, and async large-file (>10 MB) ingestion | **Pending** |
| **Task 4: Security** | SSRF protection, domain allowlist, HTTPS enforcement, payload size/content-type limits, and script sanitization | **Pending** |

---

## Completed Tasks

### Task 1: Internet Ingestion — **Done**

#### Implementation Details
* **HTTP Entry Point & Validation (`DynamicIngestController`):**
    * Exposes `POST /api/v1/ingest/dynamic/internet` (accessible via the API Gateway at `/ingest-dynamic/api/v1/ingest/dynamic/internet`).
    * Validates that the request payload and `sources` list are non-null and non-empty—returning `400 Bad Request` on invalid input—and delegates batch execution to `InternetIngestionService`.
* **Multi-Source Fetching & Parsing (`InternetIngestionService`):**
    * **`WEB_PAGE`:** Fetches HTML via Jsoup using a configurable timeout (`app.ingest.fetch-timeout-ms`), strips boilerplate elements defined in `app.ingest.boilerplate-selectors` (`nav`, `footer`, `header`, `aside`, `script`, `style`, `noscript`, `iframe`, `.cookie-banner`, `#cookie-banner`, `.cookie-consent`, `.advertisement`), extracts the document title and clean body text, and rejects blank pages.
    * **`REMOTE_PDF`:** Downloads remote PDF bytes via Java's `HttpClient` (verifying HTTP 2xx status codes) and parses pages into Spring AI `Document` objects using `PagePdfDocumentReader`.
    * **`REPO_DOC`:** Downloads raw UTF-8 text or Markdown documentation from third-party repositories via `HttpClient` and validates that the content is non-empty.
* **Chunking & Metadata Schema:**
    * Splits extracted documents into chunks via Spring AI's `TokenTextSplitter` (`app.ingest.chunk-size`, `app.ingest.min-chunk-size-chars`).
    * Attaches a standardized metadata payload to every chunk before persisting via `VectorStore.add(...)`:
        * `doc_id` — Logical document identifier (defaults to the source `url` if omitted).
        * `source_url` & `source` — Original URL of the ingested resource.
        * `fetch_date` — ISO-8601 UTC timestamp recording when the resource was fetched.
        * `source_type` — Source category (`WEB_PAGE`, `REMOTE_PDF`, or `REPO_DOC`).
        * `org_unit` — Organizational unit identifier (defaults to `"default"` if omitted) to support downstream multi-tenant isolation filters.
        * `chunk_index` — Zero-based sequential chunk position within the source document to support downstream neighbor stitching.
* **Batch Failure Isolation & Logging:**
    * Processes each `SourceItem` inside an independent `try/catch` block. When a URL is unreachable, returns a non-2xx HTTP status, or yields empty content, the service logs an `ERROR` with details, records `success: false` in `BatchIngestResult.outcomes`, and continues ingesting the remaining sources in the batch.

#### Endpoint & Payloads
* **Endpoint:** `POST /api/v1/ingest/dynamic/internet`

**Example Request (`InternetIngestRequest`):**
```json
{
  "sources": [
    {
      "url": "[https://example.org/handbook](https://example.org/handbook)",
      "type": "WEB_PAGE",
      "docId": "sample-handbook",
      "orgUnit": "unit-a"
    },
    {
      "url": "[https://example.org/guide.pdf](https://example.org/guide.pdf)",
      "type": "REMOTE_PDF",
      "docId": "sample-pdf-guide",
      "orgUnit": "unit-a"
    },
    {
      "url": "[https://example.org/raw/README.md](https://example.org/raw/README.md)",
      "type": "REPO_DOC",
      "docId": "sample-repo-readme"
    }
  ]
}
```

**Response Structure (`BatchIngestResult`):**
* Returns `totalRequested`, `succeeded`, `failed`, and `outcomes` (each entry containing `url`, `success`, `chunksStored`, and a status or error `message`).

#### Automated Tests
* **`InternetIngestionServiceTest`:** Uses a local embedded `HttpServer` and mocked `VectorStore` to verify HTML boilerplate removal, remote PDF parsing, third-party repository document ingestion, metadata enrichment, and single-source failure isolation within a batch.
* **`DynamicIngestControllerTest`:** Verifies HTTP `200 OK` JSON responses for valid batches and HTTP `400 Bad Request` responses when `sources` is empty.

---

### Task 2: Versioning — **Done**

#### Implementation Details
* **Non-Destructive Version Persistence (`VectorVersionRepository` + `InternetIngestionService`):**
    * **Version Resolution & Duplicate Guard:** Accepts an optional `version` string per `SourceItem` (e.g., `"v1"`, `"v2"`) or auto-increments (`"v" + (countDistinctVersions + 1)`) when omitted. Queries the vector table before fetching to reject duplicate `(doc_id, version)` pairs so existing versions cannot be overwritten.
    * **Archiving Previous Versions:** Within a `@Transactional` boundary, `VectorVersionRepository.markPreviousVersionsNotLatest(docId)` executes a SQL `UPDATE` using PostgreSQL `jsonb_set` on the metadata column to set `is_latest = false` for all active chunks belonging to that `doc_id`. Previous chunks and embeddings remain in the store with their original `valid_from` timestamps preserved.
    * **Active Version Metadata:** Every newly inserted chunk is tagged with `version`, `is_latest: true`, and `valid_from` (ISO-8601 UTC timestamp).
* **Version-Aware Search (`VersionedSearchService` & `DynamicIngestController`):**
    * Exposes `GET /api/v1/ingest/dynamic/search` accepting `query` (required), `docId` (optional), `version` (optional), and `topK` (optional, default `5`), returning `400 Bad Request` for blank queries or `topK <= 0`.
    * Constructs metadata filter expressions via Spring AI's `FilterExpressionBuilder`:
        * **Default Search (`version` omitted):** Filters by `is_latest == true` (combined with `doc_id == <docId>` when supplied) so queries return only the current active version.
        * **Explicit Version Search (`version` provided, e.g., `version=v1`):** Filters by `version == <version>` so older versions can be retrieved and audited on demand.

#### Endpoints & Payloads
* **Ingest a Specific Version:** `POST /api/v1/ingest/dynamic/internet` (include an optional `"version": "v1"` field in any source object).
* **Search Latest Version (Default):**
  ```http
  GET /api/v1/ingest/dynamic/search?query=policy+guidelines&docId=sample-handbook&topK=5
  ```
* **Search a Specific Older Version:**
  ```http
  GET /api/v1/ingest/dynamic/search?query=policy+guidelines&docId=sample-handbook&version=v1&topK=5
  ```

**Response Structure (`VersionedSearchResult`):**
* Returns `query`, `requestedVersion` (`"latest"` when omitted, or the explicit version requested), `totalHits`, and `hits` (each containing chunk `id`, `content`, and full `metadata`).

#### Automated Tests
* **`DocumentVersioningTest`:** Simulates an in-memory vector store honoring `Filter.Expression` across two versions (`v1` and `v2`) of the same `doc_id`:
    * Verifies that ingesting `v2` retains `v1` chunks in the store with `is_latest=false` and their original `valid_from` intact.
    * Verifies that default search returns only `v2` (`is_latest=true`).
    * Verifies that explicit search with `version="v1"` returns `v1` (`is_latest=false`).
    * Verifies failure paths: duplicate `(doc_id, version)` ingestion is rejected without corrupting stored chunks, and blank search queries throw `IllegalArgumentException`.

---

## Pending Tasks

### Task 3: Automation — **Pending**
* [ ] **File Watcher:** Monitor an external folder and detect added, modified, or deleted files within 30 seconds to trigger automatic ingestion.
* [ ] **Vendor Webhook Receiver:** Expose a webhook endpoint that validates HMAC signatures, rejects unauthorized requests with `401 Unauthorized`, and triggers ingestion automatically.
* [ ] **Deduplication & Retry Queue:** Ignore duplicate notifications and retry failed ingestion jobs with backoff and visible error logs.
* [ ] **Asynchronous Large-File Processing:** Process uploads exceeding 10 MB in the background without blocking the caller.

### Task 4: Security — **Pending**
* [ ] **SSRF Protection:** Block outbound requests targeting `localhost`, loopback addresses, private network IP ranges, and cloud metadata endpoints.
* [ ] **Fetch Controls:** Enforce a domain allowlist, HTTPS-only URLs, connection/read timeouts, maximum payload size limits, and allowed content-type checks.
* [ ] **Content Sanitization:** Strip scripts and active content from fetched web pages.
* [ ] **Negative Security Tests:** Verify each security rule with automated tests attempting blocked requests.