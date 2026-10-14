from fastapi import APIRouter, Body, HTTPException

from models.ticket_models import TicketRequest
from src.services.preprocessing.ticket_text import preprocess_ticket_text
from datetime import datetime

router = APIRouter(prefix="/api/tickets", tags=["tickets"])

@router.post("/triage")
async def triage_ticket(ticket: TicketRequest):
    # Mandatory fields check
    if not ticket.ticketId:
        return {"error": "Missing mandatory field: ticketId"}
    if not ticket.rawText:
        return {"error": "Missing mandatory field: rawText"}
    if not ticket.subject:
        return {"error": "Missing mandatory field: subject"}
    if not ticket.customerContext or not ticket.customerContext.customerTier:
        return {"error": "Missing mandatory field: customerContext.customerTier"}
    if not ticket.routingHints or not ticket.routingHints.departmentHint:
        return {"error": "Missing mandatory field: routingHints.departmentHint"}

    # Access the strongly typed request fields
    ticket_id = ticket.ticketId
    ticket_text = ticket.rawText
    customer_tier = ticket.customerContext.customerTier
    department = ticket.routingHints.departmentHint


    # TODO: Call preprocessing, validation, and LLM services here.
    ticket_text_redacted = preprocess_ticket_text(ticket_text)
    print('redacted text is ' + ticket_text_redacted)


    return {
        "status": "received",
        "ticketId": ticket_id,
        "subject": ticket.subject,
        "customerTier": customer_tier,
        "department": department,
        "message": "Ticket accepted for triage.",
        "timestamp":datetime.now()
    }

