package com.suppprt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal LLM client using Java 21 HTTP Client
 */
public class LlmClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    public final String model;
    private final String apiUrl;
    public LlmClient(String apiKey) {
        this(apiKey, "gpt-4o-mini","https://api.openai.com/v1/chat/completions");
    }

    public LlmClient(String apiKey, String model,String url) {
        this.apiKey = apiKey;
        this.model = model;
        this.apiUrl = url;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    public String call(String systemPrompt, String userPrompt) throws Exception {
        var requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.3,
                "max_tokens", 1000,
                "response_format", Map.of("type", "json_object")
        );

        var request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode() + " - " + response.body());
        }

        var json = mapper.readValue(response.body(), Map.class);
        var choices = (List<Map<String, Object>>) json.get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");
        var content = (String) message.get("content");

        var usage = (Map<String, Integer>) json.get("usage");
        var tokens = usage != null ? usage.get("total_tokens") : 0;

        return content;
    }
}