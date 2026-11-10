from pydantic import BaseModel
from datetime import datetime
from typing import List

class MemoryFact(BaseModel):
    id: str
    value: str
    created_at: datetime

class UserMemory(BaseModel):
    user_id: str
    facts: List[MemoryFact]
