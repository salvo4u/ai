import re
from dataclasses import dataclass
from typing import List

from models.ticket_models import RedactionEntry


@dataclass
class RedactionResult:
    result: str
    log: List[RedactionEntry]

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
        matches = list(pattern.finditer(result))
        for match in matches:
            original = match.group()
            replacement = f"[REDACTED:{pii_type}]"
            result = result.replace(original, replacement)
            log.append(RedactionEntry(pii_type, original, replacement))

    return RedactionResult(result=result, log=log)

def remove_quoted_reply(text: str) -> str:
    """
    Removes quoted reply chains from a piece of text.
    Handles common patterns like:
    - Lines starting with '>'
    - 'On <date>, <name> wrote:' blocks
    - '-----Original Message-----' separators
    """
    # Remove lines starting with '>'
    cleaned = re.sub(r'^>.*$', '', text, flags=re.MULTILINE)

    # Remove "On ... wrote:" style headers
    cleaned = re.sub(r'On .* wrote:\n', '', cleaned)

    # Remove "Original Message" separators
    cleaned = re.sub(r'-{2,}.*Original Message.*-{2,}', '', cleaned, flags=re.IGNORECASE | re.DOTALL)

    # Strip extra whitespace
    return cleaned.strip()

def remove_signature(text: str) -> str:
    """
    Removes common email/chat signatures from text.
    Handles patterns like:
    - 'Best regards', 'Regards', 'Sincerely'
    - 'Sent from my iPhone/Android'
    - '--' or '__' signature separators
    """

    # Remove lines starting with common signature phrases
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

    # Strip trailing whitespace
    return cleaned.strip()

def preprocess_ticket_text(text: str) -> str:
    """Apply basic normalization before triage."""
    # Strip Signatures
    text = remove_signature(text)
    # Remove quoted replies
    text = remove_quoted_reply(text)
    # Remove PII data
    redactedResult = redact_pii(text)
    # we can see what all was removed in redactedResult b using redactedResult.log
    for log in redactedResult.log:
        print(f"   - {log.type}: {log.original} → {log.replacement}")
    return redactedResult.result.strip()
