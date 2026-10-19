package com.internal.knowledge.model;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FallbackResponse {
    private String type;
    private String message;
    private double confidence;
    private List<String> suggestions;
    private boolean escalationNeeded;
    private Source source;
    private boolean isFaq;
    private String faqSource;
    private double similarityScore;
}
