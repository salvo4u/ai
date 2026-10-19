import java.util.*;

public class GuardrailDemo {

    // Mock document store with per-document access lists
    static class Document {
        String id;
        String content;
        Set<String> allowedUsers;

        Document(String id, String content, String... allowed) {
            this.id = id;
            this.content = content;
            this.allowedUsers = new HashSet<>(Arrays.asList(allowed));
        }
    }

    // Guardrail: filters retrieval by user permissions
    static List<Document> retrieveDocuments(String userId, List<Document> allDocs) {
        List<Document> visible = new ArrayList<>();
        for (Document doc : allDocs) {
            if (doc.allowedUsers.contains(userId)) {
                visible.add(doc);
            }
        }
        // Audit log entry
        System.out.printf("Audit: user=%s retrieved=%s%n",
                userId,
                visible.stream().map(d -> d.id).toList());
        return visible;
    }

    public static void main(String[] args) {
        // Mock corpus
        List<Document> corpus = List.of(
                new Document("DOC-001", "Quarterly financial report", "alice"),
                new Document("DOC-002", "R&D roadmap", "alice", "bob"),
                new Document("DOC-003", "HR salary data", "hradmin")
        );

        // Two users
        String userA = "alice";
        String userB = "charlie";

        // Guardrail in action
        System.out.println("=== Retrieval for Alice ===");
        retrieveDocuments(userA, corpus).forEach(d -> System.out.println(d.content));

        System.out.println("\n=== Retrieval for Charlie ===");
        retrieveDocuments(userB, corpus).forEach(d -> System.out.println(d.content));

        // Expected output:
        // Alice sees DOC-001 and DOC-002
        // Charlie sees nothing (guardrail blocks unauthorized access)
    }
}