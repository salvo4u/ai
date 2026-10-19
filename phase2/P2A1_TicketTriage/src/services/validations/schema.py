# JSON Schema for validation
LLM_RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "category": {"type": "string", "enum": ["billing","technical","account","feature_request","complaint","other"]},
        "urgency": {"type": "string", "enum": ["P1","P2","P3","P4"]},
        "draftReply": {"type": "string", "maxLength": 500},
        "confidenceScore": {"type": "number", "minimum": 0.0, "maximum": 1.0},
        "reasoning": {
            "type": "object",
            "properties": {
                "urgencyRationale": {"type": "string"},
                "categoryRationale": {"type": "string"},
                "suggestedActions": {"type": "array", "items": {"type": "string"}}
            },
            "required": ["urgencyRationale","categoryRationale","suggestedActions"]
        },
        "modelVersion": {"type": "string"},
        "tokensUsed": {"type": "integer", "minimum": 0},
        "inferenceTimeMs": {"type": "integer", "minimum": 0}
    },
    "required": ["category","urgency","draftReply","confidenceScore","reasoning","modelVersion","tokensUsed","inferenceTimeMs"]
}


