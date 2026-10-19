# AI Systems Design Portfolio

A collection of production-oriented GenAI system designs covering the full lifecycle of enterprise AI applications: classification, retrieval, generation, routing, evaluation, and safety.

Each project includes High-Level Design (HLD), Low-Level Design (LLD), decision logs, sequence/flow diagrams, and runnable Java proof-of-concepts where applicable.

---

## Projects Overview

| Project | Description | Key Focus |
|---------|-------------|-----------|
| **[P1A1 – Ticket System](./P1A1_Ticketsystem)** | AI-Powered Ticket Classification & Draft Reply | Classification, urgency scoring, draft generation, PII redaction, human-in-the-loop |
| **[P1A2 – Semantic Search](./P1A2_SematicSearch)** | Semantic Search for Internal Engineering Docs | Chunking strategy, embeddings, hybrid search, re-ranking, vector DB |
| **[P1A3 – RAG Chatbot](./P1A3_RAGChatBot)** | Internal Knowledge Assistant (RAG) | End-to-end RAG pipeline with grounding, citations, context assembly, feedback loop |
| **[P1A4 – Request Routing](./P1A4_RequestRouting)** | Multi-Model LLM Routing Layer | Cost/latency/quality-aware model selection with automatic fallback |
| **[P1A5 – RAG Evaluation](./P1A5_RAGEvaluation)** | RAG Evaluation Framework | Retrieval metrics (Recall@k, MRR, nDCG) + LLM-as-judge answer scoring |
| **[P1A6 – Guardrails](./P1A6_GaurdRails)** | Guardrail Layer for Enterprise AI | Input/output controls, authorization, PII protection, grounding verification, audit |

---

## Design Principles Across Projects

- **Privacy-first** — PII redaction happens before any LLM call
- **Human-in-the-loop** — LLM suggests; humans approve high-stakes actions
- **Grounded generation** — Answers must be supported by retrieved context + citations
- **Safe fallbacks** — Low confidence, timeouts, or policy violations route to human review
- **Observability** — Every decision is logged for audit and continuous improvement
- **Cost & latency awareness** — Right model for the right job

---

## Project Details

### P1A1 – AI Ticket Classification & Draft Reply
Classifies support tickets (category + urgency) and generates draft replies.  
Features confidence thresholds, PII redaction (regex + NER), token budgeting, and mandatory human review.

**Key artifacts**: HLD architecture, sequence diagram, flowchart, decision log, sample JSON output.

### P1A2 – Semantic Search
Batch ingestion → structure-aware chunking (800–1000 tokens + overlap) → embeddings → ChromaDB → hybrid retrieval + cross-encoder re-ranking.

**Key artifacts**: HLD/LLD, mermaid sequence diagrams, chunk metadata schema, decision log.

### P1A3 – RAG Chatbot (Internal Knowledge Assistant)
Full RAG pipeline:
1. Query preprocessing (PII redaction)
2. Semantic retrieval + hybrid re-ranking
3. Context assembly (dedup, token budget)
4. Grounded LLM generation with citations
5. Feedback loop for ranking improvement

Includes a proper multi-package Java Maven layout and runnable CLI.

### P1A4 – Multi-Model Routing Layer
Sits between application services and multiple LLM providers. Selects models based on:
- Task type & complexity
- Latency budget
- Cost ceiling
- Required capabilities

Includes automatic fallback on timeout / rate-limit / error and a pure-Java demo (`RoutingDemo.java`).

### P1A5 – RAG Evaluation Framework
Isolated evaluation pipeline that never touches production traffic:
- Versioned gold-standard dataset
- Retrieval metrics: Recall@k, Precision@k, MRR, nDCG, Hit Rate
- Answer metrics: Correctness, Faithfulness, Relevance, Completeness (LLM-as-judge)
- Regression detection vs baseline runs

Includes `RetrievalEvalDemo.java`.

### P1A6 – Guardrail Layer
Defense-in-depth for enterprise AI:
- **Input**: Authorization, PII detection, policy/scope checks (blocking)
- **Output**: Grounding verification, sensitive-data rescan, policy checks (degrading)
- Full audit trail with immutable storage

Includes `GuardrailDemo.java` showing permission-filtered retrieval.

---

## Tech Stack

- **Language**: Java 17+ (Maven projects + standalone demos)
- **Design**: HLD / LLD documents, sequence diagrams, decision logs
- **Vector Store**: ChromaDB (self-hosted) recommended in semantic search
- **Patterns**: RAG, hybrid search, model routing, guardrails, evaluation harnesses

---

## Getting Started

Most projects are documentation-first with optional Java demos.

```bash
# Example: run the routing demo
cd P1A4_RequestRouting
javac RoutingDemo.java
java RoutingDemo

# Example: run the RAG evaluation demo
cd P1A5_RAGEvaluation
javac RetrievalEvalDemo.java
java RetrievalEvalDemo

# Example: build the RAG Chatbot (Maven)
cd P1A3_RAGChatBot
mvn clean package
java -cp target/classes com.internal.knowledge.KnowledgeAssistant
