package com.cocacola.utils;

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

/**
 * Cualquier API compatible con OpenAI (POST {base-url}/chat/completions): OpenCode Zen (modelos gratuitos),
 * Groq, OpenRouter, Ollama local, etc. Se activa con ai.openai.api-key (Ollama no la necesita: usa ai.openai.base-url).
 */
@Slf4j
@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile HttpClient http;

    public OpenAiCompatibleProvider(@Value("${ai.openai.base-url:}") String baseUrl,
                                    @Value("${ai.openai.api-key:}") String apiKey,
                                    @Value("${ai.openai.model:}") String model) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null ? "" : model;
    }

    @Override
    public boolean isConfigured() {
        // Se necesita URL y modelo; la clave es obligatoria salvo en servidores locales (Ollama)
        boolean local = baseUrl.startsWith("http://localhost") || baseUrl.startsWith("http://127.0.0.1");
        return !baseUrl.isBlank() && !model.isBlank() && (!apiKey.isBlank() || local);
    }

    @Override
    public Optional<String> generate(String system, String prompt) {
        if (!isConfigured()) return Optional.empty();
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 500,
                    "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", prompt))));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = client().send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (response.statusCode() >= 400) {
                log.warn("La IA ({}) respondió {}: {}", model, response.statusCode(), node.path("error").path("message").asString(response.body()));
                return Optional.empty();
            }
            String text = node.path("choices").path(0).path("message").path("content").asString("");
            return text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (IOException ex) {
            log.warn("No se pudo consultar a la IA ({}): {}", baseUrl, ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException ex) {
            // Cualquier otro fallo de red o de formato: la IA es opcional, la app sigue con el resumen automático
            log.warn("Fallo inesperado al consultar a la IA ({}): {}", baseUrl, ex.getMessage());
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
