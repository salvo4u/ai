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

/**
 * Assembles the final context that will be supplied to the LLM after retrieval.
 *
 * <p>The retriever can return several chunks that are individually relevant, but
 * those chunks may contain duplicates, redundant information, stale content, or
 * more tokens than the LLM context budget can safely accommodate. This class is
 * responsible for converting that raw retrieval result into a compact,
 * ranked, traceable and token-budget-aware {@link ContextResult}.</p>
 *
 * <p>Processing pipeline:</p>
 * <ol>
 *   <li>Deduplicate highly similar chunks.</li>
 *   <li>Calculate a combined relevance score.</li>
 *   <li>Filter low-scoring chunks using a configurable threshold.</li>
 *   <li>Reserve tokens for the system prompt and model response.</li>
 *   <li>Select as many high-value chunks as fit the remaining budget.</li>
 *   <li>Trim an oversized chunk at sentence level when necessary.</li>
 *   <li>Build context entries and source/citation metadata.</li>
 * </ol>
 *
 * <p>This component sits between retrieval/ranking and LLM generation in the
 * query-side RAG pipeline.</p>
 */
public class ContextAssembler {

    /**
     * System instruction used by the downstream LLM.
     *
     * <p>The instruction establishes the grounding boundary: the generated
     * answer should use only the context assembled by this component.</p>
     */
    private static final String SYSTEM_PROMPT =
            "You are an Internal Knowledge Assistant. Use ONLY the provided context to answer questions.";

    /** Estimates the number of tokens consumed by text. */
    private final TokenEstimator tokenEstimator;

    /** Provides lexical/text similarity calculations used during ranking and deduplication. */
    private final TextSimilarityUtil textSimilarityUtil;

    /** Controls thresholds and context-window limits. */
    private final ContextAssemblerConfig config;

    /**
     * Creates an assembler with the default token estimator, similarity utility,
     * and configuration.
     */
    public ContextAssembler() {
        this.tokenEstimator = new TokenEstimator();
        this.textSimilarityUtil = new TextSimilarityUtil();
        this.config = new ContextAssemblerConfig();
    }

    /**
     * Converts retrieved chunks into the final LLM-ready context.
     *
     * @param query the user's query; used for keyword-based relevance scoring
     * @param retrievedChunks chunks returned by the retrieval layer
     * @return an assembled context containing selected chunks, sources,
     *         confidence information, token usage and fallback status
     */
    public ContextResult assembleContext(Query query, List<Chunk> retrievedChunks) {
        System.out.println("Assembling context for query: " + query.getId());

        // Remove redundant chunks before scoring so duplicate evidence does not
        // consume context-window capacity.
        List<Chunk> uniqueChunks = deduplicateChunks(retrievedChunks);

        // Re-rank using semantic similarity, keyword overlap and freshness.
        List<Chunk> rankedChunks = rankChunks(query, uniqueChunks);

        // Remove chunks whose final relevance score is below the configured
        // threshold, while retaining the best result as a fallback.
        List<Chunk> filteredChunks = filterByThreshold(rankedChunks);

        // Fit the ranked evidence into the available context-token budget.
        ContextResult result = optimizeForTokenBudget(query, filteredChunks);

        System.out.println(
                "Context assembly completed, selected " + result.getContext().size() + " chunks"
        );

        return result;
    }

    /**
     * Removes chunks that are substantially similar to a chunk already retained.
     *
     * <p>Jaccard similarity is calculated against normalized chunk text. If two
     * chunks exceed the configured deduplication threshold, the chunk with the
     * higher combined score is retained.</p>
     *
     * @param chunks retrieved chunks
     * @return a list containing one representative for each sufficiently distinct chunk
     */
    private List<Chunk> deduplicateChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Chunk> uniqueChunks = new ArrayList<>();

        for (Chunk chunk : chunks) {
            boolean isDuplicate = false;
            String normalizedText = chunk.getText().toLowerCase().trim();

            for (Chunk existing : uniqueChunks) {
                double similarity = textSimilarityUtil.jaccardSimilarity(
                        normalizedText,
                        existing.getText().toLowerCase().trim()
                );

                if (similarity > config.getDeduplicationThreshold()) {
                    isDuplicate = true;

                    // Prefer the stronger candidate if both chunks represent
                    // substantially the same content.
                    if (chunk.getCombinedScore() > existing.getCombinedScore()) {
                        uniqueChunks.remove(existing);
                        uniqueChunks.add(chunk);
                    }
                    break;
                }
            }

            if (!isDuplicate) {
                uniqueChunks.add(chunk);
            }
        }

        return uniqueChunks;
    }

    /**
     * Re-ranks chunks using three relevance signals.
     *
     * <p>The current weighted formula is:</p>
     *
     * <pre>
     * combinedScore =
     *     0.6 * semanticSimilarity
     *   + 0.3 * keywordOverlap
     *   + 0.1 * freshnessBoost
     * </pre>
     *
     * <p>Semantic similarity receives the highest weight because the RAG
     * objective is primarily semantic retrieval. Keyword overlap provides an
     * additional lexical relevance signal, while freshness gives newer content
     * a small preference.</p>
     *
     * @param query user query
     * @param chunks deduplicated retrieved chunks
     * @return chunks sorted from highest to lowest combined score
     */
    private List<Chunk> rankChunks(Query query, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        String queryText = query.getText();

        for (Chunk chunk : chunks) {
            double baseScore = chunk.getSimilarityScore();

            // Measures lexical overlap between the user's query and the chunk.
            double keywordOverlap =
                    textSimilarityUtil.calculateKeywordOverlap(queryText, chunk.getText());

            chunk.setKeywordScore(keywordOverlap);

            // Newer content receives a small positive boost. The boost decays
            // over approximately one year and is never negative.
            double freshnessBoost = 0.0;
            if (chunk.getCreatedAt() != null) {
                long daysOld = java.time.Duration
                        .between(chunk.getCreatedAt(), LocalDateTime.now())
                        .toDays();

                freshnessBoost = Math.max(
                        0,
                        0.05 * (1 - daysOld / 365.0)
                );
            }

            double combinedScore =
                    (0.6 * baseScore)
                            + (0.3 * keywordOverlap)
                            + (0.1 * freshnessBoost);

            chunk.setCombinedScore(combinedScore);
        }

        chunks.sort(
                (c1, c2) -> Double.compare(
                        c2.getCombinedScore(),
                        c1.getCombinedScore()
                )
        );

        return chunks;
    }

    /**
     * Removes chunks below the configured combined-score threshold.
     *
     * <p>If every chunk is below the threshold, the highest-ranked chunk is
     * retained and marked as a fallback. This prevents the system from silently
     * producing an empty context when retrieval produced at least one result.</p>
     *
     * @param chunks ranked chunks
     * @return chunks meeting the threshold, or the top chunk as fallback
     */
    private List<Chunk> filterByThreshold(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new ArrayList<>();
        }

        double threshold = config.getSimilarityThreshold();
        List<Chunk> filtered = new ArrayList<>();

        for (Chunk c : chunks) {
            if (c.getCombinedScore() >= threshold) {
                filtered.add(c);
            }
        }

        if (filtered.isEmpty() && !chunks.isEmpty()) {
            Chunk topChunk = chunks.get(0);
            topChunk.setBelowThreshold(true);
            filtered.add(topChunk);
        }

        return filtered;
    }

    /**
     * Selects context within the configured token budget.
     *
     * <p>The total context budget is reduced by both the reserved response
     * tokens and the system-prompt tokens. Chunks are considered in ranked order.
     * If a complete chunk does not fit, the chunk is sentence-trimmed once and
     * processing stops.</p>
     *
     * @param query user query; retained as part of the assembly contract
     * @param chunks filtered and ranked chunks
     * @return final formatted context
     */
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

        // Tokens available for system instructions + retrieved evidence.
        int tokenAvailable = maxTokens - reservedForResponse;

        int systemPromptTokens = tokenEstimator.estimateTokens(SYSTEM_PROMPT);
        tokenAvailable -= systemPromptTokens;

        List<Chunk> selectedChunks = new ArrayList<>();
        int currentTokens = systemPromptTokens;
        int maxChunksTotal = config.getMaxChunksTotal();

        for (Chunk chunk : chunks) {
            if (selectedChunks.size() >= maxChunksTotal) {
                break;
            }

            int chunkTokens = tokenEstimator.estimateTokens(chunk.getText());

            if (currentTokens + chunkTokens <= tokenAvailable) {
                selectedChunks.add(chunk);
                currentTokens += chunkTokens;
            } else {
                // Preserve as much useful evidence as possible rather than
                // discarding the entire chunk.
                Chunk trimmedChunk =
                        trimChunkToFit(chunk, tokenAvailable - currentTokens);

                if (trimmedChunk != null) {
                    selectedChunks.add(trimmedChunk);
                    currentTokens += tokenEstimator.estimateTokens(
                            trimmedChunk.getText()
                    );
                }

                // Once a chunk needs trimming, stop adding lower-ranked chunks.
                break;
            }
        }

        // Ensure at least the highest-ranked evidence is attempted even when
        // the normal selection loop could not fit a complete chunk.
        if (selectedChunks.isEmpty() && !chunks.isEmpty()) {
            Chunk trimmed = trimChunkToFit(chunks.get(0), tokenAvailable);
            if (trimmed != null) {
                selectedChunks.add(trimmed);
            }
        }

        selectedChunks.sort(
                (c1, c2) -> Double.compare(
                        c2.getCombinedScore(),
                        c1.getCombinedScore()
                )
        );

        return formatContext(selectedChunks);
    }

    /**
     * Trims a chunk at sentence boundaries so that it fits a token budget.
     *
     * <p>Sentences receive a heuristic score based on their position and on the
     * presence of words such as "important", "critical", "must" and "required".
     * Selected sentences are subsequently restored to their original order.</p>
     *
     * @param chunk source chunk
     * @param maxTokens maximum number of tokens allowed for the trimmed text
     * @return a new trimmed chunk, or {@code null} when no sentence can fit
     */
    private Chunk trimChunkToFit(Chunk chunk, int maxTokens) {
        if (maxTokens <= 0) {
            return null;
        }

        List<String> sentences = splitIntoSentences(chunk.getText());
        if (sentences.isEmpty()) {
            return null;
        }

        List<ScoredSentence> scoredSentences = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);

            // Earlier sentences receive a small positional preference.
            double score =
                    1.0 + ((double) (sentences.size() - i) / sentences.size()) * 0.3;

            String lowerSentence = sentence.toLowerCase();

            // Heuristic emphasis for sentences likely to contain requirements
            // or high-priority guidance.
            if (lowerSentence.contains("important")
                    || lowerSentence.contains("critical")
                    || lowerSentence.contains("must")
                    || lowerSentence.contains("required")) {
                score += 0.2;
            }

            scoredSentences.add(
                    new ScoredSentence(score, sentence, i)
            );
        }

        // Rank by importance first so the available budget is spent on the
        // highest-value sentences.
        scoredSentences.sort(
                (s1, s2) -> Double.compare(s2.score, s1.score)
        );

        List<String> selectedSentences = new ArrayList<>();
        int currentTokens = 0;

        for (ScoredSentence scored : scoredSentences) {
            int sentenceTokens =
                    tokenEstimator.estimateTokens(scored.sentence);

            if (currentTokens + sentenceTokens <= maxTokens) {
                selectedSentences.add(scored.sentence);
                currentTokens += sentenceTokens;
            } else {
                break;
            }
        }

        // Restore source order so the resulting context remains readable and
        // does not present sentences in importance-score order.
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

    /**
     * Converts selected chunks into the response model consumed by the
     * generation layer.
     *
     * <p>Each context entry receives a stable display citation such as
     * {@code [C1]}, while the corresponding source object stores the same
     * citation ID and source metadata. This allows generated answers to cite
     * evidence back to its originating document/chunk.</p>
     *
     * @param chunks selected context chunks
     * @return formatted context and source metadata
     */
    private ContextResult formatContext(List<Chunk> chunks) {
        List<ContextResult.ContextEntry> entries = new ArrayList<>();
        List<Source> sources = new ArrayList<>();

        int totalTokens = 0;
        boolean isFallback = false;
        double totalConfidence = 0;

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);

            if (chunk.isBelowThreshold()) {
                isFallback = true;
            }

            String citationId = "[C" + (i + 1) + "]";

            entries.add(
                    ContextResult.ContextEntry.builder()
                            .id(citationId)
                            .text(chunk.getText())
                            .source(chunk.getSource())
                            .section(chunk.getSection())
                            .relevanceScore(chunk.getCombinedScore())
                            .metadata(chunk.getMetadata())
                            .build()
            );

            sources.add(
                    Source.builder()
                            .id(i + 1)
                            .chunkId(chunk.getId())
                            .title(
                                    chunk.getTitle() != null
                                            ? chunk.getTitle()
                                            : "Document " + (i + 1)
                            )
                            .source(chunk.getSource())
                            .section(chunk.getSection())
                            .url(chunk.getUrl())
                            .citationId(citationId)
                            .lastUpdated(chunk.getCreatedAt())
                            .build()
            );

            totalTokens +=
                    tokenEstimator.estimateTokens(chunk.getText());

            totalConfidence += chunk.getCombinedScore();
        }

        double averageConfidence =
                chunks.isEmpty()
                        ? 0
                        : totalConfidence / chunks.size();

        return ContextResult.builder()
                .context(entries)
                .sources(sources)
                .totalTokens(totalTokens)
                .isFallback(isFallback)
                .averageConfidence(averageConfidence)
                .contextWindowUsed(totalTokens + 200)
                .build();
    }

    /**
     * Performs lightweight sentence segmentation using terminal punctuation.
     *
     * <p>This intentionally uses a simple regular expression rather than a
     * full NLP sentence tokenizer. Consequently, abbreviations and unusual
     * punctuation patterns may not always be handled perfectly.</p>
     *
     * @param text input text
     * @return trimmed non-empty sentences
     */
    private List<String> splitIntoSentences(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Internal value object used while selecting sentences from an oversized
     * chunk.
     */
    @Data
    @AllArgsConstructor
    private static class ScoredSentence {
        private double score;
        private String sentence;
        private int originalIndex;
    }

    /**
     * Configuration for context assembly.
     *
     * <p>The defaults represent the current implementation's operating
     * assumptions. These values should ideally be externalized/configured when
     * the service is deployed in different environments.</p>
     */
    @Data
    public static class ContextAssemblerConfig {

        /** Maximum total context window allocated by the assembler. */
        private int maxContextTokens = 4000;

        /**
         * Minimum overlap-token setting retained by the configuration model.
         *
         * <p>This field is currently not referenced by the implementation in
         * this class and may be intended for future overlap-aware trimming.</p>
         */
        private int minOverlapTokens = 50;

        /** Tokens reserved for the model's generated response. */
        private int reservedForResponse = 500;

        /** Maximum number of retrieved chunks allowed in the final context. */
        private int maxChunksTotal = 15;

        /** Minimum combined relevance score for normal chunk inclusion. */
        private double similarityThreshold = 0.65;

        /** Similarity above which two chunks are considered duplicates. */
        private double deduplicationThreshold = 0.7;
    }
}
