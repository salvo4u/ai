package com.internal.knowledge.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Query {
    private String id;
    private String text;
    private String userId;
    private String department;
    private List<String> roles;
    private LocalDateTime timestamp;
    private QueryMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryMetadata {
        private String intent;
        private double urgencyScore;
        private List<String> entities;
        private boolean containsPII;
        private String redactedText;
        private Map<String, Object> context;
    }
}
