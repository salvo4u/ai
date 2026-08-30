package com.internal.knowledge.store;

/**
 * Converts text into a fixed-length numeric vector so it can be compared
 * for semantic/keyword similarity. Swap the implementation to plug in a
 * real embedding model (OpenAI, Cohere, a local sentence-transformer, etc.)
 * without touching any of the retrieval code that depends on this interface.
 */
public interface EmbeddingService {
    double[] embed(String text);
    int getDimensions();
}
