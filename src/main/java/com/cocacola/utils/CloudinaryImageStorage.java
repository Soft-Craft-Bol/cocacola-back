package com.cocacola.utils;

import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.model.ImageUpload;
import com.cocacola.domain.model.StoredImage;
import com.cocacola.domain.repository.ImageStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador de ImageStorage sobre la API REST de Cloudinary (subida y borrado firmados).
 * Configuracion: cloudinary.cloud-name, cloudinary.api-key y cloudinary.api-secret.
 */
@Component
public class CloudinaryImageStorage implements ImageStorage {

    private static final String API = "https://api.cloudinary.com/v1_1/";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private volatile HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public CloudinaryImageStorage(@Value("${cloudinary.cloud-name:}") String cloudName,
                                  @Value("${cloudinary.api-key:}") String apiKey,
                                  @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public StoredImage upload(ImageUpload image, String folder) {
        ensureConfigured();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Map<String, String> signed = new TreeMap<>();
        signed.put("folder", folder);
        signed.put("timestamp", timestamp);

        String boundary = "----cc" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            field(body, boundary, "api_key", apiKey);
            field(body, boundary, "timestamp", timestamp);
            field(body, boundary, "folder", folder);
            field(body, boundary, "signature", sign(signed));
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + safeName(image.filename()) + "\"\r\nContent-Type: " + image.contentType() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.write(image.content());
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new StorageException("No se pudo preparar la imagen", ex);
        }

        JsonNode result = call(HttpRequest.newBuilder(URI.create(API + cloudName + "/image/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
        return new StoredImage(result.path("secure_url").asString(), result.path("public_id").asString());
    }

    @Override
    public void delete(String publicId) {
        ensureConfigured();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        Map<String, String> signed = new TreeMap<>();
        signed.put("invalidate", "true");
        signed.put("public_id", publicId);
        signed.put("timestamp", timestamp);

        String form = "api_key=" + apiKey + "&timestamp=" + timestamp + "&public_id=" + java.net.URLEncoder.encode(publicId, StandardCharsets.UTF_8)
                + "&invalidate=true&signature=" + sign(signed);
        JsonNode result = call(HttpRequest.newBuilder(URI.create(API + cloudName + "/image/destroy"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String status = result.path("result").asString();
        if (!"ok".equals(status) && !"not found".equals(status)) {
            throw new StorageException("Cloudinary no eliminó la imagen: " + status);
        }
    }

    // ---------------------------------------------------------------- utilidades

    /** El cliente HTTP se crea al primer uso (no al arrancar la aplicacion). */
    private HttpClient client() {
        HttpClient local = http;
        if (local == null) {
            synchronized (this) {
                if (http == null) {
                    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                }
                local = http;
            }
        }
        return local;
    }

    private void ensureConfigured() {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new StorageException("Cloudinary no está configurado: define cloudinary.api-secret en application.properties");
        }
    }

    private JsonNode call(HttpRequest request) {
        try {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (response.statusCode() >= 400) {
                throw new StorageException("Cloudinary rechazó la solicitud: " + node.path("error").path("message").asString("error " + response.statusCode()));
            }
            return node;
        } catch (IOException ex) {
            throw new StorageException("No se pudo conectar con Cloudinary", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new StorageException("Operación con Cloudinary interrumpida", ex);
        }
    }

    /** Firma SHA-1: parametros ordenados "k=v&k=v" + api_secret. */
    private String sign(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : "&").append(k).append('=').append(v));
        sb.append(apiSecret);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new StorageException("SHA-1 no disponible", ex);
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "imagen" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
