package com.cocacola.utils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Caché en memoria para lecturas pesadas (métricas, analítica, IA).
 * - {@link #get}: guarda por usuario (cada rol ve datos distintos) y se vacía en cuanto algo cambia por la API
 *   (cualquier POST, PUT, PATCH o DELETE llama a {@link #invalidate()}); además vence a los 60 s por si algo cambia fuera de la API.
 * - {@link #byContent}: resultados que dependen solo del contenido consultado (texto de IA); no se invalidan al escribir.
 */
@Component
public class ReadCache {

    private static final Duration DATA_TTL = Duration.ofSeconds(60);
    private static final int MAX_ENTRIES = 500;

    private record Entry(long version, long expiresAt, Object value) {
    }

    private final AtomicLong version = new AtomicLong();
    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Map<String, Entry> content = new ConcurrentHashMap<>();

    public void invalidate() {
        version.incrementAndGet();
        data.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        long v = version.get();
        String scoped = user() + "|" + key;
        long now = System.currentTimeMillis();
        Entry hit = data.get(scoped);
        if (hit != null && hit.version() == v && hit.expiresAt() > now) return (T) hit.value();
        T value = loader.get();
        if (value != null && version.get() == v) {
            if (data.size() > MAX_ENTRIES) data.clear();
            data.put(scoped, new Entry(v, now + DATA_TTL.toMillis(), value));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T byContent(String key, Duration ttl, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Entry hit = content.get(key);
        if (hit != null && hit.expiresAt() > now) return (T) hit.value();
        T value = loader.get();
        if (value != null) {
            if (content.size() > MAX_ENTRIES) content.clear();
            content.put(key, new Entry(0, now + ttl.toMillis(), value));
        }
        return value;
    }

    private static String user() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anon" : auth.getName();
    }
}
