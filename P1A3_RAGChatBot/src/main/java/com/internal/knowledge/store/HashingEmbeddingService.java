package com.internal.knowledge.store;

/**
 * A dependency-free, offline embedding implementation using the classic
 * "hashing trick": every token is hashed into a fixed-size vector slot and
 * accumulated, then the vector is L2-normalized. This is NOT a semantic
 * embedding model — it behaves like a fast bag-of-words / keyword vector
 * (similar tokens overlap, synonyms do not). It exists so the retrieval
 * pipeline has a working, zero-dependency default.
 *
 * For production-quality semantic search, replace this with a real
 * embedding model by implementing {@link EmbeddingService} against an
 * API (OpenAI, Cohere, Voyage, Bedrock, etc.) or a local model
 * (sentence-transformers via ONNX/TensorFlow, etc.) and injecting it
 * into {@link InMemoryVectorStoreService} instead of this class.
 */
public class HashingEmbeddingService implements EmbeddingService {
    private static final int DEFAULT_DIMENSIONS = 256;

    private final int dimensions;

    public HashingEmbeddingService() {
        this(DEFAULT_DIMENSIONS);
    }

    public HashingEmbeddingService(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions];
        if (text == null || text.isEmpty()) return vector;

        String[] tokens = text.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int bucket = Math.floorMod(token.hashCode(), dimensions);
            vector[bucket] += 1.0;
        }

        normalize(vector);
        return vector;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    private void normalize(double[] vector) {
        double norm = 0.0;
        for (double v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm == 0.0) return;
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }

    @Override
    public String toString() {
        return "HashingEmbeddingService{dimensions=" + dimensions + "}";
    }
}
