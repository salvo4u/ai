# P2A1 Ticket Triage

A small FastAPI service that accepts a plain-text ticket body.

## Run on port 8081

Activate your virtual environment, then install dependencies:

```bash
python -m pip install -r requirements.txt
python -m uvicorn src.main:app --host 0.0.0.0 --port 8081 --reload
```

The service will be available at `http://localhost:8081`.

## Endpoint

`POST /api/tickets/triage`

Send the ticket description as the raw request body with `Content-Type: text/plain`.

### Example using curl

```bash
curl -X POST "http://localhost:8081/api/tickets/triage" ^
  -H "Content-Type: text/plain" ^
  --data-binary "Customer cannot log in to the application."
```

On Git Bash, macOS, or Linux, use backslashes instead of carets for multiline commands, or run the command on one line.

### Health check

```bash
curl http://localhost:8081/health
```

Interactive API documentation: `http://localhost:8081/docs`

## Tests

```bash
python -m pytest
```

## Current behavior

This starter service validates that the body contains non-empty text, trims surrounding whitespace, and returns the accepted text. The LLM triage/classification logic is intentionally a placeholder.
