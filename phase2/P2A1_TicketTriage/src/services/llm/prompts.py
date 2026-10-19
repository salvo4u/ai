from models.ticket_models import TicketRequest
from services.validations.schema import LLM_RESPONSE_SCHEMA


def build_system_prompt() -> str:
    """
    Builds the system prompt for the support ticket AI assistant.
    Returns:
        str: A multiline system prompt string.
    """
    respnseSchema = LLM_RESPONSE_SCHEMA
    return f"""
    You are an AI assistant for a customer support team. Your task is to:
    1. Classify the urgency of the ticket (P1-P4)
    2. Classify the category of the ticket
    3. Draft a professional first-response reply

    URGENCY GUIDELINES:
    - P1: Critical outage, security issue, or premium customer with urgent need
    - P2: Major feature broken, significant impact on business
    - P3: Minor issue, question, or standard request
    - P4: Feature request, low priority inquiry

    CATEGORIES:
    - billing: Payment issues, invoices, refunds
    - technical: Bugs, errors, performance issues
    - account: Login, password, profile management
    - feature_request: New features or enhancements
    - complaint: Dissatisfaction with product/service
    - other: Everything else

    RESPONSE FORMAT:
    {respnseSchema}
    """
# Return valid JSON with this schema:
#     {
#         "category": "billing|technical|account|feature_request|complaint|other",
#         "urgency": "P1|P2|P3|P4",
#         "draftReply": "string (max 500 chars)",
#         "confidenceScore": 0.0-1.0,
#         "reasoning": {
#             "urgencyRationale": "string",
#             "categoryRationale": "string"
#         }
#     }
def build_user_prompt(request:TicketRequest, redacted_text: str) -> str:
    # Build previous tickets section
    prev_tickets = request.customerContext.previousTickets
    if not prev_tickets:
        prev_text = ""
    else:
        prev_lines = []
        for pt in prev_tickets[:5]:  # limit to 5
            prev_lines.append(f"  - [{pt.category}] {pt.summary}")
        prev_text = "\nPrevious Tickets:\n" + "\n".join(prev_lines)

    # Construct the full prompt
    return f"""
    INCOMING TICKET:
    Ticket ID: {request.ticketId}
    Source: {request.sourceSystem}
    Subject: {request.subject}
    Customer Tier: {request.customerContext.customerTier}
    Preferred Language: {request.routingHints.preferredLanguage}
    Region: {request.routingHints.region}
    Created: {request.agingMetrics.secondsSinceCreation} seconds ago
    Escalations: {request.agingMetrics.escalations}
    {prev_text}
    
    CUSTOMER MESSAGE:
    {redacted_text}
    
    Analyze this ticket and respond with JSON only.
    """