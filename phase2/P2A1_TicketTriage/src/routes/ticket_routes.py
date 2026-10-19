import json
import uuid

from fastapi import APIRouter

from services.llm.prompts import build_system_prompt, build_user_prompt
from streaming.stream_gpt import stream_chat_gpt
from models.ticket_models import TicketRequest, LLMResponse, RoutingDecision, PipelineOutput, RedactionResult
from services.preprocessing.preprocess_text import preprocess_ticket_text
from fastapi.responses import StreamingResponse
from telemetry import budget_tracker
from telemetry.request_logger import log_request

router = APIRouter(prefix="/api/tickets", tags=["tickets"])

def route(response: LLMResponse) -> RoutingDecision:
    confidence = response.confidenceScore
    urgency = response.urgency

    if confidence >= 0.7:
        return RoutingDecision(
            decisionType="HIGH_CONFIDENCE",
            autoRoute=True,
            requiresHumanReview=True,
            rationale="High confidence classification",
            priority="urgent" if urgency == "P1" else "normal"
        )
    elif confidence >= 0.5:
        return RoutingDecision(
            decisionType="LOW_CONFIDENCE",
            autoRoute=False,
            requiresHumanReview=True,
            rationale="Low confidence - requires human review",
            priority="normal"
        )
    else:
        return RoutingDecision(
            decisionType="VERY_LOW_CONFIDENCE",
            autoRoute=False,
            requiresHumanReview=True,
            rationale="Very low confidence - manual classification required",
            priority="normal"
        )


def build_pipeline_output(
        request: TicketRequest,
        redaction_result: RedactionResult,
        response: LLMResponse,
        routing: RoutingDecision,
        telemetry: dict,
) -> PipelineOutput:
    return PipelineOutput(
        ticketId=request.ticketId or str(uuid.uuid4()),  # auto-generate if missing
        redactedText=redaction_result.result,
        redactionLog=redaction_result.log,              # full log of entries
        classification=response,
        routing=routing,
        status="ready_for_review" if routing.reviewRequired else "auto_approved",
        telemetry=telemetry,
    )


@router.post("/triage")
async def triage_ticket(ticket: TicketRequest):
    # Preprocess...
    system_prompt = build_system_prompt()
    # Preprocess text → returns RedactionResult
    redaction_result = preprocess_ticket_text(ticket.rawText)
    user_prompt = build_user_prompt(ticket, redaction_result.result)
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt}
    ]

    # Estimate tokens before request
    estimated_tokens = sum(len(m["content"].split()) for m in messages)  # simple estimate
    budget_tracker.enforce_request_limit(estimated_tokens)

    # Call LLM
    async def stream_validated():
        for event in stream_chat_gpt(messages, system_prompt, model="gpt-4o-mini"):
            if event["type"] == "completed":
                validated = event["validated"]
                telemetry = event["telemetry"]

                # Enforce daily budget
                budget_tracker.enforce_daily_budget(ticket.customerContext.customerTier, telemetry["cost_usd"])

                # Log usage
                budget_tracker.log_usage(
                    customer_id=ticket.customerContext.customerTier,
                    ticket_id=ticket.ticketId,
                    tokens_used=telemetry["total_tokens"],
                    cost_usd=telemetry["cost_usd"]
                )

                # Build pipeline output...
                llm_response = LLMResponse(**validated)
                decision = route(llm_response)
                output = build_pipeline_output(ticket, redaction_result, llm_response, decision, telemetry)
                # Persist to log file
                log_request(
                    ticket_id=ticket.ticketId,
                    customer_id=ticket.customerContext.customerTier,
                    request=ticket.dict(),
                    raw_response=event.get("raw_buffer", ""),   # buffer before validation
                    tokens={
                        "prompt": telemetry["total_prompt_tokens"],
                        "completion": telemetry["total_completion_tokens"],
                        "total": telemetry["total_tokens"]
                    },
                    validated_output=validated
                )
                yield json.dumps(output.__dict__, indent=2)

    return StreamingResponse(stream_validated(), media_type="application/json")


