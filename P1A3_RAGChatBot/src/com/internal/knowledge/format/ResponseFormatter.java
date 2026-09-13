package com.internal.knowledge.format;

import com.internal.knowledge.model.LLMResponse;
import com.internal.knowledge.model.Source;

public class ResponseFormatter {
    public String formatForUI(LLMResponse response) {
        if (response == null) return "Unable to generate a response. Please try again.";
        StringBuilder formatted = new StringBuilder();
        formatted.append(response.getFormattedResponse());
        if (response.getConfidenceScore() < 0.6 && !response.isFallback()) {
            formatted.append("\n\n> **Confidence**: Medium - Please verify against source documents.");
        }
        if (response.isFallback()) {
            formatted.append("\n\n> **Note**: This response is based on partial matches.");
            formatted.append(" Please verify the information from the listed sources.");
        }
        return formatted.toString();
    }

    public String formatForEmail(LLMResponse response) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("Knowledge Assistant Response\n");
        formatted.append("==================================================\n\n");
        formatted.append(response.getRawResponse()).append("\n\n");
        if (!response.getSources().isEmpty()) {
            formatted.append("Sources:\n");
            for (Source source : response.getSources()) {
                formatted.append("- ").append(source.getTitle());
                if (source.getUrl() != null) formatted.append(" (").append(source.getUrl()).append(")");
                formatted.append("\n");
            }
        }
        return formatted.toString();
    }
}
