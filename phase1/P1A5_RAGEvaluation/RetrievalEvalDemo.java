import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Minimal proof-of-concept: 5 sample questions -> retrieval -> per-question metrics.
 *
 * Demonstrates that the measurement mechanism works.
 * This is not a full production evaluation harness.
 */
public class RetrievalEvalDemo {

    // -------------------------------------------------------------------------
    // Tiny corpus: document ID -> text
    // -------------------------------------------------------------------------
    private static final Map<String, String> CORPUS = new LinkedHashMap<>();

    static {
        CORPUS.put("doc_payroll_policy",
                "Employees are paid on the last working day of each month. "
                        + "Overtime is calculated at 1.5x the hourly rate after 40 hours.");

        CORPUS.put("doc_leave_policy",
                "Annual leave entitlement is 20 days. "
                        + "Sick leave requires a medical certificate after 3 consecutive days. "
                        + "Maternity leave is 26 weeks.");

        CORPUS.put("doc_expense_policy",
                "Travel expenses must be pre-approved. "
                        + "Receipts are required for amounts over $25. "
                        + "Meals are capped at $50 per day.");

        CORPUS.put("doc_security_policy",
                "All laptops must use full-disk encryption. "
                        + "Passwords must be at least 12 characters and rotated every 90 days. "
                        + "MFA is mandatory for VPN.");

        CORPUS.put("doc_onboarding",
                "New hires complete IT setup on day 1. "
                        + "HR orientation is scheduled within the first week. "
                        + "Buddy assignment happens in the first 48 hours.");

        CORPUS.put("doc_remote_work",
                "Remote work is allowed up to 3 days per week with manager approval. "
                        + "Home office stipend is $500 per year. "
                        + "VPN must be used for all internal systems.");

        CORPUS.put("doc_benefits",
                "Health insurance starts on the first of the month after hire. "
                        + "401k matching is 4%. "
                        + "Gym reimbursement is $50 per month.");

        CORPUS.put("doc_code_of_conduct",
                "Harassment of any kind is prohibited. "
                        + "Conflicts of interest must be disclosed. "
                        + "Gifts over $100 require approval.");
    }

    // -------------------------------------------------------------------------
    // Evaluation dataset: 5 sample questions
    // -------------------------------------------------------------------------
    private static final List<EvaluationItem> EVAL_SET = List.of(
            new EvaluationItem(
                    "q1",
                    "When are employees paid and how is overtime calculated?",
                    List.of("doc_payroll_policy")),

            new EvaluationItem(
                    "q2",
                    "How many days of annual leave do I get and what about sick leave?",
                    List.of("doc_leave_policy")),

            new EvaluationItem(
                    "q3",
                    "What is the policy for travel expenses and meal limits?",
                    List.of("doc_expense_policy")),

            new EvaluationItem(
                    "q4",
                    "Do I need MFA for VPN and how often must I change my password?",
                    List.of("doc_security_policy")),

            new EvaluationItem(
                    "q5",
                    "Can I work from home and is there a home office stipend?",
                    List.of("doc_remote_work"))
    );

    // -------------------------------------------------------------------------
    // Simple retrieval: bag-of-words cosine similarity
    // This is a stand-in for the real retriever.
    // -------------------------------------------------------------------------
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    private static final Map<String, Map<String, Double>> DOC_VECTORS =
            buildDocumentVectors();

    private static List<String> tokenize(String text) {
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    private static Map<String, Double> buildTf(List<String> tokens) {
        Map<String, Double> termFrequency = new HashMap<>();

        for (String token : tokens) {
            termFrequency.merge(token, 1.0, Double::sum);
        }

        double norm = Math.sqrt(
                termFrequency.values()
                        .stream()
                        .mapToDouble(value -> value * value)
                        .sum()
        );

        if (norm == 0.0) {
            norm = 1.0;
        }

        final double finalNorm = norm;

        return termFrequency.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() / finalNorm
                ));
    }

    private static double cosine(
            Map<String, Double> vectorA,
            Map<String, Double> vectorB) {

        double result = 0.0;

        for (Map.Entry<String, Double> entry : vectorA.entrySet()) {
            result += entry.getValue()
                    * vectorB.getOrDefault(entry.getKey(), 0.0);
        }

        return result;
    }

    private static Map<String, Map<String, Double>> buildDocumentVectors() {
        Map<String, Map<String, Double>> vectors = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : CORPUS.entrySet()) {
            vectors.put(
                    entry.getKey(),
                    buildTf(tokenize(entry.getValue()))
            );
        }

        return vectors;
    }

    private static List<RankedDocument> retrieve(String query, int k) {
        Map<String, Double> queryVector = buildTf(tokenize(query));

        List<RankedDocument> rankedDocuments = DOC_VECTORS.entrySet()
                .stream()
                .map(entry -> new RankedDocument(
                        entry.getKey(),
                        cosine(queryVector, entry.getValue())
                ))
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed())
                .limit(k)
                .toList();

        return rankedDocuments;
    }

    // -------------------------------------------------------------------------
    // Retrieval metrics
    // -------------------------------------------------------------------------
    private static double recallAtK(
            List<String> retrievedIds,
            List<String> expected,
            int k) {

        if (expected.isEmpty()) {
            return 0.0;
        }

        Set<String> topK = new HashSet<>(
                retrievedIds.subList(0, Math.min(k, retrievedIds.size()))
        );

        long hits = expected.stream()
                .filter(topK::contains)
                .count();

        return (double) hits / expected.size();
    }

    private static double precisionAtK(
            List<String> retrievedIds,
            List<String> expected,
            int k) {

        if (k == 0) {
            return 0.0;
        }

        List<String> topK = retrievedIds.subList(
                0,
                Math.min(k, retrievedIds.size())
        );

        long hits = topK.stream()
                .filter(expected::contains)
                .count();

        return (double) hits / k;
    }

    private static double mrr(
            List<String> retrievedIds,
            List<String> expected) {

        Set<String> expectedSet = new HashSet<>(expected);

        for (int index = 0; index < retrievedIds.size(); index++) {
            if (expectedSet.contains(retrievedIds.get(index))) {
                return 1.0 / (index + 1);
            }
        }

        return 0.0;
    }

    private static double hitRateAtK(
            List<String> retrievedIds,
            List<String> expected,
            int k) {

        Set<String> topK = new HashSet<>(
                retrievedIds.subList(0, Math.min(k, retrievedIds.size()))
        );

        return expected.stream().anyMatch(topK::contains) ? 1.0 : 0.0;
    }

    // -------------------------------------------------------------------------
    // Run the 5 questions and print per-question scores
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=".repeat(72));
        System.out.println("RETRIEVAL EVALUATION DEMO — 5 sample questions");
        System.out.println("=".repeat(72));

        int k = 3;
        List<Double> allRecalls = new ArrayList<>();
        List<Double> allMrrs = new ArrayList<>();

        for (EvaluationItem item : EVAL_SET) {
            List<RankedDocument> ranked = retrieve(item.question(), 5);

            List<String> retrievedIds = ranked.stream()
                    .map(RankedDocument::documentId)
                    .toList();

            double recall = recallAtK(
                    retrievedIds,
                    item.expectedSourceDocs(),
                    k
            );

            double precision = precisionAtK(
                    retrievedIds,
                    item.expectedSourceDocs(),
                    k
            );

            double mrrScore = mrr(
                    retrievedIds,
                    item.expectedSourceDocs()
            );

            double hitRate = hitRateAtK(
                    retrievedIds,
                    item.expectedSourceDocs(),
                    k
            );

            allRecalls.add(recall);
            allMrrs.add(mrrScore);

            List<String> topRetrievedIds = retrievedIds.subList(
                    0,
                    Math.min(k, retrievedIds.size())
            );

            List<Double> topScores = ranked.subList(
                            0,
                            Math.min(k, ranked.size())
                    )
                    .stream()
                    .map(RankedDocument::score)
                    .map(score -> Math.round(score * 1000.0) / 1000.0)
                    .toList();

            System.out.printf("%n[%s] %s%n", item.questionId(), item.question());
            System.out.printf("  Expected : %s%n", item.expectedSourceDocs());
            System.out.printf("  Retrieved: %s (scores: %s)%n",
                    topRetrievedIds,
                    topScores);
            System.out.printf("  Recall@%d   : %.2f%n", k, recall);
            System.out.printf("  Precision@%d: %.2f%n", k, precision);
            System.out.printf("  MRR        : %.2f%n", mrrScore);
            System.out.printf("  HitRate@%d  : %.2f%n", k, hitRate);
        }

        double averageRecall = allRecalls.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double averageMrr = allMrrs.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        System.out.println("\n" + "=".repeat(72));
        System.out.printf(
                "AGGREGATE  Recall@%d: %.3f   MRR: %.3f%n",
                k,
                averageRecall,
                averageMrr
        );
        System.out.println("=".repeat(72));

        System.out.println("\nThis program proves the measurement loop works:");
        System.out.println(
                "  question -> retrieve -> compare against expectedSourceDocs "
                        + "-> emit metrics."
        );
        System.out.println(
                "In production, the same functions are called by the Evaluation Runner."
        );
    }

    // -------------------------------------------------------------------------
    // Data records
    // -------------------------------------------------------------------------
    private record EvaluationItem(
            String questionId,
            String question,
            List<String> expectedSourceDocs) {
    }

    private record RankedDocument(
            String documentId,
            double score) {
    }
}
