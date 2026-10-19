import json
from jsonschema import validate, ValidationError
from services.validations.schema import  LLM_RESPONSE_SCHEMA

MAX_RETRIES = 3

def validate_llm_response(raw_json: str) -> dict:
    parsed = json.loads(raw_json)
    validate(instance=parsed, schema=LLM_RESPONSE_SCHEMA)
    return parsed


