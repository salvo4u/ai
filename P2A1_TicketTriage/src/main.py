from fastapi import FastAPI
from src.routes.ticket_routes import router as ticket_router

app = FastAPI(
    title="P2A1 Ticket Triage",
    version="0.1.0",
    description="HTTP service for receiving ticket text and triaging it.",
)

app.include_router(ticket_router)

@app.get("/health")
async def health():
    return {"status": "ok"}
