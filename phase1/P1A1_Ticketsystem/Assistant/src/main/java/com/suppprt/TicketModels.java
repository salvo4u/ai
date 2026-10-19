package com.suppprt;

import java.time.Instant;
import java.util.List;

/**
 * All data models as Java Records (JDK 21)
 * No boilerplate - records give us constructor, getters, equals, hashCode, toString
 */
public class TicketModels {

    // ─── Request Models ──────────────────────────────────────────────

    public record TicketRequest(
            String ticketId,
            String sourceSystem,
            Instant receivedAt,
            String rawText,
            String subject,
            CustomerContext customerContext,
            AgingMetrics agingMetrics,
            RoutingHints routingHints
    ) {}

    public record CustomerContext(
            String customerId,
            String customerTier,        // premium, business, standard, trial
            int accountAgeDays,
            String preferredLanguage,
            String region,
            List<PreviousTicket> previousTickets
    ) {}

    public record PreviousTicket(
            String ticketId,
            String category,
            String summary
    ) {}

    public record AgingMetrics(
            long secondsSinceCreation,
            long secondsSinceLastAgentProcessed,
            int escalations
    ) {}

    public record RoutingHints(
            String preferredLanguage,
            String region,
            String departmentHint,
            boolean requiresUrgentEscalation
    ) {}

    // ─── Response Models ──────────────────────────────────────────────

    public record LLMResponse(
            String category,            // billing, technical, account, feature_request, complaint, other
            String urgency,             // P1, P2, P3, P4
            String draftReply,
            double confidenceScore,
            Reasoning reasoning,
            String modelVersion,
            int tokensUsed,
            long inferenceTimeMs
    ) {}

    public record Reasoning(
            String urgencyRationale,
            String categoryRationale,
            List<String> suggestedActions
    ) {}

    // ─── Pipeline Output ──────────────────────────────────────────────

    public record PipelineOutput(
            String ticketId,
            String redactedText,
            List<RedactionEntry> redactionLog,
            LLMResponse classification,
            RoutingDecision routing,
            String status
    ) {}

    public record RedactionEntry(
            String type,
            String original,
            String replacement
    ) {}

    public record RoutingDecision(
            String path,                // HIGH_CONFIDENCE, LOW_CONFIDENCE, VERY_LOW_CONFIDENCE
            boolean draftCanBeUsed,
            boolean reviewRequired,
            String reason,
            String priority             // urgent, normal
    ) {}

    // ─── Helper Factory Methods ──────────────────────────────────────

    public static TicketRequest sampleTicket() {
        return new TicketRequest(
                "TKT-2026-08-15-001",
                "email",
                Instant.now(),
                """
                I'm unable to access my dashboard since this morning. 
                I keep getting a 503 error. I have an important client 
                meeting in 30 minutes and need this resolved ASAP. 
                I've tried clearing cache and using different browsers 
                but nothing works. This is costing me business.
                """,
                "URGENT: Dashboard down - 503 error",
                new CustomerContext(
                        "cust_prem_7890xyz",
                        "premium",
                        456,
                        "en",
                        "us-east",
                        List.of(
                                new PreviousTicket("TKT-2026-08-10-023", "technical", "API rate limit issue - resolved"),
                                new PreviousTicket("TKT-2026-07-28-089", "billing", "Invoice discrepancy - corrected")
                        )
                ),
                new AgingMetrics(1800, 1800, 0),
                new RoutingHints("en", "us-east", "technical-support", true)
        );
    }
}