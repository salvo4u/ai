# Internal Knowledge Assistant — Package Restructuring

The original single-file `KnowledgeAssistant.java` (all classes crammed into one file
with package-private visibility) has been split into a proper multi-package Maven
layout. Behavior is unchanged — this is a structural refactor only.

## Package layout

```
com.internal.knowledge
├── KnowledgeAssistant.java        Main application / entry point (main())
│
├── model/                         Plain data classes (Lombok @Data/@Builder)
│   ├── Query.java                 (+ nested Query.QueryMetadata)
│   ├── Chunk.java
│   ├── ContextResult.java         (+ nested ContextResult.ContextEntry)
│   ├── Citation.java
│   ├── Source.java
│   ├── FallbackResponse.java
│   ├── LLMResponse.java           (+ static factory createFallback)
│   └── FAQDocument.java
│
├── util/                          Stateless helper utilities
│   ├── TokenEstimator.java
│   ├── TextSimilarityUtil.java
│   └── PIIRedactionUtil.java
│
├── prompt/
│   └── SystemPrompt.java          Grounding system prompt + prompt builder
│
├── context/
│   └── ContextAssembler.java      Dedup / rank / filter / token-budget logic
│                                  (+ nested ScoredSentence, ContextAssemblerConfig)
│
├── fallback/
│   └── FallbackHandler.java       No-match / low-confidence / FAQ fallback logic
│
├── citation/
│   └── CitationEngine.java        Citation extraction + formatting
│                                  (+ nested CitationExtract, CitationConfig)
│
├── format/
│   └── ResponseFormatter.java     UI / email response formatting
│
└── query/
    └── QueryProcessor.java        PII redaction + query pre-processing
```

## Notes on the refactor

- All classes are now `public` (one top-level class per file, as Java requires)
  and live in the package that matches their directory.
- Cross-package references were resolved with explicit `import` statements
  (e.g. `context` imports from `model` and `util`; `KnowledgeAssistant` imports
  from every sub-package it orchestrates).
- Nested/static inner classes (`Query.QueryMetadata`, `ContextResult.ContextEntry`,
  `ContextAssembler.ContextAssemblerConfig`, `CitationEngine.CitationConfig`, etc.)
  were kept nested inside their owning class, exactly as in the original file.
- No logic, formatting strings, thresholds, or algorithms were changed.
- A minimal `pom.xml` is included (Java 17, Lombok as a `provided` dependency)
  so the project can be built directly with `mvn package`.

## Build

```
mvn clean package
java -cp target/classes com.internal.knowledge.KnowledgeAssistant
```
