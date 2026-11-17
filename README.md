# EduPulse AI - Physiotherapy

EduPulse AI is an Advanced RAG microservices platform designed specifically for the physiotherapy and healthcare sector. 

It automates compliance checks, gap analysis and clinical protocol retrieval using hybrid search, vector databases, and LLM orchestration.

## Team Distribution (Epic 1: RAG)
The project is developed by a 4-member team, with each member owning a dedicated microservice and story for the first epic:

* **Gabriel - Story 1:** Static Ingestion Resources (`ingest-static`). Responsible for PDF parsing, hierarchical chunking, atomic update/delete workflows, ingestion manifest, and initializing the `vector_store` schema.
* **Laurențiu - Story 2:** Dynamic Ingestion Resources (`ingest-dynamic`). Responsible for Internet ingestion (pages, repos), document versioning, automated file watchers/webhooks, and SSRF security guardrails.
* **Ruxandra - Story 3:** Pre-Retrieval Pipeline (`retrieval-pre`). Responsible for safe metadata filtering, ReDoS-safe query normalization, acronym expansion, and multi-query expansion (with result fusion).
* **Petre - Story 4:** Post-Retrieval & Grounding (`retrieval-post`). Responsible for cross-encoder reranking, neighbor stitching (overlap deduplication), strict citation headers, and building the golden set evaluation harness.

## Collaboration Guidelines & Architecture
To avoid merge conflicts and maintain our strict Layered Architecture, all team members must follow the core development rules: [📄 Download Basic Rules (PDF)](https://docs.google.com/document/d/1ZX1M_B68ZhQuF6Gvzah0ItpQLOInzQ8eirYZlH1oChY/export?format=pdf)

### Baseline for every task
* Code is peer-reviewed and merged to the main branch.
* Automated tests pass, with at least one test for the success path and one for a failure path.
* No secrets in the repository, and configuration is documented.
* Errors and key events are logged.
* The README or docs are updated, and the result is demonstrated to the product owner.