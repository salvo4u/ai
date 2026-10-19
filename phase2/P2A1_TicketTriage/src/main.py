from dotenv import load_dotenv
from fastapi import FastAPI
from routes.ticket_routes import router as ticket_router
from routes.usage_routes import router as usage_router
# Load environment variables from .env
load_dotenv()

app = FastAPI(
    title="P2A1 Ticket Triage",
    version="0.1.0",
    description="HTTP service for receiving ticket text and triaging it.",
)

app.include_router(ticket_router)
app.include_router(usage_router)


@app.get("/health")
async def health():
    return {"status": "ok"}
