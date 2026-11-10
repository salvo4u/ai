class MemoryRepository:

    async def get_by_user(
        self,
        user_id: str
    ):
        ...

    async def delete(
        self,
        user_id: str,
        fact_id: str
    ):
        ... 