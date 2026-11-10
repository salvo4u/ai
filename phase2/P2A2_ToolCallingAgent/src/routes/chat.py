import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from src.schemas.chat import ChatRequest

router = APIRouter(
    prefix="/api",
    tags=["Chat"]
)


async def stream_chat_response(request: ChatRequest):

    chunks = [
        "Hello",
        ", ",
        "how ",
        "can ",
        "I help ",
        "you?"
    ]

    for chunk in chunks:
        yield (
            f"event: message\n"
            f"data: {json.dumps({'content': chunk})}\n\n"
        )

    yield (
        f"event: done\n"
        f"data: {json.dumps({'conversation_id': request.conversation_id})}\n\n"
    )


@router.post("/chat")
async def chat(request: ChatRequest):

    return StreamingResponse(
        stream_chat_response(request),
        media_type="text/event-stream"
    )