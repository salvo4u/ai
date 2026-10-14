from fastapi.testclient import TestClient
from src.main import app

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

def test_triage_accepts_plain_text():
    response = client.post(
        "/api/tickets/triage",
        content="Customer cannot log in to the application.",
        headers={"Content-Type": "text/plain"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "received"
    assert body["ticket_text"] == "Customer cannot log in to the application."

def test_triage_rejects_empty_text():
    response = client.post(
        "/api/tickets/triage",
        content="   ",
        headers={"Content-Type": "text/plain"},
    )
    assert response.status_code == 400
