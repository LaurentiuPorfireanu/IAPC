# EduPulse AI - Physiotherapy

EduPulse AI is an Advanced RAG microservices platform designed specifically for the physiotherapy and healthcare sector. 

It automates compliance checks, gap analysis and clinical protocol retrieval using hybrid search, vector datab asesand LLM orchestration.

## Team Distribution
The project is developed by a 4-member team, with each member owning a dedicated microservice:
* **Gabriel:** Infrastructure, Security & Multi-Tenancy (`api-gateway`, `eureka-server`, `auth-service`)
* **Laurențiu:** Static & Dynamic Ingestion Service (Document parsing, hierarchical chunking, and `pgvector` initialization)
* **Ruxandra:** Pre-Retrieval Pipeline & Hybrid Search (Query normalization, acronym expansion, and metadata filtering)
* **Flaviu:** Post-Retrieval & Business Logic (Cross-encoder reranking, LLM grounding, and automated gap analysis)

## Collaboration Guidelines & Architecture
To avoid merge conflicts and maintain our strict Layered Architecture, all team members must follow the core development rules: [📄 Download Basic Rules (PDF)](https://docs.google.com/document/d/1ZX1M_B68ZhQuF6Gvzah0ItpQLOInzQ8eirYZlH1oChY/export?format=pdf)
