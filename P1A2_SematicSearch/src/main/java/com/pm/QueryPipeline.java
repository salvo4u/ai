package com.pm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
//import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

import java.util.*;

public class QueryPipeline {

    private static final String EMBEDDING_MODEL = "text-embedding-3-large";
    private static final String CHROMA_URL = "http://localhost:8000";
    private static final String COLLECTION_NAME = "randomdocs";

    enum RetrieverType {
        VECTOR,
        VECTOR_WITH_FILTER,
        HIGH_RECALL
    }

    enum DedupStrategy {
        NONE,
        BY_DOCUMENT,          // best chunk per sourceDocId
        BY_DOCUMENT_AND_CONTENT  // document + content hash
    }

    public static void main(String[] args) {
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(EMBEDDING_MODEL)
                .build();

        EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
                .baseUrl(CHROMA_URL)
                .collectionName(COLLECTION_NAME)
                //.apiVersion(ChromaApiVersion.V2)
                .build();

        Scanner scanner = new Scanner(System.in);

        RetrieverType currentRetriever = chooseRetriever(scanner);
        DedupStrategy currentDedup = chooseDedupStrategy(scanner);

        System.out.println("\n=== Semantic Search Ready ===");
        System.out.println("Commands: exit | quit | retriever | dedup\n");

        while (true) {
            System.out.print("Query> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;
            }

            if (input.equalsIgnoreCase("retriever")) {
                currentRetriever = chooseRetriever(scanner);
                continue;
            }

            if (input.equalsIgnoreCase("dedup")) {
                currentDedup = chooseDedupStrategy(scanner);
                continue;
            }

            search(input, currentRetriever, currentDedup, embeddingModel, embeddingStore, scanner);
            System.out.println();
        }

        scanner.close();
    }

    private static RetrieverType chooseRetriever(Scanner scanner) {
        System.out.println("\nSelect Retriever:");
        System.out.println("  1. Vector (Top 5)");
        System.out.println("  2. Vector + Metadata Filter");
        System.out.println("  3. High Recall Vector (Top 10)");
        System.out.print("Choice [1-3]: ");

        return switch (scanner.nextLine().trim()) {
            case "2" -> {
                System.out.println("→ Vector + Metadata Filter");
                yield RetrieverType.VECTOR_WITH_FILTER;
            }
            case "3" -> {
                System.out.println("→ High Recall (Top-10)");
                yield RetrieverType.HIGH_RECALL;
            }
            default -> {
                System.out.println("→ Pure Vector (Top-5)");
                yield RetrieverType.VECTOR;
            }
        };
    }
    private static Filter buildMetadataFilter(Scanner scanner) {
        System.out.println("\n--- Metadata Filters (leave empty to skip) ---");

        System.out.print("team     : ");
        String team = scanner.nextLine().trim();

        System.out.print("docType  : ");
        String docType = scanner.nextLine().trim();

        System.out.print("service  : ");
        String service = scanner.nextLine().trim();

        Filter filter = null;

        if (!team.isEmpty()) {
            filter = MetadataFilterBuilder.metadataKey("team").isEqualTo(team);
        }

        if (!docType.isEmpty()) {
            Filter docTypeFilter = MetadataFilterBuilder.metadataKey("docType").isEqualTo(docType);
            filter = (filter == null) ? docTypeFilter : filter.and(docTypeFilter);
        }

        if (!service.isEmpty()) {
            Filter serviceFilter = MetadataFilterBuilder.metadataKey("service").isEqualTo(service);
            filter = (filter == null) ? serviceFilter : filter.and(serviceFilter);
        }

        if (filter != null) {
            System.out.println("→ Applying metadata filter");
        } else {
            System.out.println("→ No metadata filter applied");
        }

        return filter;
    }
    private static DedupStrategy chooseDedupStrategy(Scanner scanner) {
        System.out.println("\nSelect Deduplication Strategy:");
        System.out.println("  1. None");
        System.out.println("  2. By Document only (sourceDocId)");
        System.out.println("  3. By Document + Content (recommended)");
        System.out.print("Choice [1-3]: ");

        return switch (scanner.nextLine().trim()) {
            case "2" -> {
                System.out.println("→ Dedup by Document");
                yield DedupStrategy.BY_DOCUMENT;
            }
            case "3" -> {
                System.out.println("→ Dedup by Document + Content");
                yield DedupStrategy.BY_DOCUMENT_AND_CONTENT;
            }
            default -> {
                System.out.println("→ No Deduplication");
                yield DedupStrategy.NONE;
            }
        };
    }

    private static void search(String userQuery,
                               RetrieverType retrieverType,
                               DedupStrategy dedupStrategy,
                               EmbeddingModel embeddingModel,
                               EmbeddingStore<TextSegment> embeddingStore,
                               Scanner scanner) {

        // 1. Embed query
        Embedding queryEmbedding = embeddingModel.embed(userQuery).content();

        int topK = (retrieverType == RetrieverType.HIGH_RECALL) ? 10 : 5;

        // 2. Always offer metadata filtering
        Filter filter = buildMetadataFilter(scanner);

        // 3. Build search request
        int fetchSize = (dedupStrategy == DedupStrategy.NONE) ? topK : topK * 4;

        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(fetchSize);

        if (filter != null) {
            requestBuilder.filter(filter);
        }

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(requestBuilder.build()).matches();

        if (matches.isEmpty()) {
            System.out.println("No relevant documents found.");
            return;
        }

        // 4. Apply deduplication
        List<EmbeddingMatch<TextSegment>> finalResults = applyDeduplication(matches, dedupStrategy, topK);

        // 5. Display results
        System.out.println("\nTop " + finalResults.size() + " results  [" + retrieverType + " | " + dedupStrategy + "]");
        System.out.println("──────────────────────────────────────────────────");

        int rank = 1;
        for (EmbeddingMatch<TextSegment> match : finalResults) {
            TextSegment segment = match.embedded();

            System.out.printf("#%d  Score: %.4f%n", rank, match.score());
            System.out.println("Title   : " + nullSafe(segment.metadata().getString("sourceDocTitle")));
            System.out.println("DocType : " + nullSafe(segment.metadata().getString("docType")));
            System.out.println("Team    : " + nullSafe(segment.metadata().getString("team")));
            System.out.println("Service : " + nullSafe(segment.metadata().getString("service")));
            System.out.println("Source  : " + nullSafe(segment.metadata().getString("sourceDocId")));

            String text = segment.text();
            String snippet = text.length() > 280 ? text.substring(0, 280) + "..." : text;
            System.out.println("Snippet : " + snippet);
            System.out.println("──────────────────────────────────────────────────");
            rank++;
        }
    }

    private static List<EmbeddingMatch<TextSegment>> applyDeduplication(
            List<EmbeddingMatch<TextSegment>> matches,
            DedupStrategy strategy,
            int topK) {

        if (strategy == DedupStrategy.NONE) {
            return matches.stream().limit(topK).toList();
        }

        // Level 1: Best chunk per Document
        Map<String, EmbeddingMatch<TextSegment>> bestPerDoc = new LinkedHashMap<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            String docId = match.embedded().metadata().getString("sourceDocId");
            if (docId == null || docId.isBlank()) {
                docId = "unknown-" + UUID.randomUUID();
            }

            if (!bestPerDoc.containsKey(docId) || match.score() > bestPerDoc.get(docId).score()) {
                bestPerDoc.put(docId, match);
            }
        }

        Collection<EmbeddingMatch<TextSegment>> afterDocDedup = bestPerDoc.values();

        if (strategy == DedupStrategy.BY_DOCUMENT) {
            return afterDocDedup.stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(topK)
                    .toList();
        }

        // Level 2: Also remove near-identical content
        Map<String, EmbeddingMatch<TextSegment>> uniqueContent = new LinkedHashMap<>();

        for (EmbeddingMatch<TextSegment> match : afterDocDedup) {
            String contentHash = Integer.toHexString(normalizeForHash(match.embedded().text()).hashCode());

            if (!uniqueContent.containsKey(contentHash) ||
                    match.score() > uniqueContent.get(contentHash).score()) {
                uniqueContent.put(contentHash, match);
            }
        }

        return uniqueContent.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    private static String normalizeForHash(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }

    private static String nullSafe(String value) {
        return value != null ? value : "N/A";
    }
}