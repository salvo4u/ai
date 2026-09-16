import java.util.*;
import java.util.stream.Collectors;

/**
 * Proof-of-concept LLM Routing Layer selection logic.
 * No network calls - pure in-memory registry + rules + scoring.
 */
public class RoutingDemo {

    // ---------- Model Registry ----------
    static class Model {
        final String modelId;
        final String provider;
        final int contextWindow;
        final double costInputPer1k;   // cents
        final double costOutputPer1k;  // cents
        final int p95LatencyMs;
        final Set<String> capabilityTags;
        final double qualityScore;     // 0.0 - 1.0
        final String status;           // healthy | degraded | down
        final int priority;            // lower = preferred

        Model(String modelId, String provider, int contextWindow,
              double costInputPer1k, double costOutputPer1k, int p95LatencyMs,
              Set<String> capabilityTags, double qualityScore, String status, int priority) {
            this.modelId = modelId;
            this.provider = provider;
            this.contextWindow = contextWindow;
            this.costInputPer1k = costInputPer1k;
            this.costOutputPer1k = costOutputPer1k;
            this.p95LatencyMs = p95LatencyMs;
            this.capabilityTags = capabilityTags;
            this.qualityScore = qualityScore;
            this.status = status;
            this.priority = priority;
        }

        double estimatedCostCents(int inputTokens, int outputTokens) {
            return (inputTokens / 1000.0) * costInputPer1k
                 + (outputTokens / 1000.0) * costOutputPer1k;
        }
    }

    // ---------- Request ----------
    static class RouteRequest {
        final String requestId;
        final String taskType;
        final String prompt;
        final int maxLatencyMs;
        final String qualityTier;          // economy | standard | premium | frontier
        final Double maxCostCents;          // optional
        final Set<String> requiredCapabilities;
        final String fallbackPolicy;        // strict | degrade | best_effort

        RouteRequest(String requestId, String taskType, String prompt,
                     int maxLatencyMs, String qualityTier, Double maxCostCents,
                     Set<String> requiredCapabilities, String fallbackPolicy) {
            this.requestId = requestId;
            this.taskType = taskType;
            this.prompt = prompt;
            this.maxLatencyMs = maxLatencyMs;
            this.qualityTier = qualityTier;
            this.maxCostCents = maxCostCents;
            this.requiredCapabilities = requiredCapabilities;
            this.fallbackPolicy = fallbackPolicy;
        }
    }

    // ---------- Complexity assessment (rules only) ----------
    static class ClassificationResult {
        final String complexity;   // low | medium | high
        final int estimatedTokens;
        final Set<String> inferredCapabilities;

        ClassificationResult(String complexity, int estimatedTokens, Set<String> inferredCapabilities) {
            this.complexity = complexity;
            this.estimatedTokens = estimatedTokens;
            this.inferredCapabilities = inferredCapabilities;
        }
    }

    static ClassificationResult classify(RouteRequest req) {
        int tokens = Math.max(1, req.prompt.length() / 4); // rough token estimate
        String lower = req.prompt.toLowerCase();
        boolean reasoningKeywords = lower.matches(
                ".*(why|compare|explain|step.?by.?step|reason|analyze|evaluate|impact).*");
        boolean longContext = tokens > 2000;
        boolean codeLike = lower.contains("code") || lower.contains("function")
                || lower.contains("algorithm") || req.taskType.equals("code_generation");

        String complexity;
        if (tokens > 1500 || reasoningKeywords || req.taskType.equals("complex_reasoning")) {
            complexity = "high";
        } else if (tokens > 400 || codeLike) {
            complexity = "medium";
        } else {
            complexity = "low";
        }

        Set<String> caps = new HashSet<>(req.requiredCapabilities);
        if (reasoningKeywords) caps.add("reasoning");
        if (longContext) caps.add("long_context");
        if (codeLike) caps.add("code");
        if (req.taskType.equals("support_chat")) caps.add("support");
        caps.add("chat"); // baseline

        return new ClassificationResult(complexity, tokens, caps);
    }

    // ---------- Cost ceilings per tier (from decision log) ----------
    static double costCeiling(String tier) {
        return switch (tier) {
            case "economy"  -> 0.002;
            case "standard" -> 0.01;
            case "premium"  -> 0.05;
            case "frontier" -> 0.25;
            default         -> 0.01;
        };
    }

    // ---------- Selection logic ----------
    static class SelectionResult {
        final Model model;
        final String reason;

        SelectionResult(Model model, String reason) {
            this.model = model;
            this.reason = reason;
        }
    }

    static SelectionResult selectModel(RouteRequest req, ClassificationResult cls,
                                       List<Model> registry, Set<String> excludeIds) {

        double ceiling = req.maxCostCents != null
                ? Math.min(req.maxCostCents, costCeiling(req.qualityTier))
                : costCeiling(req.qualityTier);

        // Stage 1 - hard filters
        List<Model> candidates = registry.stream()
                .filter(m -> !excludeIds.contains(m.modelId))
                .filter(m -> "healthy".equals(m.status) || "degraded".equals(m.status))
                .filter(m -> m.contextWindow >= cls.estimatedTokens + 500)
                .filter(m -> m.capabilityTags.containsAll(cls.inferredCapabilities))
                .filter(m -> m.estimatedCostCents(cls.estimatedTokens, 150) <= ceiling)
                .filter(m -> m.p95LatencyMs <= req.maxLatencyMs * 1.5) // grace factor
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            // relax cost & latency a bit
            candidates = registry.stream()
                    .filter(m -> !excludeIds.contains(m.modelId))
                    .filter(m -> "healthy".equals(m.status))
                    .filter(m -> m.contextWindow >= cls.estimatedTokens + 500)
                    .filter(m -> m.capabilityTags.containsAll(cls.inferredCapabilities))
                    .filter(m -> m.estimatedCostCents(cls.estimatedTokens, 150) <= ceiling * 2.0)
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            return new SelectionResult(null, "No model satisfied hard filters");
        }

        // Stage 2 - scoring (higher is better)
        Model best = null;
        double bestScore = -1;
        String bestReason = "";

        for (Model m : candidates) {
            double estCost = m.estimatedCostCents(cls.estimatedTokens, 150);
            double normCost = Math.min(1.0, estCost / Math.max(ceiling, 0.0001));
            double normLat  = Math.min(1.0, m.p95LatencyMs / (double) Math.max(req.maxLatencyMs, 1));

            double wQ = (req.qualityTier.equals("frontier") || req.qualityTier.equals("premium")) ? 0.45 : 0.25;
            double wC = req.qualityTier.equals("economy") ? 0.45 : 0.25;
            double wL = 0.20;
            double wP = 0.10;

            double score = wQ * m.qualityScore
                         + wC * (1.0 - normCost)
                         + wL * (1.0 - normLat)
                         + wP * (1.0 - m.priority / 10.0);

            // complexity bias
            if (cls.complexity.equals("high") && m.qualityScore >= 0.85) score += 0.18;
            if (cls.complexity.equals("low")  && m.qualityScore <= 0.65) score += 0.08;

            if (score > bestScore) {
                bestScore = score;
                best = m;
                bestReason = String.format(
                    "score=%.3f (Q=%.2f C=%.2f L=%.2f) | complexity=%s | ceiling=$%.4f | tags=%s",
                    score, m.qualityScore, 1.0 - normCost, 1.0 - normLat,
                    cls.complexity, ceiling, m.capabilityTags);
            }
        }

        return new SelectionResult(best, bestReason);
    }

    // ---------- Sample registry ----------
    static List<Model> buildRegistry() {
        return List.of(
            new Model("openai/gpt-4o", "openai", 128000,
                    0.25, 1.00, 1800,
                    Set.of("chat", "reasoning", "code", "vision", "long_context", "tool_use", "support"),
                    0.95, "healthy", 1),

            new Model("anthropic/claude-3.5-sonnet", "anthropic", 200000,
                    0.30, 1.50, 2200,
                    Set.of("chat", "reasoning", "code", "long_context", "tool_use", "support"),
                    0.93, "healthy", 2),

            new Model("openai/gpt-4o-mini", "openai", 128000,
                    0.015, 0.06, 900,
                    Set.of("chat", "reasoning", "code", "long_context", "support"),
                    0.78, "healthy", 3),

            new Model("openai/gpt-3.5-turbo", "openai", 16385,
                    0.05, 0.15, 600,
                    Set.of("chat", "code", "support"),
                    0.65, "healthy", 4),

            new Model("internal/support-specialist", "internal", 8192,
                    0.002, 0.005, 300,
                    Set.of("chat", "support"),
                    0.52, "healthy", 5)
        );
    }

    // ---------- Main demo ----------
    public static void main(String[] args) {
        List<Model> registry = buildRegistry();

        List<RouteRequest> samples = List.of(
            // 1. Simple support - expect cheap specialist
            new RouteRequest("req-001", "support_chat",
                    "How do I reset my password?",
                    800, "economy", 0.005,
                    Set.of("chat"), "degrade"),

            // 2. Medium summary + recommendation
            new RouteRequest("req-002", "search_summarize",
                    "Summarize the key differences between the three pricing plans and recommend which one fits a 20-person startup.",
                    1500, "standard", null,
                    Set.of("chat"), "degrade"),

            // 3. High complexity multi-step reasoning - expect frontier-class
            new RouteRequest("req-003", "complex_reasoning",
                    "Compare the long-term economic impact of carbon tax versus cap-and-trade, considering political feasibility, enforcement costs, and distributional effects across income quintiles. Provide a step-by-step analysis.",
                    4000, "frontier", 0.20,
                    Set.of("reasoning"), "strict"),

            // 4. Code + explanation with moderate budget
            new RouteRequest("req-004", "code_generation",
                    "Write a Java function that merges two sorted lists in O(n) time and explain the algorithm.",
                    1500, "premium", 0.04,
                    Set.of("code", "reasoning"), "degrade")
        );

        System.out.println("=== LLM Routing Layer - Selection Proof ===\n");

        for (RouteRequest req : samples) {
            ClassificationResult cls = classify(req);
            SelectionResult sel = selectModel(req, cls, registry, Set.of());

            System.out.println("Request : " + req.requestId + "  [" + req.taskType + "]");
            System.out.println("Prompt  : " + (req.prompt.length() > 80
                    ? req.prompt.substring(0, 77) + "..." : req.prompt));
            System.out.println("Tier    : " + req.qualityTier
                    + "  |  maxLatency=" + req.maxLatencyMs + "ms"
                    + "  |  complexity=" + cls.complexity
                    + "  |  ~tokens=" + cls.estimatedTokens);
            if (sel.model != null) {
                System.out.println("SELECTED: " + sel.model.modelId
                        + "  (quality=" + sel.model.qualityScore
                        + ", p95=" + sel.model.p95LatencyMs + "ms)");
                System.out.println("Why     : " + sel.reason);
            } else {
                System.out.println("SELECTED: NONE - " + sel.reason);
            }
            System.out.println();
        }
    }
}
