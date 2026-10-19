package com.internal.knowledge.store;

import com.internal.knowledge.feedback.FeedbackService;
import com.internal.knowledge.model.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simple in-memory document store. Holds chunks in a list and scores them
 * against the query using the {@link EmbeddingService} + cosine similarity,
 * with an optional small boost/penalty from recorded {@link FeedbackService}
 * signals. Good enough for demos/tests; swap for a real vector DB in production.
 */
public class InMemoryVectorStoreService implements VectorStoreService {
    private static final double FEEDBACK_WEIGHT = 0.1;

    private final List<Chunk> chunks = new ArrayList<>();
    private final EmbeddingService embeddingService;
    private final FeedbackService feedbackService;

    public InMemoryVectorStoreService() {
        this(new HashingEmbeddingService(), null);
    }

    public InMemoryVectorStoreService(EmbeddingService embeddingService, FeedbackService feedbackService) {
        this.embeddingService = embeddingService;
        this.feedbackService = feedbackService;
    }

    @Override
    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    @Override
    public List<Chunk> search(String queryText, int topK) {
        double[] queryVector = embeddingService.embed(queryText);

        List<Chunk> ranked = new ArrayList<>(chunks);
        for (Chunk chunk : ranked) {
            double score = cosineSimilarity(queryVector, embeddingService.embed(chunk.getText()));
            if (feedbackService != null) {
                score += FEEDBACK_WEIGHT * feedbackService.getScore(chunk.getId());
            }
            chunk.setSimilarityScore(score);
        }

        ranked.sort(Comparator.comparingDouble(Chunk::getSimilarityScore).reversed());
        return ranked.subList(0, Math.min(topK, ranked.size()));
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // vectors are already L2-normalized by HashingEmbeddingService
    }
}
