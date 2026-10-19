
from datetime import datetime, timezone
from typing import List

from pydantic import BaseModel, ConfigDict


class TicketModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


# ─── Request Models ─────────────────────────────────────────────

class PreviousTicket(TicketModel):
    ticketId: str
    category: str
    summary: str


class CustomerContext(TicketModel):
    customerId: str
    customerTier: str
    accountAgeDays: int
    preferredLanguage: str
    region: str
    previousTickets: List[PreviousTicket]


class AgingMetrics(TicketModel):
    secondsSinceCreation: int
    secondsSinceLastAgentProcessed: int
    escalations: int


class RoutingHints(TicketModel):
    preferredLanguage: str
    region: str
    departmentHint: str
    requiresUrgentEscalation: bool


class TicketRequest(TicketModel):
    ticketId: str
    sourceSystem: str
    receivedAt: datetime
    rawText: str
    subject: str
    customerContext: CustomerContext
    agingMetrics: AgingMetrics
    routingHints: RoutingHints


# ─── LLM Response Models ────────────────────────────────────────────

class Reasoning(TicketModel):
    urgencyRationale: str
    categoryRationale: str
    suggestedActions: List[str]


class LLMResponse(TicketModel):
    category: str
    urgency: str
    draftReply: str
    confidenceScore: float
    reasoning: Reasoning
    modelVersion: str
    tokensUsed: int
    inferenceTimeMs: int


# ─── Pipeline Output ────────────────────────────────────────────



class RedactionEntry(TicketModel):
    type: str
    original: str
    replacement: str

class RedactionResult(TicketModel):
    result: str
    log: List[RedactionEntry]


class RoutingDecision(TicketModel):
    path: str
    draftCanBeUsed: bool
    reviewRequired: bool
    reason: str
    priority: str


class PipelineOutput(TicketModel):
    ticketId: str
    redactedText: str
    redactionLog: List[RedactionEntry]
    classification: LLMResponse
    routing: RoutingDecision
    status: str
    telemetry: dict


# ─── Helper Factory ─────────────────────────────────────────────

def sample_ticket() -> TicketRequest:
    return TicketRequest(
        ticketId="TKT-2026-08-15-001",
        sourceSystem="email",
        receivedAt=datetime.now(timezone.utc),
        rawText=(
            "I'm unable to access my dashboard since this morning. "
            "I keep getting a 503 error. I have an important client "
            "meeting in 30 minutes and need this resolved ASAP. "
            "I've tried clearing cache and using different browsers "
            "but nothing works. This is costing me business."
        ),
        subject="URGENT: Dashboard down - 503 error",
        customerContext=CustomerContext(
            customerId="cust_prem_7890xyz",
            customerTier="premium",
            accountAgeDays=456,
            preferredLanguage="en",
            region="us-east",
            previousTickets=[
                PreviousTicket(
                    ticketId="TKT-2026-08-10-023",
                    category="technical",
                    summary="API rate limit issue - resolved",
                ),
                PreviousTicket(
                    ticketId="TKT-2026-07-28-089",
                    category="billing",
                    summary="Invoice discrepancy - corrected",
                ),
            ],
        ),
        agingMetrics=AgingMetrics(
            secondsSinceCreation=1800,
            secondsSinceLastAgentProcessed=1800,
            escalations=0,
        ),
        routingHints=RoutingHints(
            preferredLanguage="en",
            region="us-east",
            departmentHint="technical-support",
            requiresUrgentEscalation=True,
        ),
    )


if __name__ == "__main__":
    ticket = sample_ticket()
    print(ticket.model_dump_json(indent=2))
