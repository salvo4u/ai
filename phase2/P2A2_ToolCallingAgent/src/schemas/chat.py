from pydantic import BaseModel
from typing import Optional

class ChatRequest(BaseModel):
    conversation_id: Optional[str] = None
    user_id: str
    message: str

class ChatChunk(BaseModel):
    content: str