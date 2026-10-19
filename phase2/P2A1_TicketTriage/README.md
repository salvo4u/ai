# P2A1 Ticket Triage

A production-oriented **FastAPI** service that triages customer support tickets using an LLM pipeline.

It accepts a structured ticket payload, redacts PII, classifies category + urgency, generates a draft reply, applies confidence-based routing, tracks token/cost budgets, and streams the final structured result.

---

## Features

- **PII-aware preprocessing** – strips signatures, quoted replies, and redacts SSN, credit-card numbers, emails, and phone numbers before any LLM call
- **Structured LLM classification** – category, urgency (P1–P4), confidence score, reasoning, and draft reply
- **Schema validation + self-repair** – JSON Schema validation with bounded retry/repair loop
- **Confidence-based routing** – high / low / very-low confidence paths that always keep a human in the loop
- **Streaming response** – token-level streaming with final validated pipeline output
- **Budget & usage tracking** – per-request token ceiling + daily USD budget enforcement, CSV usage log
- **Request logging** – full audit trail of request, raw LLM output, and validated result
- **Usage API** – aggregate daily token/cost usage by customer

---

## Project Structure

```
P2A1_TicketTriage/
├── src/
│   ├── main.py                     # FastAPI app entry point
│   ├── models/
│   │   └── ticket_models.py        # Pydantic request/response models
│   ├── routes/
│   │   ├── ticket_routes.py        # POST /api/tickets/triage
│   │   └── usage_routes.py         # GET  /api/usage
│   ├── services/
│   │   ├── llm/
│   │   │   ├── client.py
│   │   │   └── prompts.py          # System & user prompt builders
│   │   ├── preprocessing/
│   │   │   └── preprocess_text.py  # Signature/quote stripping + PII redaction
│   │   └── validations/
│   │       ├── schema.py           # JSON Schema for LLM output
│   │       └── validator.py
│   ├── streaming/
│   │   └── stream_gpt.py           # OpenAI streaming + bounded retry
│   └── telemetry/
│       ├── budget_tracker.py       # Token & cost limits + CSV logging
│       ├── request_logger.py
│       └── telemetry.py
├── tests/
├── requirements.txt
└── README.md
```

---

## Quick Start

### 1. Prerequisites

- Python 3.10+
- OpenAI API key

### 2. Setup

```bash
# Create and activate a virtual environment
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

# Install dependencies
python -m pip install -r requirements.txt

# Set your OpenAI key (or put it in a .env file)
export OPENAI_API_KEY=sk-...
```

### 3. Run the service

```bash
python -m uvicorn src.main:app --host 0.0.0.0 --port 8081 --reload
```

Service is available at `http://localhost:8081`  
Interactive docs: `http://localhost:8081/docs`

---

## API

### Health Check

```bash
curl http://localhost:8081/health
```

```json
{"status": "ok"}
```

### Triage a Ticket

`POST /api/tickets/triage`

**Content-Type:** `application/json`

**Request body** (Pydantic `TicketRequest`):

```json
{
  "ticketId": "TKT-2026-08-15-001",
  "sourceSystem": "email",
  "receivedAt": "2026-08-15T10:30:00Z",
  "rawText": "I'm unable to access my dashboard since this morning. I keep getting a 503 error...",
  "subject": "URGENT: Dashboard down - 503 error",
  "customerContext": {
    "customerId": "cust_prem_7890xyz",
    "customerTier": "premium",
    "accountAgeDays": 456,
    "preferredLanguage": "en",
    "region": "us-east",
    "previousTickets": [
      {
        "ticketId": "TKT-2026-08-10-023",
        "category": "technical",
        "summary": "API rate limit issue - resolved"
      }
    ]
  },
  "agingMetrics": {
    "secondsSinceCreation": 1800,
    "secondsSinceLastAgentProcessed": 1800,
    "escalations": 0
  },
  "routingHints": {
    "preferredLanguage": "en",
    "region": "us-east",
    "departmentHint": "technical-support",
    "requiresUrgentEscalation": true
  }
}
```

**Response** (streamed JSON – final event is a `PipelineOutput`):

```json
{
  "ticketId": "TKT-2026-08-15-001",
  "redactedText": "...",
  "redactionLog": [
    {"type": "Email", "original": "user@example.com", "replacement": "[REDACTED:Email]"}
  ],
  "classification": {
    "category": "technical",
    "urgency": "P1",
    "draftReply": "Thank you for reporting this. We are investigating the 503 error...",
    "confidenceScore": 0.92,
    "reasoning": {
      "urgencyRationale": "...",
      "categoryRationale": "...",
      "suggestedActions": ["Check service status", "Escalate to on-call"]
    },
    "modelVersion": "gpt-4o-mini",
    "tokensUsed": 847,
    "inferenceTimeMs": 1240
  },
  "routing": {
    "path": "HIGH_CONFIDENCE",
    "draftCanBeUsed": true,
    "reviewRequired": true,
    "reason": "High confidence classification",
    "priority": "urgent"
  },
  "status": "ready_for_review",
  "telemetry": { ... }
}
```

### Usage Summary

```bash
curl http://localhost:8081/api/usage
```

Returns daily token & cost aggregates per customer from `customer_usage.csv`.

---

## Pipeline Flow

1. **Receive** structured `TicketRequest`
2. **Preprocess** – remove signatures & quoted replies, redact PII
3. **Build prompts** – system prompt (urgency/category guidelines + schema) + user prompt (context + redacted text)
4. **Budget gate** – enforce per-request token ceiling
5. **Stream LLM** – OpenAI streaming with bounded validation/repair retries
6. **Validate** – JSON Schema check; self-repair on failure
7. **Route** – confidence tiers (≥0.7 / 0.5–0.7 / <0.5)
8. **Enforce daily budget** & log usage
9. **Return** full `PipelineOutput` (streaming)

---

## Configuration

| Setting                    | Default       | Description                          |
|----------------------------|---------------|--------------------------------------|
| `MAX_TOKENS_PER_REQUEST`   | 2000          | Hard limit per triage call           |
| `DAILY_BUDGET_USD`         | 10.0          | Per-customer daily spend ceiling     |
| Model                      | `gpt-4o-mini` | Default OpenAI model                 |

Environment variables are loaded via `python-dotenv` (`.env` file supported).

---

## Testing

```bash
python -m pytest
```

---

## Design Principles

- **Privacy-first** – PII is redacted before any LLM call; original values stay in an audit log
- **Human-in-the-loop** – even high-confidence drafts require review
- **Safe fallbacks** – validation failures after retries produce a safe default response
- **Observability** – every request, token usage, cost, and validation outcome is logged
- **Cost control** – hard token and daily budget limits prevent runaway spend

---

## Dependencies

```
fastapi
pydantic>=2,<3
uvicorn[standard]
pytest
httpx
openai
jsonschema
python-dotenv
```

---

## License

Part of the [salvo4u/ai](https://github.com/salvo4u/ai) portfolio.
```