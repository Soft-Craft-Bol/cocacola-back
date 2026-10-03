package com.cocacola.utils;

import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.repository.CrmGateway;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** CRM por webhook: hace POST de cada contacto en JSON a crm.webhook-url (con crm.api-key como Bearer opcional). */
@Component
public class WebhookCrmGateway implements CrmGateway {

    private final String url;
    private final String apiKey;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile HttpClient http;

    public WebhookCrmGateway(@Value("${crm.webhook-url:}") String url, @Value("${crm.api-key:}") String apiKey) {
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public boolean isConfigured() {
        return !url.isBlank();
    }

    @Override
    public void push(Map<String, Object> contact) {
        if (!isConfigured()) throw new StorageException("El CRM no está configurado: define crm.webhook-url");
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(contact)));
            if (!apiKey.isBlank()) request.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> response = client().send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new StorageException("El CRM respondió " + response.statusCode());
        } catch (IOException ex) {
            throw new StorageException("No se pudo conectar con el CRM", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StorageException("Envío al CRM interrumpido", ex);
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
