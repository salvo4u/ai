class MemoryService:

    async def get_user_memory(
            self,
            user_id: str
    ):
        return await self.repo.get_by_user(
            user_id
        )

    async def delete_fact(
            self,
            user_id: str,
            fact_id: str
    ):
        return await self.repo.delete(
            user_id,
            fact_id
        )
