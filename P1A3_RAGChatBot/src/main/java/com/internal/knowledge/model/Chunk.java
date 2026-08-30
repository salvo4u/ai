package com.internal.knowledge.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    private String id;
    private String text;
    private String source;
    private String title;
    private String section;
    private String url;
    private Map<String, Object> metadata;
    private double similarityScore;
    private double keywordScore;
    private double combinedScore;
    private LocalDateTime createdAt;
    private boolean isBelowThreshold;
    private boolean isTrimmed;
    private int originalTextLength;
}
