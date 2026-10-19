package com.internal.knowledge.query;

import com.internal.knowledge.model.Query;
import com.internal.knowledge.util.PIIRedactionUtil;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Processes and sanitizes an incoming user query before it enters the
 * downstream RAG retrieval pipeline.
 *
 * <p>The primary responsibility of this component is to detect and redact
 * Personally Identifiable Information (PII) from the user's query before
 * the query is used for embedding, retrieval, logging, or downstream LLM
 * processing.</p>
 *
 * <p>The query-processing flow is:</p>
 *
 * <pre>
 * Raw User Query
 *       |
 *       v
 * PII Detection
 *       |
 *       v
 * PII Redaction
 *       |
 *       v
 * Query Metadata Creation
 *       |
 *       v
 * Sanitized Query
 *       |
 *       v
 * Embedding / Retrieval
 * </pre>
 *
 * <p>In addition to sanitizing the query text, this class creates metadata
 * describing the query, including whether PII was detected, the redacted
 * representation, the initial intent, urgency score, entities and context.</p>
 */
public class QueryProcessor {

    /**
     * Utility responsible for detecting and redacting Personally
     * Identifiable Information from query text.
     */
    private final PIIRedactionUtil piiUtil;

    /**
     * Creates a QueryProcessor using the default PII redaction utility.
     */
    public QueryProcessor() {
        this.piiUtil = new PIIRedactionUtil();
    }

    /**
     * Processes an incoming query before it is passed to the retrieval
     * pipeline.
     *
     * <p>The original query text is first inspected for PII. A redacted
     * version is then generated. The resulting metadata is attached to the
     * query, and the query's text is replaced with the redacted version.</p>
     *
     * <p>This means downstream components receive the sanitized query rather
     * than the original raw text.</p>
     *
     * @param query incoming user query
     * @return the same {@link Query} instance after PII sanitization and
     *         metadata enrichment
     */
    public Query process(Query query) {

        /*
         * Create a sanitized representation of the query.
         *
         * Example:
         *
         *   Original:
         *   "What happened to John Smith, john@example.com?"
         *
         *   Redacted:
         *   "What happened to [NAME], [EMAIL]?"
         *
         * The exact replacement format is determined by PIIRedactionUtil.
         */
        String redactedText = piiUtil.redactPII(query.getText());

        /*
         * Determine whether the original query contained any PII.
         *
         * This is intentionally performed against the original text rather
         * than the already-redacted text because the redaction operation may
         * remove the very information we are trying to detect.
         */
        boolean hasPII = piiUtil.containsPII(query.getText());

        /*
         * Construct metadata describing how the query was processed.
         *
         * The metadata becomes useful to later stages for:
         *
         * - auditing
         * - observability
         * - query classification
         * - future intent detection
         * - analytics
         * - debugging
         */
        Query.QueryMetadata metadata = Query.QueryMetadata.builder()

                /*
                 * Indicates whether PII was detected in the original query.
                 */
                .containsPII(hasPII)

                /*
                 * Stores the sanitized representation of the query.
                 *
                 * Keeping this value in metadata provides visibility into the
                 * transformation without requiring downstream components to
                 * access the original sensitive text.
                 */
                .redactedText(redactedText)

                /*
                 * Default intent assigned to the query.
                 *
                 * The current implementation does not perform actual intent
                 * classification. Every query initially receives GENERAL_QUERY.
                 *
                 * A future implementation could replace this with an
                 * IntentClassifier.
                 */
                .intent("GENERAL_QUERY")

                /*
                 * Default urgency value.
                 *
                 * The current implementation assigns a neutral/default value
                 * of 0.5 to every query rather than calculating urgency from
                 * the user's actual request.
                 */
                .urgencyScore(0.5)

                /*
                 * Entity extraction is not currently implemented.
                 *
                 * An empty list provides a safe default while leaving room for
                 * future NER/entity extraction.
                 */
                .entities(new ArrayList<>())

                /*
                 * Additional query context is initialized as an empty map.
                 *
                 * Future processing stages can populate this with information
                 * such as:
                 *
                 * - user-selected filters
                 * - conversation context
                 * - document scope
                 * - product/team information
                 * - temporal constraints
                 */
                .context(new HashMap<>())

                /*
                 * Finish construction of the immutable/builder-based metadata
                 * object.
                 */
                .build();

        /*
         * Attach the generated metadata to the query.
         */
        query.setMetadata(metadata);

        /*
         * Replace the original query text with the sanitized version.
         *
         * This is the most important security boundary in this component:
         * downstream processing should use the redacted text instead of
         * propagating the original PII-containing query.
         */
        query.setText(redactedText);

        /*
         * Return the same Query object after enrichment and sanitization.
         */
        return query;
    }
}
