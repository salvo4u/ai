package com.internal.knowledge.util;

import java.util.List;

public class TokenEstimator {
    private static final double CHARS_PER_TOKEN = 4.0;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public int estimateTokensForBatch(List<String> texts) {
        return texts.stream().mapToInt(this::estimateTokens).sum();
    }
}
