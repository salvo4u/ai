package com.pm;// IngestionPipeline.java

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
//import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public  class IngestionPipeline {

    // ---------- Config ----------
    private static final int CHUNK_SIZE = 900;          // target 800-1000 tokens
    private static final int CHUNK_OVERLAP = 180;       // ~20%
    private static final String EMBEDDING_MODEL = "text-embedding-3-large";
    private static final String CHROMA_URL = "http://localhost:8000";
    private static final String COLLECTION_NAME = "randomdocs";
    private static final String OPENAI_API_KEY = "some_openapikey";
    private static final String DOCUMENTS = "./documents";

    public static void main(String[] args) {
        runIngestion(DOCUMENTS);
    }

    // ---------- Main Pipeline ----------
    public static void runIngestion(String sourceDir) {
        System.out.println("=== Starting Ingestion Pipeline ===");

        // 1. Fetch / Load documents
        List<Document> rawDocs = loadDocuments(sourceDir);

        // 2. Clean & Normalize + enrich metadata
        List<Document> cleanedDocs = rawDocs.stream()
                .map(IngestionPipeline::cleanDocument)
                .toList();

        // 3 + 4 + 5. Chunk → Embed → Upsert
        ingestToChroma(cleanedDocs);

        System.out.println("=== Ingestion Complete ===");
    }

    // ---------- 1. Load Documents ----------
    private static List<Document> loadDocuments(String sourceDir) {
        Path path = Paths.get(sourceDir);
        List<Document> docs = FileSystemDocumentLoader.loadDocuments(path);
        System.out.println("Loaded " + docs.size() + " documents");
        return docs;
    }

    // ---------- 2. Clean & Normalize ----------
    private static Document cleanDocument(Document doc) {
        String text = doc.text()
                .replace("\r\n", "\n")
                .trim()
                .replaceAll("\n{3,}", "\n\n");   // collapse excessive blank lines

        Metadata metadata = new Metadata();
        metadata.put("sourceDocId", doc.metadata().getString("source") != null
                ? doc.metadata().getString("source")
                : UUID.randomUUID().toString());
        metadata.put("sourceDocTitle", extractFileName(doc));
        metadata.put("docType", "guide");               // design_doc | runbook | postmortem | guide
        metadata.put("team", "platform");
        metadata.put("service", "unknown");
        metadata.put("sourceSystem", "local");
        metadata.put("lastUpdated", Instant.now().toString());
        metadata.put("createdAt", Instant.now().toString());
        metadata.put("checksum", Integer.toHexString(text.hashCode()));

        return Document.from(text, metadata);
    }

    private static String extractFileName(Document doc) {
        String source = doc.metadata().getString("source");
        if (source == null) return "unknown";
        return Paths.get(source).getFileName().toString();
    }

    // ---------- 3 + 4 + 5. Chunk + Embed + Upsert ----------
    private static void ingestToChroma(List<Document> docs) {
        // Embedding model
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName(EMBEDDING_MODEL)
                .build();

        // Chroma vector store
        EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
                .baseUrl(CHROMA_URL)
                .collectionName(COLLECTION_NAME)

                .build();

        // Structure-aware recursive splitter (prefers headings → paragraphs → sentences)
        var splitter = DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP);

        // Ingestor = chunk + embed + store in one step
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(docs);
        System.out.println("Upserted chunks into ChromaDB (" + COLLECTION_NAME + ")");
    }
}