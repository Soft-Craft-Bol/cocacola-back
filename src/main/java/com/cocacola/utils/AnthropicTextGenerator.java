package com.cocacola.utils;

import com.cocacola.domain.repository.TextGenerator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador de TextGenerator sobre la API de Claude (Messages). Se activa con ai.anthropic.api-key. */
@Slf4j
@Component
public class AnthropicTextGenerator implements TextGenerator {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final String model;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile HttpClient http;

    public AnthropicTextGenerator(@Value("${ai.anthropic.api-key:}") String apiKey,
                                  @Value("${ai.model:claude-sonnet-5-5}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Optional<String> generate(String system, String prompt) {
        if (!isConfigured()) return Optional.empty();
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 800,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (response.statusCode() >= 400) {
                log.warn("Claude respondió {}: {}", response.statusCode(), node.path("error").path("message").asString(""));
                return Optional.empty();
            }
            String text = node.path("content").path(0).path("text").asString("");
            return text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (IOException ex) {
            log.warn("No se pudo consultar a Claude: {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private HttpClient client() {
        HttpClient local = http;
        if (local == null) {
            synchronized (this) {
                if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                local = http;
            }
        }
        return local;
    }
}
