package com.cocacola.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte texto en audio (MP3) para narrar los resúmenes. Usa ElevenLabs si está configurado (tts.elevenlabs.*)
 * y, si no, la API de voz de OpenAI con la misma clave de ai.openai.*. Sin ninguno, el frontend narra con la voz del navegador.
 */
@Slf4j
@Component
public class SpeechSynthesizer {

    public static final int MAX_CHARS = 4000;

    private final String elevenKey;
    private final String elevenVoice;
    private final String elevenModel;
    private final String openAiBase;
    private final String openAiKey;
    private final String openAiModel;
    private final String openAiVoice;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile HttpClient httpClient;

    public SpeechSynthesizer(@Value("${tts.elevenlabs.api-key:}") String elevenKey,
                             @Value("${tts.elevenlabs.voice-id:}") String elevenVoice,
                             @Value("${tts.elevenlabs.model:eleven_multilingual_v2}") String elevenModel,
                             @Value("${ai.openai.base-url:}") String openAiBase,
                             @Value("${ai.openai.api-key:}") String openAiKey,
                             @Value("${tts.openai.model:gpt-4o-mini-tts}") String openAiModel,
                             @Value("${tts.openai.voice:alloy}") String openAiVoice) {
        this.elevenKey = elevenKey == null ? "" : elevenKey.trim();
        this.elevenVoice = elevenVoice == null ? "" : elevenVoice.trim();
        this.elevenModel = elevenModel;
        this.openAiBase = openAiBase == null ? "" : openAiBase.replaceAll("/+$", "");
        this.openAiKey = openAiKey == null ? "" : openAiKey.trim();
        this.openAiModel = openAiModel;
        this.openAiVoice = openAiVoice;
    }

    public boolean elevenLabsConfigured() {
        return !elevenKey.isBlank() && !elevenVoice.isBlank();
    }

    public boolean openAiConfigured() {
        return !openAiKey.isBlank() && openAiBase.startsWith("https://api.openai.com");
    }

    /** Proveedor activo: "elevenlabs", "openai" o "navegador" (sin voz en el servidor). */
    public String provider() {
        return elevenLabsConfigured() ? "elevenlabs" : openAiConfigured() ? "openai" : "navegador";
    }

    public Optional<byte[]> synthesize(String text) {
        String clean = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        if (elevenLabsConfigured()) {
            Optional<byte[]> audio = elevenLabs(clean);
            if (audio.isPresent()) return audio;
        }
        return openAiConfigured() ? openAi(clean) : Optional.empty();
    }

    private Optional<byte[]> elevenLabs(String text) {
        try {
            String body = json.writeValueAsString(Map.of("text", text, "model_id", elevenModel));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + elevenVoice + "?output_format=mp3_44100_128"))
                    .header("xi-api-key", elevenKey).header("Content-Type", "application/json").header("Accept", "audio/mpeg")
                    .timeout(Duration.ofSeconds(60)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            return audio(client().send(request, HttpResponse.BodyHandlers.ofByteArray()), "ElevenLabs");
        } catch (IOException ex) {
            log.warn("No se pudo consultar a ElevenLabs: {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<byte[]> openAi(String text) {
        try {
            String body = json.writeValueAsString(Map.of("model", openAiModel, "voice", openAiVoice, "input", text, "response_format", "mp3"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(openAiBase + "/audio/speech"))
                    .header("Authorization", "Bearer " + openAiKey).header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            return audio(client().send(request, HttpResponse.BodyHandlers.ofByteArray()), "OpenAI");
        } catch (IOException ex) {
            log.warn("No se pudo consultar la voz de OpenAI: {}", ex.getMessage());
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<byte[]> audio(HttpResponse<byte[]> response, String name) {
        if (response.statusCode() >= 400 || response.body().length == 0) {
            log.warn("La voz de {} respondió {}: {}", name, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            return Optional.empty();
        }
        return Optional.of(response.body());
    }

    // Se crea al primer uso (no al arrancar la aplicación)
    private HttpClient client() {
        HttpClient local = httpClient;
        if (local == null) {
            synchronized (this) {
                if (httpClient == null) httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                local = httpClient;
            }
        }
        return local;
    }
}
