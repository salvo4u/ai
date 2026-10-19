package com.internal.knowledge;

import com.internal.knowledge.citation.CitationEngine;
import com.internal.knowledge.context.ContextAssembler;
import com.internal.knowledge.fallback.FallbackHandler;
import com.internal.knowledge.feedback.FeedbackService;
import com.internal.knowledge.feedback.FeedbackSignal;
import com.internal.knowledge.format.ResponseFormatter;
import com.internal.knowledge.model.Chunk;
import com.internal.knowledge.model.ContextResult;
import com.internal.knowledge.model.FallbackResponse;
import com.internal.knowledge.model.LLMResponse;
import com.internal.knowledge.model.Query;
import com.internal.knowledge.prompt.SystemPrompt;
import com.internal.knowledge.query.QueryProcessor;
import com.internal.knowledge.store.HashingEmbeddingService;
import com.internal.knowledge.store.InMemoryVectorStoreService;
import com.internal.knowledge.store.VectorStoreService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

public class KnowledgeAssistant {
    private static final int DEFAULT_TOP_K = 5;

    private final QueryProcessor queryProcessor;
    private final ContextAssembler contextAssembler;
    private final FallbackHandler fallbackHandler;
    private final CitationEngine citationEngine;
    private final ResponseFormatter responseFormatter;
    private final VectorStoreService vectorStoreService;
    private final FeedbackService feedbackService;

    public KnowledgeAssistant() {
        this.queryProcessor = new QueryProcessor();
        this.contextAssembler = new ContextAssembler();
        this.fallbackHandler = new FallbackHandler();
        this.citationEngine = new CitationEngine();
        this.responseFormatter = new ResponseFormatter();
        this.feedbackService = new FeedbackService();
        this.vectorStoreService = new InMemoryVectorStoreService(new HashingEmbeddingService(), feedbackService);
    }

    /**
     * Adds a document/chunk to the knowledge base at runtime.
     * Replaces the old hardcoded sample data - callers (CLI, API, tests)
     * supply real content instead.
     */
    public void addDocument(Chunk chunk) {
        vectorStoreService.addChunk(chunk);
    }

    /**
     * Records user feedback (thumbs up/down) on a specific chunk that was
     * used in a response. Future searches will nudge that chunk's ranking
     * up or down based on accumulated feedback.
     */
    public void submitFeedback(String chunkId, FeedbackSignal signal) {
        feedbackService.recordFeedback(chunkId, signal);
    }

    public LLMResponse processQuery(Query query) {
        return processQuery(query, DEFAULT_TOP_K);
    }

    public LLMResponse processQuery(Query query, int topK) {
        System.out.println("Processing query: " + query.getId());
        long startTime = System.currentTimeMillis();

        try {
            Query processedQuery = queryProcessor.process(query);

            List<Chunk> retrievedChunks = vectorStoreService.search(processedQuery.getText(), topK);

            FallbackResponse fallback = fallbackHandler.handleNoMatch(processedQuery, retrievedChunks);
            if (fallback != null) {
                System.out.println("Fallback triggered: " + fallback.getType());
                return LLMResponse.createFallback(fallback.getMessage(), fallback.getType(), fallback.getConfidence());
            }

            ContextResult context = contextAssembler.assembleContext(processedQuery, retrievedChunks);
            String prompt = SystemPrompt.buildContextPrompt(processedQuery, context);

            String rawResponse = buildResponseFromContext(context);
            LLMResponse llmResponse = LLMResponse.builder()
                .rawResponse(rawResponse)
                .formattedResponse(rawResponse)
                .confidenceScore(context.getAverageConfidence())
                .isFallback(false)
                .build();

            LLMResponse finalResponse = citationEngine.processCitations(
                llmResponse.getRawResponse(), context);
            finalResponse.setConfidenceScore(context.getAverageConfidence());
            finalResponse.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            System.out.println("Query processing completed in " + finalResponse.getProcessingTimeMs() + "ms");
            return finalResponse;

        } catch (Exception e) {
            System.err.println("Error processing query: " + e.getMessage());
            return LLMResponse.createFallback(
                "An error occurred while processing your query. Please try again later.",
                "ERROR", 0.0);
        }
    }

    /**
     * Builds a response purely from the assembled context entries, citing
     * each one. This stands in for a real LLM call - swap this method for
     * an actual model invocation (passing SystemPrompt.buildContextPrompt(...))
     * when wiring up a real completion API.
     */
    private String buildResponseFromContext(ContextResult context) {
        if (context.getContext() == null || context.getContext().isEmpty()) {
            return "No relevant information was found in the provided documents.";
        }

        StringBuilder response = new StringBuilder();
        response.append("Based on the available documentation:\n\n");
        for (ContextResult.ContextEntry entry : context.getContext()) {
            response.append("- ").append(entry.getText().trim()).append(" ").append(entry.getId()).append("\n");
        }
        return response.toString().trim();
    }

    // ---- Interactive CLI helpers ----

    private static void loadDocumentsFromUser(KnowledgeAssistant assistant, Scanner scanner) {
        System.out.println("Enter documents to add to the knowledge base.");
        System.out.println("(Press Enter on an empty 'Text' prompt when you're done adding documents.)\n");

        int docNumber = 1;
        while (true) {
            System.out.println("Document " + docNumber + ":");
            System.out.print("  Text (or press Enter to finish): ");
            String text = scanner.nextLine();
            if (text == null || text.trim().isEmpty()) {
                break;
            }

            System.out.print("  Source name: ");
            String source = scanner.nextLine();

            System.out.print("  Title: ");
            String title = scanner.nextLine();

            System.out.print("  Section (optional): ");
            String section = scanner.nextLine();

            System.out.print("  URL (optional): ");
            String url = scanner.nextLine();

            Chunk chunk = Chunk.builder()
                .id("chunk-" + docNumber)
                .text(text.trim())
                .source(source.trim())
                .title(title.trim())
                .section(section != null ? section.trim() : "")
                .url(url != null ? url.trim() : "")
                .createdAt(LocalDateTime.now())
                .build();

            assistant.addDocument(chunk);
            docNumber++;
            System.out.println();
        }

        System.out.println((docNumber - 1) + " document(s) added.\n");
    }

    public static void main(String[] args) {
        System.out.println("=== Internal Knowledge Assistant ===");
        System.out.println("Starting up...\n");

        KnowledgeAssistant assistant = new KnowledgeAssistant();
        Scanner scanner = new Scanner(System.in);

        loadDocumentsFromUser(assistant, scanner);

        System.out.print("Enter your question: ");
        String queryText = scanner.nextLine();

        System.out.print("Your user ID (optional): ");
        String userId = scanner.nextLine();

        System.out.print("Your department (optional): ");
        String department = scanner.nextLine();

        Query query = Query.builder()
            .id("Q-" + System.currentTimeMillis())
            .text(queryText)
            .userId(userId)
            .department(department)
            .timestamp(LocalDateTime.now())
            .build();

        System.out.println("\n--------------------------------------------------\n");

        LLMResponse response = assistant.processQuery(query);

        System.out.println("Response:\n");
        System.out.println(assistant.responseFormatter.formatForUI(response));

        System.out.println("\n--------------------------------------------------");
        System.out.println("Confidence: " + response.getConfidenceScore());
        System.out.println("Fallback: " + response.isFallback());
        System.out.println("Processing Time: " + response.getProcessingTimeMs() + "ms");

        if (!response.isFallback() && response.getSources() != null && !response.getSources().isEmpty()) {
            System.out.print("\nWas this response helpful? (y/n): ");
            String feedback = scanner.nextLine();
            FeedbackSignal signal = feedback.trim().equalsIgnoreCase("y")
                ? FeedbackSignal.POSITIVE
                : FeedbackSignal.NEGATIVE;
            for (var source : response.getSources()) {
                assistant.submitFeedback(source.getChunkId(), signal);
            }
        }

        System.out.println("\n=== Done ===");
        scanner.close();
    }
}
