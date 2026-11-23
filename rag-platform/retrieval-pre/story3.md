# `retrieval-pre` — Story 3: Pre-Retrieval Pipeline

Spring Boot 4 + Spring AI 2 microservice handling pre-retrieval validation, safety, and query preparation before querying `pgvector`.

Routed through `api-gateway` at `/api/v1/retrieve/pre/**`.

---

## Implemented Tasks

### Task 1: Safe Metadata Filtering

- **Strict URL-to-Object Parsing**: All URL filter parameters are parsed into an immutable validated `MetadataFilter` object and converted into a Spring AI `Filter.Expression` via `FilterExpressionBuilder`. Raw strings never reach the vector database.
- **Whitelisted Fields & Operators**: Configured externally in `src/main/resources/application.properties` (`app.filter.allowed-fields.*` and `app.filter.allowed-operators`) with strict type validation (`STRING`, `INTEGER`, `DOUBLE`, `BOOLEAN`).
- **Explicit 400 Error Responses**: Any unknown query parameter, non-whitelisted field, disallowed operator, invalid logical operator, or type mismatch returns `400 Bad Request` listing `validFields` and `validOperators`.
- **Multi-Variable `AND` / `OR` Support**:
  - Repeatable `filter=field:op:value` with `logic=AND` (default) or `logic=OR`
  - Direct URL parameters (`doc_id=doc-001`) and bracket operators (`page[gte]=2`)
  - Combined `AND`/`OR` groups via repeatable `filterGroup=field1:op:val1;field2:op:val2` (conditions inside each group are `AND`-ed; groups are `OR`-ed by default)
- **Null Filter Fall-Through**: When no filter parameters are provided, the service falls through to standard unfiltered retrieval.

---

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/retrieve/pre/search` | Executes pre-retrieval search with optional validated metadata filters |
| `GET` | `/api/v1/retrieve/pre/filters/metadata` | Returns the configured whitelisted metadata fields, types, and operators |

### Example Requests

- **Null filter (standard flow)**:
  ```http
  GET /api/v1/retrieve/pre/search?query=compliance%20requirements&topK=5
  ```
- **Multi-variable `AND` filter**:
  ```http
  GET /api/v1/retrieve/pre/search?query=retention%20policy&filter=source:eq:handbook.pdf&filter=page:gte:2&filter=is_latest:eq:true&logic=AND
  ```
- **Multi-variable `OR` filter**:
  ```http
  GET /api/v1/retrieve/pre/search?query=audit%20scope&filter=doc_id:eq:doc-001&filter=doc_id:eq:doc-002&logic=OR
  ```
- **Combined `AND` + `OR` groups**:
  ```http
  GET /api/v1/retrieve/pre/search?query=governance&filterGroup=org_unit:eq:unit-a;page:gte:2&filterGroup=org_unit:eq:unit-b;is_latest:eq:true
  ```

---

## Configuration

All secrets are supplied via environment variables (never committed to git). Schema creation is owned exclusively by `ingest-static` (`spring.ai.vectorstore.pgvector.initialize-schema=false`).

| Property | Env Var | Default | Description |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8083` | Service HTTP port |
| `eureka.client.service-url.defaultZone` | `EUREKA_URI` | `http://localhost:8761/eureka/` | Eureka registry URL |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ragdb` | PostgreSQL JDBC URL |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `rag` | Database user |
| `spring.datasource.password` | `POSTGRES_PASSWORD` | *(empty)* | Database password |
| `spring.ai.openai.api-key` | `LLM_API_KEY` | *(empty)* | LLM / embedding API key |
| `app.filter.default-top-k` | `APP_RETRIEVAL_DEFAULT_TOP_K` | `5` | Default `topK` |
| `app.filter.max-top-k` | `APP_RETRIEVAL_MAX_TOP_K` | `50` | Maximum `topK` |
| `app.filter.max-conditions` | `APP_FILTER_MAX_CONDITIONS` | `20` | Max metadata filter conditions per request |
| `app.filter.max-value-length` | `APP_FILTER_MAX_VALUE_LENGTH` | `256` | Max length of a single filter value |

### Default Whitelisted Metadata Fields

`doc_id`, `version`, `source`, `source_url`, `source_type`, `page`, `chunk_index`, `checksum`, `is_latest`, `valid_from`, `fetch_date`, `org_unit`, `organization_unit`, `security_clearance`.

### Default Whitelisted Operators

`eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `in`.

---

## Running Tests & Postman Collection

Run all unit tests (JUnit 5 + Mockito) and `@SpringBootTest` integration tests without requiring a running PostgreSQL or LLM instance:

```bash
mvn -f retrieval-pre/pom.xml test
```

Import the Postman collection from:
`retrieval-pre/postman/retrieval-pre-task1.postman_collection.json`
