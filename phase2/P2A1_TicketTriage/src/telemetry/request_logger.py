import json
import datetime
import os
import uuid

LOG_FILE = "all_requests.log"

def log_request(ticket_id: str, customer_id: str, request: dict, raw_response: str,
                tokens: dict, validated_output: dict):
    """
    Persist request/response details into a log file.
    Each line is a JSON object for easy parsing later.
    """
    record = {
        "requestid": uuid.uuid4(),
        "timestamp": datetime.datetime.utcnow().isoformat(),
        "ticket_id": ticket_id,
        "customer_id": customer_id,
        "request": request,              # original request payload
        "raw_response": raw_response,    # raw LLM output before validation
        "tokens": tokens,                # {"prompt": x, "completion": y, "total": z}
        "validated_output": validated_output
    }

    with open(LOG_FILE, mode="a", encoding="utf-8") as f:
        f.write(json.dumps(record) + "\n")
