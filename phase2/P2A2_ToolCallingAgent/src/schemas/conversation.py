from pydantic import BaseModel
from datetime import datetime
from typing import List

class Message(BaseModel):
    role: str
    content: str

class ConversationResponse(BaseModel):
    id: str
    user_id: str
    created_at: datetime
    messages: List[Message]
