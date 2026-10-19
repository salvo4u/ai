package com.internal.knowledge.feedback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * Minimal feedback loop: records thumbs-up/down per chunk and exposes a
 * small score that retrieval can use to nudge ranking over time.
 *
 * In-memory only (resets on restart) — swap for a DB-backed implementation
 * if feedback needs to persist across runs.
 */
public class FeedbackService {
    private final Map<String, AtomicInteger> positiveCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> negativeCounts = new ConcurrentHashMap<>();

    public void recordFeedback(String chunkId, FeedbackSignal signal) {
        Map<String, AtomicInteger> counts = (signal == FeedbackSignal.POSITIVE) ? positiveCounts : negativeCounts;
        counts.computeIfAbsent(chunkId, id -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * Returns a small boost/penalty for the given chunk, derived from the
     * ratio of positive to negative feedback it has received so far.
     * Range is roughly -1.0 (all negative) to +1.0 (all positive), 0 if no feedback yet.
     */
    public double getScore(String chunkId) {
        int positive = positiveCounts.getOrDefault(chunkId, new AtomicInteger()).get();
        int negative = negativeCounts.getOrDefault(chunkId, new AtomicInteger()).get();
        int total = positive + negative;
        if (total == 0) return 0.0;
        return (double) (positive - negative) / total;
    }
}
