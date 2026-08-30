package com.internal.knowledge.context;

import com.internal.knowledge.model.Chunk;
import com.internal.knowledge.model.ContextResult;
import com.internal.knowledge.model.Query;
import com.internal.knowledge.model.Source;
import com.internal.knowledge.util.TextSimilarityUtil;
import com.internal.knowledge.util.TokenEstimator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;

public class ContextAssembler {
    private static final String SYSTEM_PROMPT = "You are an Internal Knowledge Assistant. Use ONLY the provided context to answer questions.";

    private final TokenEstimator tokenEstimator;
    private final TextSimilarityUtil textSimilarityUtil;
    private final ContextAssemblerConfig config;

    public ContextAssembler() {
        this.tokenEstimator = new TokenEstimator();
        this.textSimilarityUtil = new TextSimilarityUtil();
        this.config = new ContextAssemblerConfig();
    }

    public ContextResult assembleContext(Query query, List<Chunk> retrievedChunks) {
        System.out.println("Assembling context for query: " + query.getId());

        List<Chunk> uniqueChunks = deduplicateChunks(retrievedChunks);
        List<Chunk> rankedChunks = rankChunks(query, uniqueChunks);
        List<Chunk> filteredChunks = filterByThreshold(rankedChunks);
        ContextResult result = optimizeForTokenBudget(query, filteredChunks);

        System.out.println("Context assembly completed, selected " + result.getContext().size() + " chunks");
        return result;
    }

    private List<Chunk> deduplicateChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return new ArrayList<>();

        List<Chunk> uniqueChunks = new ArrayList<>();
        for (Chunk chunk : chunks) {
            boolean isDuplicate = false;
            String normalizedText = chunk.getText().toLowerCase().trim();

            for (Chunk existing : uniqueChunks) {
                double similarity = textSimilarityUtil.jaccardSimilarity(
                    normalizedText, existing.getText().toLowerCase().trim());
                if (similarity > config.getDeduplicationThreshold()) {
                    isDuplicate = true;
                    if (chunk.getCombinedScore() > existing.getCombinedScore()) {
                        uniqueChunks.remove(existing);
                        uniqueChunks.add(chunk);
                    }
                    break;
                }
            }
            if (!isDuplicate) uniqueChunks.add(chunk);
        }
        return uniqueChunks;
    }

    private List<Chunk> rankChunks(Query query, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return new ArrayList<>();

        String queryText = query.getText();
        for (Chunk chunk : chunks) {
            double baseScore = chunk.getSimilarityScore();
            double keywordOverlap = textSimilarityUtil.calculateKeywordOverlap(queryText, chunk.getText());
            chunk.setKeywordScore(keywordOverlap);

            double freshnessBoost = 0.0;
            if (chunk.getCreatedAt() != null) {
                long daysOld = java.time.Duration.between(chunk.getCreatedAt(), LocalDateTime.now()).toDays();
                freshnessBoost = Math.max(0, 0.05 * (1 - daysOld / 365.0));
            }

            double combinedScore = (0.6 * baseScore) + (0.3 * keywordOverlap) + (0.1 * freshnessBoost);
            chunk.setCombinedScore(combinedScore);
        }

        chunks.sort((c1, c2) -> Double.compare(c2.getCombinedScore(), c1.getCombinedScore()));
        return chunks;
    }

    private List<Chunk> filterByThreshold(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return new ArrayList<>();

        double threshold = config.getSimilarityThreshold();
        List<Chunk> filtered = new ArrayList<>();
        for (Chunk c : chunks) {
            if (c.getCombinedScore() >= threshold) filtered.add(c);
        }

        if (filtered.isEmpty() && !chunks.isEmpty()) {
            Chunk topChunk = chunks.get(0);
            topChunk.setBelowThreshold(true);
            filtered.add(topChunk);
        }
        return filtered;
    }

    private ContextResult optimizeForTokenBudget(Query query, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return ContextResult.builder()
                .context(new ArrayList<>())
                .sources(new ArrayList<>())
                .totalTokens(0)
                .isFallback(true)
                .averageConfidence(0)
                .build();
        }

        int maxTokens = config.getMaxContextTokens();
        int reservedForResponse = config.getReservedForResponse();
        int tokenAvailable = maxTokens - reservedForResponse;
        int systemPromptTokens = tokenEstimator.estimateTokens(SYSTEM_PROMPT);
        tokenAvailable -= systemPromptTokens;

        List<Chunk> selectedChunks = new ArrayList<>();
        int currentTokens = systemPromptTokens;
        int maxChunksTotal = config.getMaxChunksTotal();

        for (Chunk chunk : chunks) {
            if (selectedChunks.size() >= maxChunksTotal) break;
            int chunkTokens = tokenEstimator.estimateTokens(chunk.getText());

            if (currentTokens + chunkTokens <= tokenAvailable) {
                selectedChunks.add(chunk);
                currentTokens += chunkTokens;
            } else {
                Chunk trimmedChunk = trimChunkToFit(chunk, tokenAvailable - currentTokens);
                if (trimmedChunk != null) {
                    selectedChunks.add(trimmedChunk);
                    currentTokens += tokenEstimator.estimateTokens(trimmedChunk.getText());
                }
                break;
            }
        }

        if (selectedChunks.isEmpty() && !chunks.isEmpty()) {
            Chunk trimmed = trimChunkToFit(chunks.get(0), tokenAvailable);
            if (trimmed != null) selectedChunks.add(trimmed);
        }

        selectedChunks.sort((c1, c2) -> Double.compare(c2.getCombinedScore(), c1.getCombinedScore()));
        return formatContext(selectedChunks);
    }

    private Chunk trimChunkToFit(Chunk chunk, int maxTokens) {
        if (maxTokens <= 0) return null;

        List<String> sentences = splitIntoSentences(chunk.getText());
        if (sentences.isEmpty()) return null;

        List<ScoredSentence> scoredSentences = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            double score = 1.0 + ((double)(sentences.size() - i) / sentences.size()) * 0.3;
            if (sentence.toLowerCase().contains("important") ||
                sentence.toLowerCase().contains("critical") ||
                sentence.toLowerCase().contains("must") ||
                sentence.toLowerCase().contains("required")) {
                score += 0.2;
            }
            scoredSentences.add(new ScoredSentence(score, sentence, i));
        }

        scoredSentences.sort((s1, s2) -> Double.compare(s2.score, s1.score));

        List<String> selectedSentences = new ArrayList<>();
        int currentTokens = 0;
        for (ScoredSentence scored : scoredSentences) {
            int sentenceTokens = tokenEstimator.estimateTokens(scored.sentence);
            if (currentTokens + sentenceTokens <= maxTokens) {
                selectedSentences.add(scored.sentence);
                currentTokens += sentenceTokens;
            } else break;
        }

        selectedSentences.sort((s1, s2) -> {
            int idx1 = sentences.indexOf(s1);
            int idx2 = sentences.indexOf(s2);
            return Integer.compare(idx1, idx2);
        });

        String trimmedText = String.join(" ", selectedSentences);

        return Chunk.builder()
            .id(chunk.getId())
            .text(trimmedText)
            .source(chunk.getSource())
            .title(chunk.getTitle())
            .section(chunk.getSection())
            .url(chunk.getUrl())
            .metadata(chunk.getMetadata())
            .similarityScore(chunk.getSimilarityScore())
            .keywordScore(chunk.getKeywordScore())
            .combinedScore(chunk.getCombinedScore())
            .createdAt(chunk.getCreatedAt())
            .isBelowThreshold(chunk.isBelowThreshold())
            .isTrimmed(true)
            .originalTextLength(chunk.getText().length())
            .build();
    }

    private ContextResult formatContext(List<Chunk> chunks) {
        List<ContextResult.ContextEntry> entries = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        int totalTokens = 0;
        boolean isFallback = false;
        double totalConfidence = 0;

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            if (chunk.isBelowThreshold()) isFallback = true;

            entries.add(ContextResult.ContextEntry.builder()
                .id("[C" + (i + 1) + "]")
                .text(chunk.getText())
                .source(chunk.getSource())
                .section(chunk.getSection())
                .relevanceScore(chunk.getCombinedScore())
                .metadata(chunk.getMetadata())
                .build());

            sources.add(Source.builder()
                .id(i + 1)
                .chunkId(chunk.getId())
                .title(chunk.getTitle() != null ? chunk.getTitle() : "Document " + (i + 1))
                .source(chunk.getSource())
                .section(chunk.getSection())
                .url(chunk.getUrl())
                .citationId("[C" + (i + 1) + "]")
                .lastUpdated(chunk.getCreatedAt())
                .build());

            totalTokens += tokenEstimator.estimateTokens(chunk.getText());
            totalConfidence += chunk.getCombinedScore();
        }

        double averageConfidence = chunks.isEmpty() ? 0 : totalConfidence / chunks.size();

        return ContextResult.builder()
            .context(entries)
            .sources(sources)
            .totalTokens(totalTokens)
            .isFallback(isFallback)
            .averageConfidence(averageConfidence)
            .contextWindowUsed(totalTokens + 200)
            .build();
    }

    private List<String> splitIntoSentences(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    private static class ScoredSentence {
        private double score;
        private String sentence;
        private int originalIndex;
    }

    @Data
    public static class ContextAssemblerConfig {
        private int maxContextTokens = 4000;
        private int minOverlapTokens = 50;
        private int reservedForResponse = 500;
        private int maxChunksTotal = 15;
        private double similarityThreshold = 0.65;
        private double deduplicationThreshold = 0.7;
    }
}
