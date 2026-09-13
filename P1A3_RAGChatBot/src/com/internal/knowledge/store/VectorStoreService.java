package com.internal.knowledge.store;

import com.internal.knowledge.model.Chunk;

import java.util.List;

/**
 * Minimal document store abstraction. Swap {@link InMemoryVectorStoreService}
 * for a real backend (pgvector, Pinecone, Weaviate, etc.) by implementing
 * this interface — nothing else in the app needs to change.
 */
public interface VectorStoreService {
    void addChunk(Chunk chunk);
    List<Chunk> search(String queryText, int topK);
}
