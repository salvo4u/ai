
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.suppprt.LlmClient;
import com.suppprt.TicketModels;

import static com.suppprt.TicketModels.*;

/**
 * Complete ticket processing pipeline using JDK 21 features
 * Run with: java TicketPipeline.java (if you use Java 21+ source launcher)
 * Or: javac TicketPipeline.java && java TicketPipeline
 */
public class TicketPipeline {

    private final LlmClient llmClient;
    private final ObjectMapper mapper;

    // PII patterns
    private static final List<PatternEntry> PII_PATTERNS = List.of(
            new PatternEntry("EMAIL", Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")),
            new PatternEntry("PHONE", Pattern.compile("\\+?1?[-. ]?\\(?[0-9]{3}\\)?[-. ]?[0-9]{3}[-. ]?[0-9]{4}")),
            new PatternEntry("SSN", Pattern.compile("\\d{3}-\\d{2}-\\d{4}")),
            new PatternEntry("CREDIT_CARD", Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}"))
    );

    record PatternEntry(String type, Pattern pattern) {}

    public TicketPipeline(String apiKey) {
        this.llmClient = new LlmClient(apiKey);
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ─── STEP 1: PII REDACTION ──────────────────────────────────────

    private record RedactionResult(String text, List<RedactionEntry> log) {}

    private RedactionResult redactPII(String text) {
        var log = new ArrayList<TicketModels.RedactionEntry>();
        var result = text;

        for (var entry : PII_PATTERNS) {
            var matcher = entry.pattern().matcher(result);
            while (matcher.find()) {
                var original = matcher.group();
                var replacement = "[REDACTED:" + entry.type() + "]";
                result = result.replace(original, replacement);
                log.add(new RedactionEntry(entry.type(), original, replacement));
            }
        }

        return new RedactionResult(result, log);
    }

    // ─── STEP 2: BUILD PROMPT ──────────────────────────────────────

    private String buildSystemPrompt() {
        return """
        You are an AI assistant for a customer support team. Your task is to:
        1. Classify the urgency of the ticket (P1-P4)
        2. Classify the category of the ticket
        3. Draft a professional first-response reply
        
        URGENCY GUIDELINES:
        - P1: Critical outage, security issue, or premium customer with urgent need
        - P2: Major feature broken, significant impact on business
        - P3: Minor issue, question, or standard request
        - P4: Feature request, low priority inquiry
        
        CATEGORIES:
        - billing: Payment issues, invoices, refunds
        - technical: Bugs, errors, performance issues
        - account: Login, password, profile management
        - feature_request: New features or enhancements
        - complaint: Dissatisfaction with product/service
        - other: Everything else
        
        RESPONSE FORMAT:
        Return valid JSON with this schema:
        {
            "category": "billing|technical|account|feature_request|complaint|other",
            "urgency": "P1|P2|P3|P4",
            "draftReply": "string (max 500 chars)",
            "confidenceScore": 0.0-1.0,
            "reasoning": {
                "urgencyRationale": "string",
                "categoryRationale": "string"
            }
        }
        """;
    }

    private String buildUserPrompt(TicketRequest request, String redactedText) {
        var prevTickets = request.customerContext().previousTickets();
        var prevText = prevTickets.isEmpty() ? "" :
                "\nPrevious Tickets:\n" + prevTickets.stream()
                        .limit(5)
                        .map(pt -> "  - [" + pt.category() + "] " + pt.summary())
                        .collect(Collectors.joining("\n"));

        return """
        INCOMING TICKET:
        Ticket ID: %s
        Source: %s
        Subject: %s
        Customer Tier: %s
        Preferred Language: %s
        Region: %s
        Created: %d seconds ago
        Escalations: %d
        %s
        
        CUSTOMER MESSAGE:
        %s
        
        Analyze this ticket and respond with JSON only.
        """.formatted(
                request.ticketId(),
                request.sourceSystem(),
                request.subject(),
                request.customerContext().customerTier(),
                request.routingHints().preferredLanguage(),
                request.routingHints().region(),
                request.agingMetrics().secondsSinceCreation(),
                request.agingMetrics().escalations(),
                prevText,
                redactedText
        );
    }

    // ─── STEP 3: PARSE & VALIDATE ──────────────────────────────────

    @SuppressWarnings("unchecked")
    private LLMResponse parseAndValidate(String rawOutput) throws Exception {
        var json = mapper.readValue(rawOutput, java.util.Map.class);

        // Extract fields
        var category = (String) json.getOrDefault("category", "other");
        var urgency = (String) json.getOrDefault("urgency", "P3");
        var draft = (String) json.getOrDefault("draftReply", "");
        var confidence = ((Number) json.getOrDefault("confidenceScore", 0.5)).doubleValue();
        confidence = Math.max(0.0, Math.min(1.0, confidence));

        var reasoningMap = (java.util.Map<String, String>) json.getOrDefault("reasoning", java.util.Map.of());
        var reasoning = new Reasoning(
                reasoningMap.getOrDefault("urgencyRationale", "No rationale provided"),
                reasoningMap.getOrDefault("categoryRationale", "No rationale provided"),
                List.of()
        );

        // Validate enums
        var validCategories = List.of("billing", "technical", "account", "feature_request", "complaint", "other");
        var validUrgencies = List.of("P1", "P2", "P3", "P4");

        if (!validCategories.contains(category)) category = "other";
        if (!validUrgencies.contains(urgency)) urgency = "P3";

        return new LLMResponse(
                category, urgency, draft, confidence, reasoning,
                llmClient.model, 0, 0
        );
    }

    // ─── STEP 4: ROUTING DECISION ──────────────────────────────────

    private RoutingDecision route(LLMResponse response) {
        var confidence = response.confidenceScore();
        var urgency = response.urgency();

        if (confidence >= 0.7) {
            return new RoutingDecision(
                    "HIGH_CONFIDENCE",
                    true,
                    true,
                    "High confidence classification",
                    "P1".equals(urgency) ? "urgent" : "normal"
            );
        } else if (confidence >= 0.5) {
            return new RoutingDecision(
                    "LOW_CONFIDENCE",
                    false,
                    true,
                    "Low confidence - requires human review",
                    "normal"
            );
        } else {
            return new RoutingDecision(
                    "VERY_LOW_CONFIDENCE",
                    false,
                    true,
                    "Very low confidence - manual classification required",
                    "normal"
            );
        }
    }

    // ─── MAIN PIPELINE ─────────────────────────────────────────────

    public PipelineOutput process(TicketRequest request) throws Exception {
        System.out.println("\n" + "█".repeat(80));
        System.out.println("█  AI SUPPORT TICKET PIPELINE");
        System.out.println("█".repeat(80));

        // Step 1: PII Redaction
        System.out.println("\n📌 STEP 1: PII REDACTION");
        var redaction = redactPII(request.rawText());
        System.out.println("   PII found: " + redaction.log().size());
        redaction.log().forEach(log ->
                System.out.println("   - " + log.type() + ": " + log.original() + " → " + log.replacement())
        );

        // Step 2: Build Prompt
        System.out.println("\n📌 STEP 2: BUILD PROMPT");
        var systemPrompt = buildSystemPrompt();
        var userPrompt = buildUserPrompt(request, redaction.text());
        System.out.println("   System prompt: " + systemPrompt.length() + " chars");
        System.out.println("   User prompt: " + userPrompt.length() + " chars");

        // Step 3: Call LLM
        System.out.println("\n📌 STEP 3: CALL LLM");
        String rawOutput;
        try {
            rawOutput = llmClient.call(systemPrompt, userPrompt);
            System.out.println("   ✅ LLM response received");
        } catch (Exception e) {
            System.err.println("   ❌ LLM failed: " + e.getMessage());
            // Mock fallback
            rawOutput = """
                {
                    "category": "technical",
                    "urgency": "P1",
                    "draftReply": "We're investigating your issue. Our team will respond shortly.",
                    "confidenceScore": 0.85,
                    "reasoning": {
                        "urgencyRationale": "Critical issue reported",
                        "categoryRationale": "Technical error"
                    }
                }
                """;
            System.out.println("   ⚠️ Using mock fallback response");
        }

        // Step 4: Parse & Validate
        System.out.println("\n📌 STEP 4: PARSE & VALIDATE");
        var response = parseAndValidate(rawOutput);
        System.out.println("   Category: " + response.category());
        System.out.println("   Urgency: " + response.urgency());
        System.out.println("   Confidence: " + String.format("%.2f%%", response.confidenceScore() * 100));

        // Step 5: Routing
        System.out.println("\n📌 STEP 5: ROUTING DECISION");
        var routing = route(response);
        System.out.println("   Path: " + routing.path());
        System.out.println("   Draft can be used: " + routing.draftCanBeUsed());
        System.out.println("   Review required: " + routing.reviewRequired());

        // Step 6: Output
        System.out.println("\n📌 STEP 6: FINAL OUTPUT");
        var output = new PipelineOutput(
                request.ticketId(),
                redaction.text(),
                redaction.log(),
                response,
                routing,
                routing.reviewRequired() ? "ready_for_review" : "auto_approved"
        );

        // Print JSON
        System.out.println("\n📦 STRUCTURED OUTPUT:");
        var json = mapper.writeValueAsString(output);
        System.out.println(json);

        // Save to file
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("ticket_processing_output.json"),
                json
        );
        System.out.println("\n📁 Output saved to: ticket_processing_output.json");

        System.out.println("\n" + "█".repeat(80));
        System.out.println("█  ✅ PIPELINE COMPLETE");
        System.out.println("█".repeat(80));

        return output;
    }

    // ─── MAIN ──────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // Check for API key

        //var apiKey = System.getenv("OPENAI_API_KEY");
        //OPEN API
        var apiKey = "";
        //MISTRAL API
        //var apiKey = "";
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("⚠️  WARNING: OPENAI_API_KEY not set");
            System.out.println("   Using mock mode with fallback responses");
            apiKey = "MOCK_KEY";
        }

        // Run pipeline
        var pipeline = new TicketPipeline(apiKey);
        var sample = TicketModels.sampleTicket();
        pipeline.process(sample);
    }
}