import re
from dataclasses import dataclass
from typing import List
from src.models.ticket_models import RedactionEntry, RedactionResult

# Example PII patterns: list of (regex, type)
PII_PATTERNS = [
    (re.compile(r'\b\d{3}-\d{2}-\d{4}\b'), "SSN"),          # Social Security Number
    (re.compile(r'\b\d{16}\b'), "CreditCard"),              # 16-digit card
    (re.compile(r'\b[\w\.-]+@[\w\.-]+\.\w+\b'), "Email"),   # Email
    (re.compile(r'\b\d{10}\b'), "Phone"),                   # 10-digit phone
]

def redact_pii(text: str) -> RedactionResult:
    log: List[RedactionEntry] = []
    result = text

    for pattern, pii_type in PII_PATTERNS:
        for match in pattern.finditer(result):
            original = match.group()
            replacement = f"[REDACTED:{pii_type}]"
            result = result.replace(original, replacement)
            log.append(RedactionEntry(type=pii_type, original=original, replacement=replacement))

    return RedactionResult(result=result, log=log)

def remove_quoted_reply(text: str) -> str:
    """Remove quoted reply chains from text."""
    cleaned = re.sub(r'^>.*$', '', text, flags=re.MULTILINE)
    cleaned = re.sub(r'On .* wrote:\n', '', cleaned)
    cleaned = re.sub(r'-{2,}.*Original Message.*-{2,}', '', cleaned, flags=re.IGNORECASE | re.DOTALL)
    return cleaned.strip()

def remove_signature(text: str) -> str:
    """Remove common email/chat signatures."""
    signature_patterns = [
        r'(?i)^best regards.*$',
        r'(?i)^regards.*$',
        r'(?i)^sincerely.*$',
        r'(?i)^cheers.*$',
        r'(?i)^thanks.*$',
        r'(?i)^sent from my.*$',
        r'(?i)^--.*$',
        r'(?i)^__.*$'
    ]
    cleaned = text
    for pattern in signature_patterns:
        cleaned = re.sub(pattern, '', cleaned, flags=re.MULTILINE)
    return cleaned.strip()

def preprocess_ticket_text(text: str) -> RedactionResult:
    """
    Apply normalization before triage:
    - Strip signatures
    - Remove quoted replies
    - Redact PII
    Returns a RedactionResult with cleaned text and log entries.
    """
    text = remove_signature(text)
    text = remove_quoted_reply(text)
    redacted_result = redact_pii(text)

    # Debug log (optional)
    for entry in redacted_result.log:
        print(f"   - {entry.type}: {entry.original} → {entry.replacement}")

    return redacted_result
