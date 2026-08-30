package com.internal.knowledge.citation;

import com.internal.knowledge.model.Citation;
import com.internal.knowledge.model.ContextResult;
import com.internal.knowledge.model.LLMResponse;
import com.internal.knowledge.model.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

public class CitationEngine {
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[C(\\d+)\\]");
    private final CitationConfig config;

    public CitationEngine() {
        this.config = new CitationConfig();
    }

    public LLMResponse processCitations(String rawResponse, ContextResult contextResult) {
        System.out.println("Processing citations in response");

        Map<Integer, CitationExtract> extractedCitations = extractCitations(rawResponse);
        Map<Integer, List<Citation>> mappedCitations = mapCitationsToSources(
            extractedCitations, contextResult.getSources());
        List<Citation> structuredCitations = generateCitationData(
            mappedCitations, contextResult.getContext());
        String formattedResponse = formatCitationsInResponse(rawResponse, structuredCitations);

        return LLMResponse.builder()
            .rawResponse(rawResponse)
            .formattedResponse(formattedResponse)
            .citations(structuredCitations)
            .sources(contextResult.getSources())
            .build();
    }

    private Map<Integer, CitationExtract> extractCitations(String response) {
        Map<Integer, CitationExtract> extracts = new LinkedHashMap<>();
        Matcher matcher = CITATION_PATTERN.matcher(response);
        while (matcher.find()) {
            int position = matcher.start();
            int endPosition = matcher.end();
            int citationId = Integer.parseInt(matcher.group(1));
            if (!extracts.containsKey(position)) {
                extracts.put(position, new CitationExtract(position, endPosition));
            }
            extracts.get(position).addCitation(citationId);
        }
        return extracts;
    }

    private Map<Integer, List<Citation>> mapCitationsToSources(
            Map<Integer, CitationExtract> extracts, List<Source> sources) {
        Map<Integer, List<Citation>> mapped = new LinkedHashMap<>();
        Map<Integer, Source> sourceMap = new HashMap<>();
        for (Source source : sources) sourceMap.put(source.getId(), source);

        for (Map.Entry<Integer, CitationExtract> entry : extracts.entrySet()) {
            CitationExtract extract = entry.getValue();
            List<Citation> citations = new ArrayList<>();
            for (int citationId : extract.getCitationIds()) {
                Source source = sourceMap.get(citationId);
                if (source != null) {
                    citations.add(Citation.builder()
                        .id(citationId)
                        .sourceUrl(source.getUrl())
                        .sourceTitle(source.getTitle())
                        .sourceSection(source.getSection())
                        .build());
                }
            }
            if (!citations.isEmpty()) mapped.put(entry.getKey(), citations);
        }
        return mapped;
    }

    private List<Citation> generateCitationData(
            Map<Integer, List<Citation>> mappedCitations,
            List<ContextResult.ContextEntry> contextEntries) {
        List<Citation> allCitations = new ArrayList<>();
        Map<Integer, ContextResult.ContextEntry> contextMap = new HashMap<>();
        for (int i = 0; i < contextEntries.size(); i++) {
            contextMap.put(i + 1, contextEntries.get(i));
        }

        for (Map.Entry<Integer, List<Citation>> entry : mappedCitations.entrySet()) {
            int position = entry.getKey();
            for (Citation citation : entry.getValue()) {
                ContextResult.ContextEntry context = contextMap.get(citation.getId());
                if (context != null) {
                    String previewText = context.getText();
                    if (previewText.length() > 200) previewText = previewText.substring(0, 200) + "...";
                    citation.setPreviewText(previewText);
                    citation.setContextPositionStart(position);
                    citation.setContextPositionEnd(position + 3);
                }
                allCitations.add(citation);
            }
        }
        return allCitations;
    }

    private String formatCitationsInResponse(String response, List<Citation> citations) {
        String formattedResponse = response;

        String format = config.getCitationFormat();
        if ("superscript".equals(format)) {
            formattedResponse = formatAsSuperscript(response, citations);
        } else if ("numeric".equals(format)) {
            formattedResponse = formatAsNumeric(response, citations);
        } else {
            formattedResponse = formatAsNumeric(response, citations);
        }

        formattedResponse = addSourcesSection(formattedResponse, citations);
        return formattedResponse;
    }

    private String formatAsSuperscript(String response, List<Citation> citations) {
        String formatted = response;
        for (Citation citation : citations) {
            formatted = formatted.replaceAll("\\[C" + citation.getId() + "\\]",
                "<sup>[" + citation.getId() + "]</sup>");
        }
        return formatted;
    }

    private String formatAsNumeric(String response, List<Citation> citations) {
        String formatted = response;
        for (Citation citation : citations) {
            formatted = formatted.replaceAll("\\[C" + citation.getId() + "\\]",
                "[" + citation.getId() + "]");
        }
        return formatted;
    }

    private String addSourcesSection(String response, List<Citation> citations) {
        if (citations.isEmpty()) return response;

        StringBuilder sourcesSection = new StringBuilder();
        sourcesSection.append("\n\n---\n");
        sourcesSection.append("**Sources:**\n");

        Set<Integer> uniqueIds = new LinkedHashSet<>();
        for (Citation citation : citations) uniqueIds.add(citation.getId());

        for (int id : uniqueIds) {
            Citation citation = citations.stream()
                .filter(c -> c.getId() == id)
                .findFirst().orElse(null);
            if (citation != null) {
                sourcesSection.append("- **[").append(id).append("]** ");
                sourcesSection.append(citation.getSourceTitle());
                if (citation.getSourceSection() != null && !citation.getSourceSection().isEmpty()) {
                    sourcesSection.append(" - Section: ").append(citation.getSourceSection());
                }
                if (config.isIncludeUrls() && citation.getSourceUrl() != null) {
                    sourcesSection.append(" - [Link](").append(citation.getSourceUrl()).append(")");
                }
                sourcesSection.append("\n");
            }
        }
        return response + sourcesSection.toString();
    }

    @Data
    private static class CitationExtract {
        private final int position;
        private final int endPosition;
        private final List<Integer> citationIds = new ArrayList<>();

        public CitationExtract(int position, int endPosition) {
            this.position = position;
            this.endPosition = endPosition;
        }
        public void addCitation(int citationId) {
            if (!citationIds.contains(citationId)) citationIds.add(citationId);
        }
    }

    @Data
    public static class CitationConfig {
        private String citationFormat = "numeric";
        private boolean includeUrls = true;
        private String citationSeparator = ", ";
        private int maxSourcesPerCitation = 5;
    }
}
