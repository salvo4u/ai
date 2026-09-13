package com.internal.knowledge.fallback;

import com.internal.knowledge.model.Chunk;
import com.internal.knowledge.model.FAQDocument;
import com.internal.knowledge.model.FallbackResponse;
import com.internal.knowledge.model.Query;
import com.internal.knowledge.model.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FallbackHandler {
    private static final double SIMILARITY_THRESHOLD = 0.65;
    private static final double NEAR_THRESHOLD = 0.55;

    private final List<FAQDocument> faqDocuments;

    public FallbackHandler() {
        this(new ArrayList<>());
    }

    public FallbackHandler(List<FAQDocument> faqDocuments) {
        this.faqDocuments = faqDocuments;
    }

    public FallbackResponse handleNoMatch(Query query, List<Chunk> retrievedChunks) {
        System.out.println("Handling no-match for query: " + query.getId());

        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return noChunksFound(query);
        }

        boolean allBelowThreshold = retrievedChunks.stream()
            .allMatch(c -> c.getCombinedScore() < SIMILARITY_THRESHOLD);

        if (allBelowThreshold) {
            return belowThresholdHandler(query, retrievedChunks);
        }

        Chunk topChunk = retrievedChunks.get(0);
        if (topChunk.getCombinedScore() >= NEAR_THRESHOLD) {
            return nearThresholdHandler(query, retrievedChunks);
        }

        return null;
    }

    private FallbackResponse noChunksFound(Query query) {
        return FallbackResponse.builder()
            .type("NO_CHUNKS")
            .message("I couldn't find any relevant documentation for your question. This might mean the topic isn't covered in our current documentation, or the search system is experiencing issues.")
            .confidence(0.0)
            .suggestions(Arrays.asList(
                "Try rephrasing your question with more specific keywords",
                "Check if the information might be in a different documentation repository",
                "Contact the knowledge management team to request new documentation",
                "Try searching directly in the documentation portal"
            ))
            .escalationNeeded(true)
            .build();
    }

    private FallbackResponse belowThresholdHandler(Query query, List<Chunk> chunks) {
        FAQDocument faqMatch = searchFAQ(query);
        if (faqMatch != null) {
            return FallbackResponse.builder()
                .type("FAQ_MATCH")
                .message(faqMatch.getAnswer())
                .confidence(0.4)
                .suggestions(Arrays.asList(
                    "This question was found in the FAQ section",
                    "For more details, consult the full documentation"
                ))
                .escalationNeeded(false)
                .isFaq(true)
                .faqSource(faqMatch.getSource())
                .build();
        }

        Chunk closest = chunks.stream()
            .max(Comparator.comparingDouble(Chunk::getCombinedScore))
            .orElse(null);

        if (closest != null) {
            return FallbackResponse.builder()
                .type("LOW_CONFIDENCE")
                .message(String.format(
                    "I found some potentially related content, but I'm not confident it directly answers your question. Here is the closest match I could find:\n\n%s\n\nThis might not be exactly what you're looking for. I recommend reviewing the source document or rephrasing your question.",
                    closest.getText()))
                .confidence(closest.getCombinedScore())
                .similarityScore(closest.getSimilarityScore())
                .source(Source.builder()
                    .source(closest.getSource())
                    .title(closest.getTitle())
                    .section(closest.getSection())
                    .url(closest.getUrl())
                    .build())
                .suggestions(Arrays.asList(
                    "Review the source document for more context",
                    "Contact the documentation team if you need clarification",
                    "Try a different phrasing of your question"
                ))
                .escalationNeeded(true)
                .build();
        }
        return noChunksFound(query);
    }

    private FallbackResponse nearThresholdHandler(Query query, List<Chunk> chunks) {
        Chunk topChunk = chunks.get(0);
        return FallbackResponse.builder()
            .type("NEAR_THRESHOLD")
            .message(String.format(
                "Based on the documentation, I found relevant content that might help:\n\n%s\n\nPlease note: This is a partial match. You may want to verify the information against the full source document for complete accuracy.",
                topChunk.getText()))
            .confidence(topChunk.getCombinedScore())
            .source(Source.builder()
                .source(topChunk.getSource())
                .title(topChunk.getTitle())
                .section(topChunk.getSection())
                .url(topChunk.getUrl())
                .build())
            .suggestions(Arrays.asList(
                "Review the full source document",
                "Refine your search with more specific terms",
                "Check related documentation sections"
            ))
            .escalationNeeded(false)
            .build();
    }

    private FAQDocument searchFAQ(Query query) {
        if (faqDocuments == null || faqDocuments.isEmpty()) return null;

        String queryText = query.getText().toLowerCase();
        for (FAQDocument faq : faqDocuments) {
            long matches = faq.getKeywords().stream()
                .filter(keyword -> queryText.contains(keyword.toLowerCase()))
                .count();
            double keywordScore = faq.getKeywords().isEmpty() ? 0 :
                (double) matches / faq.getKeywords().size();
            if (keywordScore > 0.3) return faq;
        }
        return null;
    }
}
