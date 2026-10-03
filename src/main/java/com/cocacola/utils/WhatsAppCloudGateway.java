package com.cocacola.utils;

import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.repository.WhatsAppGateway;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * WhatsApp Business Cloud API (Meta). Requiere whatsapp.token y whatsapp.phone-number-id.
 * Nota: los mensajes de texto libre solo se entregan dentro de la ventana de 24 h de una conversación abierta
 * por el usuario; fuera de ella Meta exige plantillas aprobadas.
 */
@Component
public class WhatsAppCloudGateway implements WhatsAppGateway {

    private final String token;
    private final String phoneNumberId;
    private final String countryCode;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile HttpClient http;

    public WhatsAppCloudGateway(@Value("${whatsapp.token:}") String token,
                                @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
                                @Value("${whatsapp.default-country-code:57}") String countryCode) {
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.countryCode = countryCode;
    }

    @Override
    public boolean isConfigured() {
        return !token.isBlank() && !phoneNumberId.isBlank();
    }

    @Override
    public void sendText(String phone, String text) {
        if (!isConfigured()) throw new StorageException("WhatsApp no está configurado: define whatsapp.token y whatsapp.phone-number-id");
        try {
            String body = json.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp",
                    "to", normalize(phone),
                    "type", "text",
                    "text", Map.of("body", text)));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/v21.0/" + phoneNumberId + "/messages"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                JsonNode node = json.readTree(response.body());
                throw new StorageException("WhatsApp rechazó el mensaje: " + node.path("error").path("message").asString("error " + response.statusCode()));
            }
        } catch (IOException ex) {
            throw new StorageException("No se pudo conectar con WhatsApp", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StorageException("Envío a WhatsApp interrumpido", ex);
        }
    }

    /** Solo dígitos; si el número tiene 10 dígitos se antepone el código de país por defecto. */
    String normalize(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() == 10 ? countryCode + digits : digits;
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
