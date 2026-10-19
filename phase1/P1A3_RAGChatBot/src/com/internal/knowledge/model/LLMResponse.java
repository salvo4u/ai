package com.internal.knowledge.model;

import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {
    private String rawResponse;
    private String formattedResponse;
    private List<Citation> citations;
    private List<Source> sources;
    private double confidenceScore;
    private int tokenCount;
    private boolean isFallback;
    private String fallbackType;
    private long processingTimeMs;

    public static LLMResponse createFallback(String message, String type, double confidence) {
        return LLMResponse.builder()
            .rawResponse(message)
            .formattedResponse(message)
            .citations(new ArrayList<>())
            .sources(new ArrayList<>())
            .confidenceScore(confidence)
            .isFallback(true)
            .fallbackType(type)
            .build();
    }
}
