package com.internal.knowledge.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TextSimilarityUtil {
    public double jaccardSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    public double calculateKeywordOverlap(String query, String text) {
        if (query == null || text == null || query.isEmpty() || text.isEmpty()) {
            return 0.0;
        }
        String[] queryTerms = query.toLowerCase().split("\\s+");
        Set<String> querySet = new HashSet<>(Arrays.asList(queryTerms));
        Set<String> textSet = new HashSet<>(Arrays.asList(text.toLowerCase().split("\\s+")));
        Set<String> overlap = new HashSet<>(querySet);
        overlap.retainAll(textSet);
        return querySet.isEmpty() ? 0.0 : (double) overlap.size() / querySet.size();
    }
}
