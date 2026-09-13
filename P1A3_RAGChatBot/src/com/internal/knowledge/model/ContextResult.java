package com.internal.knowledge.model;

import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextResult {
    private List<ContextEntry> context;
    private List<Source> sources;
    private int totalTokens;
    private boolean isFallback;
    private double averageConfidence;
    private int contextWindowUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextEntry {
        private String id;
        private String text;
        private String source;
        private String section;
        private double relevanceScore;
        private Map<String, Object> metadata;
    }
}
