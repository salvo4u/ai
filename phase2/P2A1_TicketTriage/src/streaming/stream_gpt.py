import openai,asyncio,json
from jsonschema import ValidationError
import os

from services.validations.validator import validate_llm_response, MAX_RETRIES
from telemetry.telemetry import start_timer, end_timer, build_telemetry, aggregate_telemetry

client = openai.OpenAI(api_key=os.getenv("VARIABLE_NAME", "default_value"))


MAX_RETRIES = 3
def repair_response(invalid_json: str, error_message: str, system_prompt: str) -> str:
    """
    Ask the model to repair its own output based on validation errors.
    """
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": f"""
        The following JSON response was invalid:

        {invalid_json}

        Validation error: {error_message}

        Please regenerate a corrected JSON object that strictly follows the schema.
        """}
    ]
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages
    )
    return response.choices[0].message.content

def bounded_retry(messages, system_prompt, model="gpt-4o-mini"):
    """
    Runs a bounded retry loop: calls the LLM, validates JSON, repairs if invalid,
    retries up to MAX_RETRIES, and returns a validated dict or fallback.
    """
    attempt, raw_response, error_message = 0, None, None

    while attempt < MAX_RETRIES:
        attempt += 1
        if attempt == 1:
            response = client.chat.completions.create(model=model, messages=messages)
            raw_response = response.choices[0].message.content
        else:
            raw_response = repair_response(raw_response, error_message, system_prompt)

        try:
            validated = validate_llm_response(raw_response)
            return validated
        except ValidationError as e:
            error_message = e.message

    # Fallback if all retries fail
    return {
        "category": "other",
        "urgency": "P4",
        "draftReply": "We have received your request and will follow up.",
        "confidenceScore": 0.5,
        "reasoning": {
            "urgencyRationale": "Fallback response due to repeated validation failures.",
            "categoryRationale": "Defaulted to 'other'.",
            "suggestedActions": ["Log error", "Notify support team"]
        },
        "modelVersion": "fallback",
        "tokensUsed": 0,
        "inferenceTimeMs": 0
    }



def stream_chat_gpt(messages, system_prompt, model="gpt-4o-mini"):
    """
    Streams tokens live, buffers internally, then calls bounded_retry at completion
    to validate and repair JSON. Yields token events and the final validated output.
    """
    buffer = ""
    attempts = []
    start = start_timer()

    with client.chat.completions.stream(model=model, messages=messages) as stream:
        for event in stream:
            if event.type == "token":
                buffer += event.token
                yield {"type": "token", "data": event.token}
            elif event.type == "completed":
                latency_ms = end_timer(start)
                usage = stream.response.usage
                attempts.append(build_telemetry(model, usage, latency_ms))

                # Instead of duplicating retry logic, call bounded_retry
                validated = bounded_retry(messages, system_prompt, model=model)

                telemetry_summary = aggregate_telemetry(attempts)

                yield {
                    "type": "completed",
                    "validated": validated,
                    "telemetry": telemetry_summary,
                    "raw_buffer": buffer
                }



